package com.misgus.pengu.ui.support

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AccountState
import com.misgus.pengu.core.repository.AddonInstallException
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.repository.SignInMethod
import com.misgus.pengu.core.repository.SignInRequest
import com.misgus.pengu.core.repository.StreamGroup
import com.misgus.pengu.core.repository.StreamRepository
import com.misgus.pengu.core.repository.TmdbLookup
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.repository.TmdbVerifyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [AccountRepository] for tests. */
class FakeAccountRepository(
    initial: AccountState = AccountState(isSignedIn = true, requiresProfile = false),
) : AccountRepository {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<AccountState> = _state

    override val signInMethods: List<SignInMethod> = listOf(SignInMethod.EmailPassword)

    override suspend fun signIn(request: SignInRequest) {
        val email =
            when (request) {
                is SignInRequest.EmailPassword -> request.email
            }
        _state.value = AccountState(isSignedIn = true, email = email)
    }

    override suspend fun restoreSession(): Boolean = _state.value.isSignedIn

    override suspend fun signOut() {
        _state.value = AccountState(isSignedIn = false)
    }

    override suspend fun loadProfiles() = Unit

    override suspend fun selectProfile(profile: BackendProfile) {
        _state.value = _state.value.copy(activeProfile = profile)
    }
}

/** In-memory [LibraryRepository] for tests. */
class FakeLibraryRepository(
    initial: List<LibraryItem> = emptyList(),
    /** Optional gate a test completes to control when the first read answers. */
    private val gate: CompletableDeferred<Unit>? = null,
) : LibraryRepository {
    private val items = initial.toMutableList()
    private val _changes = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = _changes

    override suspend fun all(): List<LibraryItem> {
        gate?.await()
        return items.toList()
    }

    override suspend fun contains(contentId: String): Boolean = items.any { it.contentId == contentId }

    override suspend fun add(item: LibraryItem) {
        if (contains(item.contentId)) return
        items.add(item)
        _changes.value += 1
    }

    override suspend fun remove(contentId: String) {
        items.removeAll { it.contentId == contentId }
        _changes.value += 1
    }
}

/** In-memory [ProgressRepository] for tests. */
class FakeProgressRepository(
    private val entries: List<WatchedEntry> = emptyList(),
    progress: List<WatchProgress> = emptyList(),
) : ProgressRepository {
    private val _changes = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = _changes

    private val saved = progress.toMutableList()

    /** Most recently written first, like the real repository. */
    override suspend fun all(): List<WatchProgress> = saved.toList()

    override suspend fun save(progress: WatchProgress) {
        saved.removeAll { it.progressKey == progress.progressKey }
        saved.add(0, progress)
        _changes.value += 1
    }

    override suspend fun watchedEntries(candidateIds: Collection<String>): List<WatchedEntry> =
        entries.filter { it.contentId in candidateIds }

    override suspend fun markWatched(
        contentId: String,
        contentType: ContentType,
        season: Season?,
        episode: Episode?,
    ) = Unit

    override suspend fun unmarkWatched(
        contentId: String,
        season: Season?,
        episode: Episode?,
    ) = Unit
}

/** In-memory [MetadataRepository] for tests. */
class FakeMetadataRepository(
    private val detail: MetaDetail? = null,
    private val details: Map<String, MetaDetail> = emptyMap(),
    private val similar: List<MetaPreview> = emptyList(),
) : MetadataRepository {
    override suspend fun detail(
        type: ContentType,
        id: String,
    ): MetaDetail? = details[id] ?: detail

    override suspend fun similar(
        type: ContentType,
        id: String,
        genre: String?,
    ): List<MetaPreview> = similar
}

/** In-memory [CatalogRepository] for tests. */
class FakeCatalogRepository(
    private val refs: List<CatalogRef> = emptyList(),
    private val items: Map<String, List<MetaPreview>> = emptyMap(),
    private val detailed: Map<String, List<MetaDetail>> = emptyMap(),
    /** Optional per-catalog gate a test completes to control when a row resolves. */
    private val gates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
) : CatalogRepository {
    override suspend fun catalogs(): List<CatalogRef> = refs

    override suspend fun items(
        ref: CatalogRef,
        extra: Map<String, String>?,
    ): List<MetaPreview> {
        gates[ref.key]?.await()
        return items[ref.key].orEmpty()
    }

    override suspend fun detailedItems(
        ref: CatalogRef,
        extra: Map<String, String>?,
    ): List<MetaDetail> = detailed[ref.key].orEmpty()
}

/** In-memory [StreamRepository] for tests. */
class FakeStreamRepository(
    private val groups: List<StreamGroup> = emptyList(),
) : StreamRepository {
    override suspend fun all(
        type: ContentType,
        id: String,
    ): List<StreamGroup> = groups
}

/** In-memory [AddonRepository] for tests. */
class FakeAddonRepository(
    initial: List<Addon> = emptyList(),
    override val canManage: Boolean = true,
    /** When set, [install] fails with it instead of appending the addon. */
    private val installError: AddonInstallException? = null,
    /** Manifest answers keyed by addon URL. */
    private val manifests: Map<String, AddonManifest> = emptyMap(),
) : AddonRepository {
    private val installed = initial.toMutableList()

    override suspend fun all(): List<Addon> = installed.filter { it.enabled }

    override suspend fun installed(): List<Addon> = installed.toList()

    override suspend fun install(url: String): Addon {
        installError?.let { throw it }
        val addon =
            Addon(
                id = "addon-${installed.size + 1}",
                url = url,
                sortOrder = installed.size,
            )
        installed += addon
        return addon
    }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) {
        installed.replaceAll { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun remove(id: String) {
        installed.removeAll { it.id == id }
    }

    override suspend fun reorder(ids: List<String>) {
        val byId = installed.associateBy(Addon::id)
        val reordered = ids.mapNotNull { byId[it] }.mapIndexed { index, addon -> addon.copy(sortOrder = index) }
        installed.clear()
        installed += reordered
    }

    override suspend fun manifest(url: String): AddonManifest? = manifests[url]
}

/** In-memory [TmdbRepository] for tests. */
class FakeTmdbRepository(
    private val titles: Map<String, TmdbTitle> = emptyMap(),
    private val recommended: List<MetaPreview> = emptyList(),
    private val verified: TmdbVerifyResult = TmdbVerifyResult.VALID,
    override val isConfigured: Boolean = true,
    /** Optional gate a test completes to hold a lookup in flight. */
    private val gate: CompletableDeferred<Unit>? = null,
) : TmdbRepository {
    /** Number of lookups, so a test can assert the disabled path makes none. */
    var lookups: Int = 0
        private set

    override suspend fun lookup(request: TmdbLookup): TmdbTitle? {
        lookups += 1
        gate?.await()
        return titles[request.contentId]
    }

    override suspend fun recommendations(request: TmdbLookup): List<MetaPreview> = recommended

    override suspend fun verify(apiKey: String): TmdbVerifyResult = verified
}
