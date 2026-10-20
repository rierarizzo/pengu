package com.misgus.pengu.ui.home

import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.catalog.HomeSnapshot
import com.misgus.pengu.core.catalog.HomeSnapshotRow
import com.misgus.pengu.core.catalog.HomeSnapshotStore
import com.misgus.pengu.core.catalog.NoopHomeSnapshotStore
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.ProgressThresholds
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.enrichedWith
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.NoTmdbRepository
import com.misgus.pengu.core.repository.NoopAddonRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.TmdbLookup
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.core.watched.WatchedCandidate
import com.misgus.pengu.ui.common.AccountScopedViewModel
import com.misgus.pengu.ui.common.WatchedBadgeSync
import com.misgus.pengu.ui.services.PlaybackUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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
 * (watch progress joined with the library). Catalogs load in batches and the
 * screen paints after the first one, with the featured artwork already enriched
 * so the billboard never swaps; later batches only append rows. Re-entering the
 * section keeps the loaded content and refreshes behind it, and a cold start
 * paints the previous session's rows from disk before the first request. The
 * billboard comes from every loaded row (Smart) or from the catalogs picked in
 * Settings (Manual), and a stored set older than [HERO_PICK_TTL] is re-picked on
 * a cold start. The
 * watched badges are resolved after the paint. Every browsable catalog gets a
 * row; only the items per row are capped.
 */
class HomeViewModel(
    account: AccountRepository,
    private val catalogs: CatalogRepository,
    private val library: LibraryRepository,
    private val progress: ProgressRepository,
    badges: WatchedBadgeResolver,
    private val tmdb: TmdbRepository = NoTmdbRepository(),
    private val snapshot: HomeSnapshotStore = NoopHomeSnapshotStore(),
    addons: AddonRepository = NoopAddonRepository(),
    private val settings: SettingsRepository = InMemorySettingsRepository(),
    private val playbackUpdates: PlaybackUpdates = PlaybackUpdates(),
    private val random: Random = Random.Default,
    private val now: () -> Instant = { Clock.System.now() },
) : AccountScopedViewModel(account) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val watchedSync = WatchedBadgeSync(badges, account)

    /** In-flight load, so the screen effect and the account observer cannot double-load. */
    private var loadJob: Job? = null

    /** When the last load finished, so a re-entry refreshes only when stale. */
    private var lastLoadedAt: Instant? = null

    /**
     * The saved titles from the last load, by content id.
     *
     * Kept so repainting "Continue watching" after a playback does not re-read
     * the library: only the progress changed.
     */
    private var libraryByContent: Map<String, LibraryItem> = emptyMap()

    /**
     * When the billboard set was picked.
     *
     * Kept across loads so the snapshot carries the pick time, not the time of the
     * last write: a re-entry that refreshes the rows behind must not make the set
     * look freshly picked (nor older than it is).
     */
    private var billboardAt: Instant? = null

    init {
        // A save or a remove made elsewhere (the detail screen, the library)
        // bumps the repository; keep the billboard's save state in sync without
        // reloading the catalogs. A refresh is skipped while a write of our own
        // is in flight, so the optimistic flip is not clobbered by stale reads.
        viewModelScope.launch {
            library.changes.drop(1).collect { if (_state.value.savingIds.isEmpty()) refreshSaved() }
        }
        // The addon list is answered from the last snapshot and refreshed behind
        // it; when the addons really changed (the user installed one in another
        // client), the rows on screen are loaded again.
        viewModelScope.launch {
            addons.changes.collect { if (_state.value.hasLoaded) load(force = true) }
        }
        // Picking a different billboard source is a deliberate change, so it
        // applies at once instead of waiting for the next rotation or reload.
        viewModelScope.launch {
            settings.state
                .map { it.heroSource to it.heroCatalogKeys }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (_state.value.rows.isNotEmpty()) rotateFeatured() }
        }
        // A playback just saved its last position and closed. The section stays
        // composed while the player is open, so nothing else would notice: without
        // this tick the row only changes on the next load.
        viewModelScope.launch {
            playbackUpdates.closed.drop(1).collect {
                if (_state.value.hasLoaded && account.state.value.isSignedIn) refreshContinueWatching()
            }
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
        if (loadJob?.isActive == true) return
        if (_state.value.hasLoaded && !force) {
            // Re-entering Home keeps the loaded rows on screen and refreshes
            // behind them, unless the last load is recent enough to reuse.
            val last = lastLoadedAt
            if (last != null && now() - last < HOME_REFRESH_INTERVAL) return
            loadJob = viewModelScope.launch { doLoad(refresh = true) }
            return
        }
        loadJob = viewModelScope.launch { doLoad(refresh = false) }
    }

    /**
     * Re-reads watch progress and repaints only the "Continue watching" row.
     *
     * The catalogs are left alone on purpose: a playback only changes progress,
     * and reloading the rows would re-fetch every addon for nothing.
     */
    private fun refreshContinueWatching() {
        // A full load is already computing the row from fresher data.
        if (loadJob?.isActive == true) return
        viewModelScope.launch {
            val entries = runCatchingCancellable { progress.all() }.getOrNull() ?: return@launch
            val row = continueEntries(entries)
            _state.update { it.copy(continueWatching = row) }
        }
    }

    /**
     * "Continue watching": progress joined with the saved titles, keeping only the
     * titles that are actually mid-way, newest first.
     *
     * The join runs against [libraryByContent] instead of a fresh library read, so
     * a refresh after a playback costs one request.
     */
    private fun continueEntries(progressEntries: List<WatchProgress>): List<ContinueEntry> =
        progressEntries
            .mapNotNull { entry ->
                val item = libraryByContent[entry.contentId] ?: return@mapNotNull null
                val fraction = entry.fraction ?: 0.0
                if (fraction <= ProgressThresholds.IN_PROGRESS_MIN ||
                    fraction >= ProgressThresholds.IN_PROGRESS_MAX
                ) {
                    return@mapNotNull null
                }
                ContinueEntry(item, entry)
            }.sortedByDescending { it.progress.lastWatched ?: Instant.fromEpochMilliseconds(0) }
            .take(MAX_CONTINUE)

    private suspend fun doLoad(refresh: Boolean) {
        _state.update { it.copy(isLoading = true, error = null) }
        var progressEntries: List<WatchProgress> = emptyList()
        var loaded = false
        try {
            // Paint the previous session's rows from disk before the first
            // request, so a cold start shows content instead of a spinner. The
            // network pass then replaces them row by row; the billboard that is
            // already on screen is kept, unless its set is old enough to be
            // re-picked (the stored rows paint either way).
            val stored = restoreSnapshot()
            val keepsBillboard = refresh || (stored != null && !stored.billboardExpired())
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
            libraryByContent = libraryItems.associateBy { it.contentId }
            val continueWatching = continueEntries(progressEntries)
            val savedIds = libraryItems.mapTo(mutableSetOf()) { item -> item.contentId }

            // Catalogs load in batches and the screen paints after the first one,
            // instead of waiting for the slowest catalog. The featured set is
            // picked and enriched from the rows on screen before that first paint,
            // so the billboard never shows the catalog artwork and then swaps to
            // TMDB's; later batches only append rows and leave the billboard alone.
            // A refresh keeps the billboard that is already on screen, and so does
            // a cold start with stored rows: there the rows stay until the load
            // finishes, so the screen never shrinks to the first batch's catalogs,
            // and an expired set is re-picked from the final rows below.
            val paintsEarly = !refresh && stored == null && _state.value.rows.isEmpty()
            val order = refs.mapIndexed { index, ref -> ref.key to index }.toMap()
            val batches = refs.chunked(HOME_CATALOG_FETCH_BATCH_SIZE)
            val rows = mutableListOf<HomeRow>()
            var featured = if (keepsBillboard) _state.value.featured else emptyList()

            batches.forEachIndexed { batchIndex, batch ->
                val results = coroutineScope { batch.map { ref -> async { loadRow(ref) } }.awaitAll() }
                rows += results.filterNotNull()
                rows.sortBy { order[it.ref.key] ?: Int.MAX_VALUE }
                val firstContentPaint = paintsEarly && featured.isEmpty() && rows.isNotEmpty()
                if (firstContentPaint) {
                    featured = pickBillboard(rows)
                }
                val last = batchIndex == batches.lastIndex
                val publishNow =
                    paintsEarly &&
                        (batchIndex == 0 || (batchIndex + 1) % HOME_CATALOG_PUBLISH_INTERVAL == 0 || last)
                if (publishNow || firstContentPaint) {
                    publishContent(isLoading = true, continueWatching, savedIds, rows, featured)
                } else if (batchIndex == 0) {
                    // Content was already on screen: the stored rows stay, but the
                    // backend-owned rows (continue watching) do not have to wait for
                    // the last batch.
                    publishBackend(continueWatching, savedIds)
                }
            }

            // A set that was dropped above (its snapshot had aged out) is picked
            // from the rows that are actually loaded, not from the first batch.
            if (featured.isEmpty() && rows.isNotEmpty()) {
                featured = pickBillboard(rows)
            }

            publishContent(isLoading = false, continueWatching, savedIds, rows, featured)
            lastLoadedAt = now()
            writeSnapshot(rows, featured, billboardAt)
            loaded = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, error = error) }
        }
        // Home paints without waiting for the watch history; the checks fill in
        // when the lookup finishes.
        if (loaded) syncWatched(progressEntries)
    }

    /** Publishes the loaded content, keeping [rows] stable for the state. */
    private fun publishContent(
        isLoading: Boolean,
        continueWatching: List<ContinueEntry>,
        savedIds: Set<String>,
        rows: List<HomeRow>,
        featured: List<MetaPreview>,
    ) {
        _state.update {
            it.copy(
                isLoading = isLoading,
                hasLoaded = true,
                continueWatching = continueWatching,
                savedIds = savedIds,
                rows = rows.toList(),
                featured = featured,
            )
        }
    }

    /** Publishes the backend-owned rows, leaving the loaded catalogs untouched. */
    private fun publishBackend(
        continueWatching: List<ContinueEntry>,
        savedIds: Set<String>,
    ) {
        _state.update { it.copy(continueWatching = continueWatching, savedIds = savedIds) }
    }

    /** Loads one catalog row, or `null` when it is empty or fails. */
    private suspend fun loadRow(ref: CatalogRef): HomeRow? =
        runCatchingCancellable {
            val items = catalogs.items(ref)
            if (items.isEmpty()) null else HomeRow(ref, ref.name, items.take(MAX_ITEMS_PER_ROW))
        }.getOrNull()

    /**
     * Publishes the rows of the last session and returns its snapshot.
     *
     * The rows go on screen whatever their age — that is the point of the
     * snapshot — while the caller decides whether the billboard that came with
     * them is recent enough to keep. An unreadable snapshot is treated as no
     * snapshot: the load simply starts from the network.
     */
    private suspend fun restoreSnapshot(): HomeSnapshot? {
        val stored = runCatchingCancellable { snapshot.read(profileKey()) }.getOrNull() ?: return null
        val rows =
            stored.rows
                .filter { it.items.isNotEmpty() }
                .map { HomeRow(ref = it.ref, title = it.ref.name, items = it.items.take(MAX_ITEMS_PER_ROW)) }
        if (rows.isEmpty()) return null
        _state.update { it.copy(rows = rows, featured = stored.featured, hasLoaded = true) }
        if (billboardAt == null) billboardAt = stored.featuredAt
        return stored
    }

    /**
     * The rows the billboard is built from.
     *
     * Smart draws from every loaded row. Manual narrows it to the catalogs picked
     * in Settings, and falls back to every row when none of them is loaded, so an
     * addon removed in the meantime cannot leave Home without a billboard.
     */
    private fun billboardRows(rows: List<HomeRow>): List<HomeRow> {
        val current = settings.state.value
        if (current.heroSource != HeroSource.MANUAL || current.heroCatalogKeys.isEmpty()) return rows
        return rows.filter { it.ref.key in current.heroCatalogKeys }.ifEmpty { rows }
    }

    /** Whether the stored set is old enough that a cold start should re-pick it. */
    private fun HomeSnapshot.billboardExpired(): Boolean {
        val pickedAt = featuredAt ?: return true
        return now() - pickedAt >= HERO_PICK_TTL
    }

    /** Stores what is on screen, so the next cold start can paint it. */
    private suspend fun writeSnapshot(
        rows: List<HomeRow>,
        featured: List<MetaPreview>,
        featuredAt: Instant?,
    ) {
        if (rows.isEmpty()) return
        runCatchingCancellable {
            snapshot.write(
                profileKey(),
                HomeSnapshot(
                    rows = rows.map { HomeSnapshotRow(it.ref, it.items) },
                    featured = featured,
                    featuredAt = featuredAt,
                ),
            )
        }
    }

    /**
     * Scopes the on-disk snapshot.
     *
     * The integer profile id is what every other local cache keys on, and a
     * backend without profiles (local mode) keeps a single snapshot under
     * [LOCAL_PROFILE_KEY].
     */
    private fun profileKey(): String =
        account.state.value.activeProfile
            ?.profileId
            ?.toString() ?: LOCAL_PROFILE_KEY

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
            val featured = pickBillboard(rows, exclude = current)
            _state.update { it.copy(featured = featured) }
            writeSnapshot(rows, featured, billboardAt)
        }
    }

    /**
     * Picks the billboard set from [rows] and records when it was picked.
     *
     * The one place that turns rows into a billboard, so the first paint, a cold
     * start that re-picks an aged set and the timed rotation cannot drift.
     */
    private suspend fun pickBillboard(
        rows: List<HomeRow>,
        exclude: Set<Pair<ContentType, String>> = emptySet(),
    ): List<MetaPreview> {
        billboardAt = now()
        return enrichFeatured(pickFeatured(billboardRows(rows), MAX_FEATURED, random, exclude))
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
        lastLoadedAt = null
        billboardAt = null
        watchedSync.invalidate()
        _state.value = HomeUiState()
    }

    private companion object {
        const val MAX_ITEMS_PER_ROW = 20
        const val MAX_CONTINUE = 12
        const val MAX_FEATURED = 6
        const val MAX_ENRICH_CONCURRENCY = 4

        /**
         * Catalogs fetched per batch, and how often a batch is published while
         * the first load streams in. The screen paints after the first batch, so
         * a phone with many addons does not wait for the slowest catalog; the
         * batch size is also the fetch concurrency.
         */
        const val HOME_CATALOG_FETCH_BATCH_SIZE = 4
        const val HOME_CATALOG_PUBLISH_INTERVAL = 2

        /** A re-entry refreshes behind only when the loaded content is this old. */
        val HOME_REFRESH_INTERVAL = 5.minutes

        /**
         * How long a stored billboard set is reused before a cold start re-picks
         * it. Long on purpose: the set is enriched and on disk, so replacing it
         * is a visible change, not a repair.
         */
        val HERO_PICK_TTL = 6.hours

        /** Snapshot key for a backend without profiles. */
        const val LOCAL_PROFILE_KEY = "local"
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
