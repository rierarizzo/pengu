package com.misgus.pengu.data

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.data.backend.NuvioAccountRepository
import com.misgus.pengu.data.backend.NuvioAddonRepository
import com.misgus.pengu.data.backend.NuvioAuth
import com.misgus.pengu.data.backend.NuvioDiscovery
import com.misgus.pengu.data.backend.NuvioLibraryRepository
import com.misgus.pengu.data.backend.NuvioProgressRepository
import com.misgus.pengu.data.backend.NuvioRest
import com.misgus.pengu.data.backend.NuvioTransport
import com.misgus.pengu.data.local.LocalAccountRepository
import com.misgus.pengu.data.local.LocalAddonRepository
import com.misgus.pengu.data.local.LocalLibraryRepository
import com.misgus.pengu.data.local.LocalProgressRepository
import com.misgus.pengu.data.platform.createHttpClient
import com.misgus.pengu.data.store.AddonSnapshotStore
import com.misgus.pengu.data.store.AppPaths
import com.misgus.pengu.data.store.FileAddonSnapshotStore
import com.misgus.pengu.data.store.JsonFileStore
import com.misgus.pengu.data.store.NoopSessionStore
import com.misgus.pengu.data.store.SessionStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Which backend provides the account, the library and the progress.
 *
 * Adding a backend means adding a value here and a branch in [createBackend].
 * Nothing in `ui/` changes, because it only knows the interfaces.
 */
enum class BackendKind {
    /** Nuvio's hosted (or self-hosted) Supabase backend. */
    NUVIO,

    /** Everything on this machine: no account, no profiles. */
    LOCAL,
    ;

    companion object {
        /**
         * Reads `PENGU_BACKEND`, defaulting to [NUVIO] for an unknown value.
         */
        fun fromEnvironment(environment: Map<String, String>): BackendKind {
            val raw = environment["PENGU_BACKEND"] ?: return NUVIO
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: NUVIO
        }
    }
}

/** The repositories a backend provides, plus a way to release its resources. */
class BackendBundle(
    val account: AccountRepository,
    val library: LibraryRepository,
    val progress: ProgressRepository,
    /** Addons installed in the account, consumed by the Stremio addon layer. */
    val addons: AddonRepository,
    /** Releases the backend resources (HTTP clients). Optional. */
    val close: () -> Unit = {},
)

/**
 * Builds the repositories for [kind].
 *
 * It is the only place that knows the concrete implementations, so swapping the
 * backend is a change here and nowhere else.
 */
fun createBackend(
    kind: BackendKind = BackendKind.NUVIO,
    httpClient: HttpClient = createHttpClient(),
    baseUrl: String? = null,
    sessionStore: SessionStore = NoopSessionStore(),
    localDir: String? = null,
    localAddons: List<Addon>? = null,
    addonSnapshots: AddonSnapshotStore = FileAddonSnapshotStore(),
): BackendBundle =
    when (kind) {
        BackendKind.NUVIO ->
            createNuvioBackend(
                baseUrl = baseUrl ?: NuvioTransport.DEFAULT_BASE_URL,
                httpClient = httpClient,
                sessionStore = sessionStore,
                addonSnapshots = addonSnapshots,
            )
        BackendKind.LOCAL -> {
            val dir = localDir ?: AppPaths.localStoreDir
            BackendBundle(
                account = LocalAccountRepository(),
                library = LocalLibraryRepository(JsonFileStore("$dir/library.json")),
                progress =
                    LocalProgressRepository(
                        store = JsonFileStore("$dir/progress.json"),
                        watched = JsonFileStore("$dir/watched.json"),
                    ),
                addons = LocalAddonRepository(localAddons ?: emptyList()),
            )
        }
    }

/** Builds the Nuvio backend, using [httpClient] for every request. */
fun createNuvioBackend(
    baseUrl: String = NuvioTransport.DEFAULT_BASE_URL,
    httpClient: HttpClient,
    sessionStore: SessionStore = NoopSessionStore(),
    addonSnapshots: AddonSnapshotStore = FileAddonSnapshotStore(),
): BackendBundle {
    // The pieces are wired here and handed out narrowly: a repository gets the
    // transport slice it needs, not a client that can do everything.
    val transport = NuvioTransport(baseUrl = baseUrl, http = httpClient)
    val discovery = NuvioDiscovery(transport)
    val auth = NuvioAuth(transport, discovery, sessionStore)
    val rest = NuvioRest(transport, discovery, auth)
    val account = NuvioAccountRepository(auth, rest)
    // The addon repository refreshes its local snapshot after answering, so it
    // owns a scope that outlives a single call; the bundle closes it.
    val addonScope = CoroutineScope(SupervisorJob())
    return BackendBundle(
        account = account,
        library = NuvioLibraryRepository(rest, auth, account),
        progress = NuvioProgressRepository(rest, account),
        addons = NuvioAddonRepository(rest, account, addonSnapshots, addonScope),
        close = {
            addonScope.cancel()
            transport.close()
        },
    )
}
