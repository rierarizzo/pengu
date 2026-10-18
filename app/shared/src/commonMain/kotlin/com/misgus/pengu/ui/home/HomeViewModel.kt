package com.misgus.pengu.ui.home

import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.ProgressThresholds
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.enrichedWith
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.NoTmdbRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.repository.TmdbLookup
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.core.watched.WatchedCandidate
import com.misgus.pengu.ui.common.AccountScopedViewModel
import com.misgus.pengu.ui.common.WatchedBadgeSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import kotlin.time.Instant

/** A library title with progress, shown in "Continue watching". */
data class ContinueEntry(
    val item: LibraryItem,
    val progress: WatchProgress,
)

/** One catalog row on Home. */
data class HomeRow(
    val ref: CatalogRef,
    val title: String,
    val items: List<MetaPreview>,
)

/** Immutable state of the Home section. */
data class HomeUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
    val featured: List<MetaPreview> = emptyList(),
    val continueWatching: List<ContinueEntry> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val watchedIds: Set<String> = emptySet(),
    val isSyncingWatched: Boolean = false,
    val savedIds: Set<String> = emptySet(),
    val savingIds: Set<String> = emptySet(),
    val savedError: Throwable? = null,
)

/**
 * Loads Home: a featured set, "Continue watching" and catalog rows.
 *
 * Catalogs come from the addons; continue watching comes from the backend
 * (watch progress joined with the library). Continue watching and the rows are
 * published as they arrive, so Home paints without waiting for the slowest
 * catalog, and the watched badges and the TMDB enrichment are resolved after
 * the paint. Every browsable catalog gets a row; only the items per row are
 * capped.
 */
class HomeViewModel(
    account: AccountRepository,
    private val catalogs: CatalogRepository,
    private val library: LibraryRepository,
    private val progress: ProgressRepository,
    badges: WatchedBadgeResolver,
    private val tmdb: TmdbRepository = NoTmdbRepository(),
    private val random: Random = Random.Default,
) : AccountScopedViewModel(account) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val watchedSync = WatchedBadgeSync(badges, account)

    /** In-flight load, so the screen effect and the account observer cannot double-load. */
    private var loadJob: Job? = null

    init {
        // A save or a remove made elsewhere (the detail screen, the library)
        // bumps the repository; keep the billboard's save state in sync without
        // reloading the catalogs. A refresh is skipped while a write of our own
        // is in flight, so the optimistic flip is not clobbered by stale reads.
        viewModelScope.launch {
            library.changes.drop(1).collect { if (_state.value.savingIds.isEmpty()) refreshSaved() }
        }
    }

    override fun onAccountReady() = load(force = true)

    override fun onAccountCleared() = clear()

    /** Loads everything. Does nothing when already loaded, unless [force]. */
    fun load(force: Boolean = false) {
        if (!account.state.value.isSignedIn) {
            clear()
            return
        }
        // The job is tracked, not just the `isLoading` flag: the flag is set
        // inside the coroutine, so a second call arriving before it starts (the
        // screen's effect and the account observer both fire on entry) would slip
        // past a state check and load twice.
        if (loadJob?.isActive == true || (_state.value.hasLoaded && !force)) return
        loadJob = viewModelScope.launch { doLoad() }
    }

    private suspend fun doLoad() {
        _state.update { it.copy(isLoading = true, error = null) }
        var progressEntries: List<WatchProgress> = emptyList()
        var loaded = false
        try {
            // Watch progress, library and the catalog list are independent, so
            // they all start at once instead of paying three round-trips in a row.
            val (entries, libraryItems, refs) =
                coroutineScope {
                    val progressDeferred = async { progress.all() }
                    val libraryDeferred = async { library.all() }
                    val catalogsDeferred = async { catalogs.catalogs().filter { it.browsable } }
                    Triple(progressDeferred.await(), libraryDeferred.await(), catalogsDeferred.await())
                }
            progressEntries = entries
            val byContent = libraryItems.associateBy { it.contentId }
            val continueWatching =
                progressEntries
                    .mapNotNull { entry ->
                        val item = byContent[entry.contentId] ?: return@mapNotNull null
                        val fraction = entry.fraction ?: 0.0
                        if (fraction <= ProgressThresholds.IN_PROGRESS_MIN ||
                            fraction >= ProgressThresholds.IN_PROGRESS_MAX
                        ) {
                            return@mapNotNull null
                        }
                        ContinueEntry(item, entry)
                    }.sortedByDescending { it.progress.lastWatched ?: Instant.fromEpochMilliseconds(0) }
                    .take(MAX_CONTINUE)

            // Continue watching and the saved set do not depend on the catalogs,
            // so they are published before the rows: Home paints while the
            // catalogs stream in instead of waiting for the slowest one.
            _state.update {
                it.copy(
                    hasLoaded = true,
                    continueWatching = continueWatching,
                    savedIds = libraryItems.map { item -> item.contentId }.toSet(),
                )
            }
            loaded = true

            // Each catalog is fetched behind a small semaphore, so a phone with
            // many addons does not open one socket per catalog at once. Each row
            // is published as it arrives and the featured set grows from the rows
            // already on screen, so the billboard never waits for the last one.
            val order = refs.mapIndexed { index, ref -> ref.key to index }.toMap()
            val permits = Semaphore(MAX_ROW_CONCURRENCY)
            coroutineScope {
                refs
                    .map { ref ->
                        async {
                            val row = permits.withPermit { loadRow(ref) }
                            if (row != null) publishRow(row, order)
                        }
                    }.awaitAll()
            }

            // Enrichment runs after the paint, like the detail screen: the
            // billboard shows the catalog artwork first and swaps to TMDB's when
            // the lookup lands, instead of holding the whole screen back for it.
            // A title TMDB has no match for keeps its catalog artwork, and with no
            // key the set is unchanged.
            val featured = _state.value.featured
            if (featured.isNotEmpty()) {
                _state.update { it.copy(featured = enrichFeatured(featured)) }
            }
            _state.update { it.copy(isLoading = false) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, error = error) }
            loaded = false
        }
        // Home paints without waiting for the watch history; the checks fill in
        // when the lookup finishes.
        if (loaded) syncWatched(progressEntries)
    }

    /** Loads one catalog row, or `null` when it is empty or fails. */
    private suspend fun loadRow(ref: CatalogRef): HomeRow? =
        runCatchingCancellable {
            val items = catalogs.items(ref)
            if (items.isEmpty()) null else HomeRow(ref, ref.name, items.take(MAX_ITEMS_PER_ROW))
        }.getOrNull()

    /**
     * Adds [row] to the state and grows the featured set from the rows known so
     * far.
     *
     * Rows keep the addon's order even though they arrive in completion order:
     * the list is re-sorted against [order] on every insert. The featured set is
     * only appended to, never re-picked, so a row that arrives late does not
     * reshuffle the billboard that is already on screen.
     */
    private fun publishRow(
        row: HomeRow,
        order: Map<String, Int>,
    ) {
        _state.update { state ->
            val rows = (state.rows + row).sortedBy { order[it.ref.key] ?: Int.MAX_VALUE }
            state.copy(rows = rows, featured = growFeatured(state.featured, rows))
        }
    }

    /** Appends up to [MAX_FEATURED] items picked from [rows], skipping [current]. */
    private fun growFeatured(
        current: List<MetaPreview>,
        rows: List<HomeRow>,
    ): List<MetaPreview> {
        if (current.size >= MAX_FEATURED) return current
        val exclude = current.mapTo(mutableSetOf()) { it.type to it.id }
        return current + pickFeatured(rows, MAX_FEATURED - current.size, random, exclude)
    }

    private suspend fun syncWatched(progressEntries: List<WatchProgress>) {
        val candidates =
            buildSet {
                _state.value.rows.forEach { row -> row.items.forEach { add(WatchedCandidate(it.id, it.type)) } }
                _state.value.continueWatching.forEach {
                    add(WatchedCandidate(it.item.contentId, it.item.contentType))
                }
            }
        watchedSync.resolve(
            candidates = candidates,
            progressEntries = progressEntries,
            onStart = { _state.update { it.copy(isSyncingWatched = true) } },
            onResult = { watched ->
                _state.update { it.copy(watchedIds = watched, isSyncingWatched = false) }
            },
        )
    }

    /** Re-reads the saved set, keeping the billboard in sync with other screens. */
    private suspend fun refreshSaved() {
        val saved =
            runCatchingCancellable { library.all() }
                .getOrNull()
                ?.map { item -> item.contentId }
                ?.toSet()
                ?: return
        _state.update { it.copy(savedIds = saved) }
    }

    /**
     * Adds or removes [item] from the library.
     *
     * Optimistic: the flag flips immediately and reverts if the write fails, so
     * the billboard reacts without waiting for the round-trip. The write is
     * marked busy meanwhile, so a second tap cannot race it; a failure is
     * published through [HomeUiState.savedError] for the screen's snackbar.
     */
    fun toggleSaved(item: MetaPreview) {
        val id = item.id
        if (id in _state.value.savingIds) return
        val wasSaved = id in _state.value.savedIds
        _state.update {
            it.copy(
                savedIds = if (wasSaved) it.savedIds - id else it.savedIds + id,
                savingIds = it.savingIds + id,
                savedError = null,
            )
        }
        viewModelScope.launch {
            runCatchingCancellable {
                if (wasSaved) {
                    library.remove(id)
                } else {
                    library.add(LibraryItem.fromPreview(item))
                }
            }.onSuccess {
                _state.update { it.copy(savingIds = it.savingIds - id) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        savedIds = if (wasSaved) it.savedIds + id else it.savedIds - id,
                        savingIds = it.savingIds - id,
                        savedError = error,
                    )
                }
            }
        }
    }

    /** Clears [HomeUiState.savedError] once the screen has shown it. */
    fun consumeSavedError() {
        _state.update { it.copy(savedError = null) }
    }

    /**
     * Replaces the featured set with a new random mix of the loaded rows.
     *
     * The screen calls it on a timer, so the billboard changes over time without
     * re-picking (or reshuffling) on every recomposition. Items already on screen
     * are dropped when the pool is large enough, so a rotation shows new ones.
     */
    fun rotateFeatured() {
        val rows = _state.value.rows
        if (rows.isEmpty()) return
        val current =
            _state.value.featured
                .map { it.type to it.id }
                .toSet()
        viewModelScope.launch {
            val featured = enrichFeatured(pickFeatured(rows, MAX_FEATURED, random, exclude = current))
            _state.update { it.copy(featured = featured) }
        }
    }

    /**
     * Fills each featured billboard item's missing artwork from TMDB.
     *
     * Only the handful of slides on screen are looked up, never the whole
     * catalog, and the requests run in parallel behind a small semaphore so a
     * wide window's six slides do not open six sockets at once. Without a key,
     * or when a lookup fails, the catalog item is returned untouched.
     */
    private suspend fun enrichFeatured(items: List<MetaPreview>): List<MetaPreview> {
        if (!tmdb.isConfigured || items.isEmpty()) return items
        return coroutineScope {
            val permits = Semaphore(MAX_ENRICH_CONCURRENCY)
            items
                .map { item ->
                    async {
                        permits.withPermit {
                            val lookup =
                                TmdbLookup(
                                    type = item.type,
                                    contentId = item.id,
                                    name = item.name,
                                    year = releaseYear(item.releaseInfo),
                                )
                            val title = runCatchingCancellable { tmdb.lookup(lookup) }.getOrNull()
                            item.enrichedWith(title)
                        }
                    }
                }.awaitAll()
        }
    }

    private fun clear() {
        loadJob?.cancel()
        loadJob = null
        watchedSync.invalidate()
        _state.value = HomeUiState()
    }

    private companion object {
        const val MAX_ITEMS_PER_ROW = 20
        const val MAX_CONTINUE = 12
        const val MAX_FEATURED = 6
        const val MAX_ENRICH_CONCURRENCY = 4

        /**
         * Catalogs fetched at once. Small on purpose: a phone's HTTP engine
         * opens a bounded number of connections per host, so a wider fan-out
         * would only build a queue. The semaphore keeps the concurrency
         * explicit and portable across both engines.
         */
        const val MAX_ROW_CONCURRENCY = 6
    }
}

/** Lowest IMDb rating a title needs to lead the billboard. `null` passes. */
private const val FEATURED_MIN_RATING = 6.0

/**
 * Picks the featured set from every catalog row, mixing movies and series.
 *
 * The pool is every item across all rows, deduped by `type:id`, so the billboard
 * is not tied to the first catalog. Selection follows a preference order — well
 * rated and off screen first, then well rated, then the rest — so a rotation
 * shows something new and low-rated titles are only used when there is not
 * enough good content. [exclude] is the set currently on screen; the result
 * keeps a movie / series balance and is shuffled, so the order is random instead
 * of always "movie, series, movie, ...".
 */
internal fun pickFeatured(
    rows: List<HomeRow>,
    count: Int,
    random: Random,
    exclude: Set<Pair<ContentType, String>> = emptySet(),
): List<MetaPreview> {
    val all = rows.flatMap { it.items }.distinctBy { it.type to it.id }
    if (count <= 0 || all.isEmpty()) return emptyList()
    val good = all.filter { it.isWellRated() }
    val rest = all.filterNot { it.isWellRated() }
    val tiers =
        listOf(
            good.filterNot { (it.type to it.id) in exclude },
            good.filter { (it.type to it.id) in exclude },
            rest.filterNot { (it.type to it.id) in exclude },
            rest.filter { (it.type to it.id) in exclude },
        )
    val picked = mutableListOf<MetaPreview>()
    for (tier in tiers) {
        if (picked.size >= count) break
        picked += takeBalanced(tier, count - picked.size, random)
    }
    return picked.shuffled(random)
}

/**
 * Whether a title is rated well enough to lead the billboard. An unknown rating
 * passes, since the catalog simply does not know; only a known low score fails.
 */
private fun MetaPreview.isWellRated(): Boolean {
    val rating = imdbRating
    return rating == null || rating >= FEATURED_MIN_RATING
}

/** Takes up to [count] items from [pool], alternating movies and series. */
private fun takeBalanced(
    pool: List<MetaPreview>,
    count: Int,
    random: Random,
): List<MetaPreview> {
    if (count <= 0 || pool.isEmpty()) return emptyList()
    val movies = pool.filter { it.type == ContentTypes.MOVIE }.shuffled(random)
    val series = pool.filter { ContentTypes.isSeries(it.type) }.shuffled(random)
    val other = pool.filterNot { it.type == ContentTypes.MOVIE || ContentTypes.isSeries(it.type) }.shuffled(random)
    val balanced =
        buildList {
            repeat(maxOf(movies.size, series.size)) { index ->
                movies.getOrNull(index)?.let(::add)
                series.getOrNull(index)?.let(::add)
            }
            addAll(other)
        }
    return balanced.take(count)
}
