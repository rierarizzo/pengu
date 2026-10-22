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
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.MetadataRepository
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
 * (watch progress joined with the library). The rows are fetched with a bounded
 * number of requests in flight; the watch history is read at the same time, and
 * the feed is published once, with both already in it, so nothing appears after
 * the first paint: no row half-loaded, no "Continue watching" popping in, and the
 * billboard is picked and enriched from the full set before it is shown. Only the
 * watched badges resolve afterwards, since they are not part of the layout.
 * Re-entering the section keeps what is on screen and refreshes behind it,
 * replacing it once at the end; changing profile drops it and starts from the
 * loading state again. The billboard comes from every loaded row (Smart) or from
 * the catalogs picked in Settings (Manual). Every browsable catalog gets a row;
 * only the items per row are capped.
 */
class HomeViewModel(
    account: AccountRepository,
    private val catalogs: CatalogRepository,
    private val library: LibraryRepository,
    private val progress: ProgressRepository,
    private val metadata: MetadataRepository,
    badges: WatchedBadgeResolver,
    private val tmdb: TmdbRepository = NoTmdbRepository(),
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
     * Profile whose rows are on screen.
     *
     * A different profile is a different Home, so its rows are dropped instead of
     * refreshed behind the previous profile's content.
     */
    private var loadedProfile: String? = null

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
        // applies at once instead of waiting for the next load.
        viewModelScope.launch {
            settings.state
                .map { it.heroSource to it.heroCatalogKeys }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (_state.value.rows.isNotEmpty()) repickFeatured() }
        }
        // A playback just saved its last position and closed. The section stays
        // composed while the player is open, so nothing else would notice: without
        // this tick the row only changes on the next load.
        viewModelScope.launch {
            playbackUpdates.closed.drop(1).collect {
                if (_state.value.hasLoaded && account.state.value.isSignedIn) refreshContinueWatching()
            }
        }
        // The caches were dropped in Settings: re-read the whole feed, so the row
        // and the billboard come back with what the backend has now.
        viewModelScope.launch {
            playbackUpdates.cachesCleared.drop(1).collect {
                if (account.state.value.isSignedIn) load(force = true)
            }
        }
    }

    override fun onAccountReady() {
        val profile =
            account.state.value.activeProfile
                ?.id
        if (loadedProfile != null && loadedProfile != profile) clear()
        loadedProfile = profile
        load(force = true)
    }

    override fun onAccountCleared() {
        loadedProfile = null
        clear()
    }

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
            // Re-entering Home keeps what is on screen and refreshes behind it,
            // unless the last load is recent enough to reuse.
            val last = lastLoadedAt
            if (last != null && now() - last < HOME_REFRESH_INTERVAL) return
        }
        loadJob = viewModelScope.launch { doLoad() }
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
            // The same builder the load uses, enrichment included: repainting the
            // row without it dropped the wide artwork back to a cropped poster.
            val row = buildContinueRow(entries)
            _state.update { it.copy(continueWatching = row) }
        }
    }

    /**
     * "Continue watching": progress joined with the saved titles, one card per
     * title, keeping only the ones that are actually mid-way, newest first.
     *
     * Progress arrives one row per episode, so the entries are collapsed by
     * `contentId` before anything else: the newest `lastWatched` is what the
     * title is "in the middle of". Collapsing before the mid-way filter is
     * deliberate — a finished last episode hides the series even when an older
     * episode was left unfinished, because that older episode is not where the
     * user is. The per-episode rows stay in the repository: resume and the
     * watched badge read them.
     *
     * The item is looked up in [libraryByContent] and, for a title the user never
     * saved, resolved from its metadata: the row is about what is being watched,
     * not about what is in the library, and a progress row carries only ids.
     */
    private suspend fun continueEntries(progressEntries: List<WatchProgress>): List<ContinueEntry> {
        val candidates =
            progressEntries
                .sortedByDescending { it.lastWatched ?: Instant.fromEpochMilliseconds(0) }
                .distinctBy { it.contentId }
                .filter { entry ->
                    val fraction = entry.fraction ?: 0.0
                    fraction > ProgressThresholds.IN_PROGRESS_MIN && fraction < ProgressThresholds.IN_PROGRESS_MAX
                }.take(MAX_CONTINUE)
        val items =
            coroutineScope {
                val permits = Semaphore(MAX_ENRICH_CONCURRENCY)
                candidates
                    .map { entry ->
                        async { libraryByContent[entry.contentId] ?: permits.withPermit { unsavedItem(entry) } }
                    }.awaitAll()
            }
        return candidates
            .zip(items)
            .mapNotNull { (entry, item) -> item?.let { ContinueEntry(it, entry) } }
    }

    /**
     * A [LibraryItem] for a title that was never saved.
     *
     * Best effort: an addon that does not answer it leaves the title out of the
     * row instead of showing a card with no name.
     */
    private suspend fun unsavedItem(entry: WatchProgress): LibraryItem? =
        runCatchingCancellable { metadata.detail(entry.contentType, entry.contentId) }
            .getOrNull()
            ?.let { meta ->
                LibraryItem(
                    id = meta.id,
                    contentId = entry.contentId,
                    contentType = meta.type,
                    name = meta.name,
                    poster = meta.poster,
                    background = meta.background,
                    logo = meta.logo,
                    textlessPoster = meta.textlessPoster,
                    description = meta.description,
                    releaseInfo = meta.releaseInfo,
                    imdbRating = meta.imdbRating,
                    genres = meta.genres,
                )
            }

    /** The row as the screen shows it: the entries, with their wide artwork. */
    private suspend fun buildContinueRow(progressEntries: List<WatchProgress>): List<ContinueEntry> =
        enrichContinue(continueEntries(progressEntries))

    private suspend fun doLoad() {
        _state.update { it.copy(isLoading = true, error = null) }
        val rows: List<HomeRow>
        val watching: Watching?
        try {
            // The catalogs and the watch history are independent, so they load
            // together and are published in the same update: "Continue watching"
            // must be there from the first frame, not pop in after the rows.
            coroutineScope {
                val rowsDeferred = async { fetchRows(catalogs.catalogs().filter { it.browsable }) }
                val watchingDeferred = async { readWatching() }
                rows = rowsDeferred.await()
                watching = watchingDeferred.await()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, error = error) }
            return
        }

        if (watching != null) {
            libraryByContent = watching.library.associateBy { it.contentId }
        }
        val progress = watching?.progress.orEmpty()
        // Enriched before the paint on purpose: a second artwork URL arriving after
        // the hero is on screen is exactly the swap this screen must not show.
        val featured = if (rows.isEmpty()) emptyList() else pickBillboard(rows)
        val continueWatching = buildContinueRow(progress)
        _state.update {
            it.copy(
                isLoading = false,
                hasLoaded = true,
                rows = rows,
                featured = featured,
                continueWatching = continueWatching,
                savedIds = watching?.library?.mapTo(mutableSetOf()) { item -> item.contentId }.orEmpty(),
            )
        }
        lastLoadedAt = now()
        syncWatched(progress)
    }

    /** One reading of the watch history: progress plus the titles it joins with. */
    private data class Watching(
        val progress: List<WatchProgress>,
        val library: List<LibraryItem>,
    )

    /**
     * Reads the two watch-history sources together.
     *
     * Best-effort: a backend failure leaves the row empty instead of failing
     * Home, because the feed is still worth showing.
     */
    private suspend fun readWatching(): Watching? =
        try {
            coroutineScope {
                val progressDeferred = async { progress.all() }
                val libraryDeferred = async { library.all() }
                Watching(progressDeferred.await(), libraryDeferred.await())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

    /**
     * Fetches every catalog row with at most [HOME_CATALOG_CONCURRENCY] requests in
     * flight, in catalog order.
     *
     * The limit is a sliding window, not a batch: a row that answers frees its slot
     * for the next catalog immediately, so one slow addon does not hold back the
     * rest of its group.
     */
    private suspend fun fetchRows(refs: List<CatalogRef>): List<HomeRow> {
        if (refs.isEmpty()) return emptyList()
        val order = refs.mapIndexed { index, ref -> ref.key to index }.toMap()
        val permits = Semaphore(HOME_CATALOG_CONCURRENCY)
        return coroutineScope {
            refs.map { ref -> async { permits.withPermit { loadRow(ref) } } }.awaitAll()
        }.filterNotNull()
            .sortedBy { order[it.ref.key] ?: Int.MAX_VALUE }
    }

    /** Loads one catalog row, or `null` when it is empty or fails. */
    private suspend fun loadRow(ref: CatalogRef): HomeRow? =
        runCatchingCancellable {
            val items = catalogs.items(ref)
            if (items.isEmpty()) null else HomeRow(ref, ref.name, items.take(MAX_ITEMS_PER_ROW))
        }.getOrNull()

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
     * Replaces the featured set with a new mix of the loaded rows.
     *
     * Only a deliberate change calls it (the billboard source in Settings); the set
     * otherwise stays put for the session, so Home does not rearrange itself while
     * it is being looked at. Items already on screen are dropped when the pool is
     * large enough, so the new pick is visibly different.
     */
    private fun repickFeatured() {
        val rows = _state.value.rows
        if (rows.isEmpty()) return
        val current =
            _state.value.featured
                .map { it.type to it.id }
                .toSet()
        viewModelScope.launch {
            // Picked and enriched before it replaces what is on screen, so the hero
            // does not swap twice.
            val featured = pickBillboard(rows, exclude = current)
            _state.update { it.copy(featured = featured) }
        }
    }

    /**
     * Picks the billboard set from [rows], enriched with TMDB artwork.
     *
     * The one place that turns rows into a billboard, so the first load and a
     * source change cannot drift. It runs before the feed is painted: the hero is
     * enriched while the loading screen is still up, never after.
     */
    private suspend fun pickBillboard(
        rows: List<HomeRow>,
        exclude: Set<Pair<ContentType, String>> = emptySet(),
    ): List<MetaPreview> = enrichFeatured(pickFeatured(billboardRows(rows), MAX_FEATURED, random, exclude))

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

    /**
     * Gives a "Continue watching" card the wide artwork it draws.
     *
     * A title added from a catalog that had no backdrop falls back to its portrait
     * poster, which a 16:9 card would crop; only those are looked up, in parallel
     * behind the billboard's semaphore, and the resolved payload is cached for the
     * process like every other TMDB read.
     */
    private suspend fun enrichContinue(entries: List<ContinueEntry>): List<ContinueEntry> {
        if (!tmdb.isConfigured || entries.isEmpty()) return entries
        return coroutineScope {
            val permits = Semaphore(MAX_ENRICH_CONCURRENCY)
            entries
                .map { entry ->
                    async {
                        if (!entry.item.background.isNullOrEmpty()) return@async entry
                        permits.withPermit {
                            val lookup =
                                TmdbLookup(
                                    type = entry.item.contentType,
                                    contentId = entry.item.contentId,
                                    name = entry.item.name,
                                    year = entry.item.year,
                                )
                            val backdrop =
                                withTimeoutOrNull(ENRICH_TIMEOUT) {
                                    runCatchingCancellable { tmdb.lookup(lookup) }.getOrNull()
                                }?.backdrop
                            if (backdrop.isNullOrEmpty()) entry else entry.copy(item = entry.item.copy(background = backdrop))
                        }
                    }
                }.awaitAll()
        }
    }

    private fun clear() {
        loadJob?.cancel()
        loadJob = null
        lastLoadedAt = null
        watchedSync.invalidate()
        _state.value = HomeUiState()
    }

    private companion object {
        const val MAX_ITEMS_PER_ROW = 20
        const val MAX_CONTINUE = 12
        const val MAX_FEATURED = 6
        const val MAX_ENRICH_CONCURRENCY = 4

        /**
         * How long a per-card enrichment may take before the card is drawn without
         * it: the rows are published together with the rest of Home, so an outside
         * source that stalls must not hold the screen.
         */
        val ENRICH_TIMEOUT = 4.seconds

        /**
         * Catalogs fetched at once. A sliding window, not a batch: a row that answers
         * frees its slot immediately, and the rows are still published together.
         */
        const val HOME_CATALOG_CONCURRENCY = 4

        /** A re-entry refreshes behind only when the loaded content is this old. */
        val HOME_REFRESH_INTERVAL = 5.minutes
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
