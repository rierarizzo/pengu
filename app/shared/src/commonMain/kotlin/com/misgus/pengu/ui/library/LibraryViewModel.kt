package com.misgus.pengu.ui.library

import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.core.watched.WatchedCandidate
import com.misgus.pengu.ui.common.AccountScopedViewModel
import com.misgus.pengu.ui.common.WatchedBadgeSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state of the Library section. */
data class LibraryUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
    val items: List<LibraryItem> = emptyList(),
    val progressByContentId: Map<String, WatchProgress> = emptyMap(),
    val watchedIds: Set<String> = emptySet(),
    val isSyncingWatched: Boolean = false,
    val filter: LibraryFilter = LibraryFilter.All,
    /** Show only titles the watched badges do not mark; stacks on [filter]. */
    val onlyUnwatched: Boolean = false,
    val sort: LibrarySort = LibrarySort.Recent,
)

/**
 * Loads the library and the watch progress that decorates it.
 *
 * The watched badges are resolved in the background, so the grid paints as soon
 * as the content is there.
 */
class LibraryViewModel(
    account: AccountRepository,
    private val library: LibraryRepository,
    private val progress: ProgressRepository,
    badges: WatchedBadgeResolver,
) : AccountScopedViewModel(account) {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val watchedSync = WatchedBadgeSync(badges, account)

    override fun onAccountReady() = load(force = true)

    /** Narrows the grid; kept in memory, like the grid's scroll. */
    fun setFilter(filter: LibraryFilter) = _state.update { it.copy(filter = filter) }

    /** Drops the watched titles, on top of whichever kind is picked. */
    fun setOnlyUnwatched(enabled: Boolean) = _state.update { it.copy(onlyUnwatched = enabled) }

    /** Reorders the grid. */
    fun setSort(sort: LibrarySort) = _state.update { it.copy(sort = sort) }

    override fun onAccountCleared() = clear()

    init {
        // A write elsewhere (e.g. "add to library" from the detail screen)
        // must refresh the grid, so the repositories are observable too.
        viewModelScope.launch { library.changes.drop(1).collect { load(force = true) } }
        viewModelScope.launch { progress.changes.drop(1).collect { load(force = true) } }
    }

    /** Loads library and progress. Does nothing when already loaded, unless [force]. */
    fun load(force: Boolean = false) {
        if (!account.state.value.isSignedIn) {
            clear()
            return
        }
        if (_state.value.isLoading || (_state.value.hasLoaded && !force)) return
        viewModelScope.launch { doLoad() }
    }

    private suspend fun doLoad() {
        _state.update { it.copy(isLoading = true, error = null) }
        var progressEntries: List<WatchProgress> = emptyList()
        var loaded = false
        try {
            // The library and the progress that decorates it are independent; a
            // single round-trip each, run together.
            val (items, entries) =
                coroutineScope {
                    val libraryDeferred = async { library.all() }
                    val progressDeferred = async { progress.all() }
                    libraryDeferred.await() to progressDeferred.await()
                }
            progressEntries = entries
            _state.update {
                it.copy(
                    isLoading = false,
                    hasLoaded = true,
                    items = items,
                    progressByContentId = latestByContentId(progressEntries),
                )
            }
            loaded = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, error = error) }
        }
        if (loaded) syncWatched(progressEntries)
    }

    private suspend fun syncWatched(progressEntries: List<WatchProgress>) {
        val candidates = _state.value.items.map { WatchedCandidate(it.contentId, it.contentType) }
        watchedSync.resolve(
            candidates = candidates,
            progressEntries = progressEntries,
            onStart = { _state.update { it.copy(isSyncingWatched = true) } },
            onResult = { watched ->
                _state.update { it.copy(watchedIds = watched, isSyncingWatched = false) }
            },
        )
    }

    private fun clear() {
        watchedSync.invalidate()
        _state.value = LibraryUiState()
    }

    /**
     * Keeps the most recent progress entry per content id.
     *
     * Series have one row per episode, so the newest one describes what the user
     * is currently watching.
     */
    private fun latestByContentId(entries: List<WatchProgress>): Map<String, WatchProgress> {
        val latest = mutableMapOf<String, WatchProgress>()
        for (entry in entries) {
            val current = latest[entry.contentId]
            val left = entry.lastWatched
            val right = current?.lastWatched
            if (current == null || (left != null && (right == null || left > right))) {
                latest[entry.contentId] = entry
            }
        }
        return latest
    }
}
