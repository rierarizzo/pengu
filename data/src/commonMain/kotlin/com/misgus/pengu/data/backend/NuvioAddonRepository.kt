package com.misgus.pengu.data.backend

import com.misgus.pengu.core.BackendException
import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonInstallException
import com.misgus.pengu.core.repository.AddonInstallFailure
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.data.addons.AddonClientPool
import com.misgus.pengu.data.addons.normalizeAddonBaseUrl
import com.misgus.pengu.data.store.AddonSnapshotStore
import com.misgus.pengu.data.store.NoopAddonSnapshotStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [AddonRepository] backed by a Nuvio (Supabase) deployment.
 *
 * Addons belong to a profile, and row-level security only stops at the account,
 * so reads and writes are scoped by `profile_id` like the library.
 *
 * The list is answered local-first. Every catalog request needs it, so waiting
 * for the backend would put a round-trip in front of the first catalog: the last
 * known list is read from [snapshots] instead, and the backend answer refreshes
 * the snapshot for the next launch. The snapshot holds enabled *and* disabled
 * addons, so the settings page can manage them while the content path filters
 * the disabled ones out.
 *
 * Management writes (install, enable, remove, reorder) go through the backend
 * and then re-read the list, so the local copy and the snapshot always mirror
 * what the server stored. A write announces the change through [changes] so
 * Home and the other content sections reload their catalogs.
 *
 * The in-memory copy is keyed by the profile's uuid (globally unique, unlike the
 * integer `profile_id`), so switching profile or account drops it.
 */
class NuvioAddonRepository(
    private val rest: NuvioRest,
    private val account: AccountRepository,
    private val auth: NuvioAuth,
    private val httpClient: HttpClient,
    private val snapshots: AddonSnapshotStore = NoopAddonSnapshotStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AddonRepository {
    private val cacheMutex = Mutex()
    private var cachedProfileKey: String? = null
    private var cachedAddons: List<Addon>? = null

    /** Manifests fetched from the settings page, pooled so they are cached. */
    private val manifestClients = AddonClientPool(httpClient)

    /** Profiles whose background refresh is already running, so one is enough. */
    private val refreshMutex = Mutex()
    private val refreshing = mutableSetOf<String>()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override val canManage: Boolean = true

    override suspend fun all(): List<Addon> = installed().filter { it.enabled }

    override suspend fun installed(): List<Addon> {
        val profile = account.requireActiveProfile()
        val profileId = account.requireProfileId()
        val profileKey = profile.id
        cachedAddons?.takeIf { cachedProfileKey == profileKey }?.let { return it }
        // Serialize the fetch so concurrent callers share one request (Home,
        // Discover and Search all ask at once on a cold start).
        return cacheMutex.withLock {
            cachedAddons?.takeIf { cachedProfileKey == profileKey }?.let { return@withLock it }
            val snapshot = runCatchingCancellable { snapshots.read(profileKey) }.getOrNull()
            if (snapshot != null) {
                // Served as-is: the snapshot is already what the disk holds, so
                // there is nothing to write back.
                cachedProfileKey = profileKey
                cachedAddons = snapshot
                refreshBehind(profileKey, profileId, snapshot)
                snapshot
            } else {
                fetch(profileId).also { remember(profileKey, it) }
            }
        }
    }

    override suspend fun install(url: String): Addon {
        val baseUrl = addonBaseUrl(url) ?: throw AddonInstallException(AddonInstallFailure.INVALID_URL)
        val manifest = fetchManifest(baseUrl)
        val profile = account.requireActiveProfile()
        val profileId = account.requireProfileId()
        return cacheMutex.withLock {
            val installed = fetch(profileId)
            if (installed.any { addonBaseUrl(it.url) == baseUrl }) {
                throw AddonInstallException(AddonInstallFailure.ALREADY_INSTALLED)
            }
            val manifestUrl = "$baseUrl/manifest.json"
            rest.insert(
                "addons",
                buildJsonObject {
                    put("user_id", requireUserId())
                    put("profile_id", profileId)
                    put("url", manifestUrl)
                    put("name", manifest.name)
                    put("enabled", true)
                    put("sort_order", installed.size)
                },
            )
            val fresh = fetch(profileId)
            remember(profile.id, fresh)
            _changes.tryEmit(Unit)
            // The backend assigns the id; a failed re-read keeps the install
            // successful from the caller's point of view.
            fresh.firstOrNull { addonBaseUrl(it.url) == baseUrl }
                ?: Addon(
                    id = "",
                    url = manifestUrl,
                    name = manifest.name,
                    enabled = true,
                    sortOrder = installed.size,
                    profileId = profileId,
                )
        }
    }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) {
        mutate { profileId ->
            rest.update(
                "addons",
                filterFor(id, profileId),
                buildJsonObject { put("enabled", enabled) },
            )
        }
    }

    override suspend fun remove(id: String) {
        mutate { profileId ->
            rest.delete("addons", filterFor(id, profileId))
        }
    }

    override suspend fun reorder(ids: List<String>) {
        mutate { profileId ->
            ids.forEachIndexed { index, id ->
                rest.update(
                    "addons",
                    filterFor(id, profileId),
                    buildJsonObject { put("sort_order", index) },
                )
            }
        }
    }

    override suspend fun manifest(url: String): AddonManifest? {
        val baseUrl = addonBaseUrl(url) ?: return null
        return runCatchingCancellable { manifestClients.clientFor(baseUrl).fetchManifest() }.getOrNull()
    }

    /**
     * Runs one management write, then re-reads the list so the local copy and
     * the snapshot match the server's ids, order and enabled flags.
     */
    private suspend fun mutate(change: suspend (profileId: Int) -> Unit) {
        val profile = account.requireActiveProfile()
        val profileId = account.requireProfileId()
        cacheMutex.withLock {
            change(profileId)
            val fresh = fetch(profileId)
            remember(profile.id, fresh)
            _changes.tryEmit(Unit)
        }
    }

    private suspend fun fetch(profileId: Int): List<Addon> {
        val rows =
            rest.select(
                "addons",
                order = "sort_order.asc",
                filter = "profile_id=eq.$profileId",
            )
        return rows.map(::addonFromRow)
    }

    private suspend fun remember(
        profileKey: String,
        addons: List<Addon>,
    ) {
        cachedProfileKey = profileKey
        cachedAddons = addons
        runCatchingCancellable { snapshots.write(profileKey, addons) }
    }

    /**
     * Re-reads the addons behind a caller that was already served a snapshot.
     *
     * Failure is silent on purpose: the snapshot is still a usable list, and a
     * cold device without a network should keep working. When the addon set did
     * change, the fresh list is stored for the next launch and [changes] tells
     * the screens already on screen to reload.
     */
    private suspend fun refreshBehind(
        profileKey: String,
        profileId: Int,
        served: List<Addon>,
    ) {
        if (!refreshMutex.withLock { refreshing.add(profileKey) }) return
        scope.launch {
            try {
                val fresh = runCatchingCancellable { fetch(profileId) }.getOrNull()
                if (fresh != null && fresh != served) {
                    // A management write may have replaced the list while this
                    // refresh was in flight; only apply the answer to the list it
                    // actually read, so a stale response cannot undo a toggle.
                    cacheMutex.withLock {
                        if (cachedProfileKey == profileKey && cachedAddons == served) {
                            remember(profileKey, fresh)
                            _changes.tryEmit(Unit)
                        }
                    }
                }
            } finally {
                refreshMutex.withLock { refreshing.remove(profileKey) }
            }
        }
    }

    private suspend fun fetchManifest(baseUrl: String): AddonManifest =
        try {
            manifestClients.clientFor(baseUrl).fetchManifest()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            throw AddonInstallException(AddonInstallFailure.MANIFEST_UNREACHABLE, error)
        }

    private fun requireUserId(): String {
        val userId = auth.session?.userId
        if (userId.isNullOrEmpty()) throw BackendException("Not signed in")
        return userId
    }

    private fun filterFor(
        id: String,
        profileId: Int,
    ): String = "id=eq.${postgrestLiteral(id)}&profile_id=eq.$profileId"
}

/**
 * Normalizes an addon URL to its base, or `null` when it is not an HTTP(S) URL.
 *
 * The user pastes either a manifest URL or a base URL; both normalize to the
 * base the addon protocol expects.
 */
private fun addonBaseUrl(input: String): String? {
    val base = normalizeAddonBaseUrl(input.trim())
    if (!base.startsWith("http://", ignoreCase = true) && !base.startsWith("https://", ignoreCase = true)) return null
    if (base.substringAfter("://").isBlank()) return null
    return base
}
