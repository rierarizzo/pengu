package com.misgus.pengu.data.backend

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.data.store.AddonSnapshotStore
import com.misgus.pengu.data.store.NoopAddonSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AddonRepository] backed by a Nuvio (Supabase) deployment.
 *
 * Addons belong to a profile, and row-level security only stops at the account,
 * so reads are scoped by `profile_id` like the library.
 *
 * The list is answered local-first. Every catalog request needs it, so waiting
 * for the backend would put a round-trip in front of the first catalog: the last
 * known list is read from [snapshots] instead, and the backend answer refreshes
 * the snapshot for the next launch. Addons have no write path in this
 * repository, so a served list cannot go stale mid-load; a list that did change
 * behind the caller's back is announced through [changes].
 *
 * The in-memory copy is keyed by the profile's uuid (globally unique, unlike the
 * integer `profile_id`), so switching profile or account drops it.
 */
class NuvioAddonRepository(
    private val rest: NuvioRest,
    private val account: AccountRepository,
    private val snapshots: AddonSnapshotStore = NoopAddonSnapshotStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AddonRepository {
    private val cacheMutex = Mutex()
    private var cachedProfileKey: String? = null
    private var cachedAddons: List<Addon>? = null

    /** Profiles whose background refresh is already running, so one is enough. */
    private val refreshMutex = Mutex()
    private val refreshing = mutableSetOf<String>()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun all(): List<Addon> {
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

    private suspend fun fetch(profileId: Int): List<Addon> {
        val rows =
            rest.select(
                "addons",
                order = "sort_order.asc",
                filter = "profile_id=eq.$profileId&enabled=is.true",
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
                    remember(profileKey, fresh)
                    _changes.tryEmit(Unit)
                }
            } finally {
                refreshMutex.withLock { refreshing.remove(profileKey) }
            }
        }
    }
}
