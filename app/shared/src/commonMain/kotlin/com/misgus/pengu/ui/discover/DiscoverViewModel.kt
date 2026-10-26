package com.misgus.pengu.ui.discover

import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.CatalogExtras
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.common.AccountScopedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state of the discover feed. */
data class DiscoverUiState(
    val isLoading: Boolean = false,
    /** `true` while the next page of the selected catalog is appended. */
    val isLoadingMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
    /** Types the enabled addons offer, ordered movies, series, then the rest. */
    val types: List<ContentType> = emptyList(),
    val selectedType: ContentType? = null,
    /** Discover catalogs of [selectedType], in the addon's order. */
    val catalogs: List<CatalogRef> = emptyList(),
    val selectedCatalogKey: String? = null,
    /** Genres the selected catalog declares in its `genre` extra. */
    val genres: List<String> = emptyList(),
    /** Whether the selected catalog forces one of [genres]. */
    val genreRequired: Boolean = false,
    val selectedGenre: String? = null,
    val items: List<MetaPreview> = emptyList(),
    val hasMore: Boolean = false,
) {
    val selectedCatalog: CatalogRef? get() = catalogs.firstOrNull { it.key == selectedCatalogKey }
}

/**
 * Browses one discover catalog at a time, like Nuvio.
 *
 * The addons say which of their catalogs may be browsed ([CatalogRef.browsable])
 * and which extras each one takes, so the screen offers three pickers — type,
 * catalog and genre — and pages the catalog that is chosen. Searching is the
 * other half of the same screen and lives in [com.misgus.pengu.ui.search];
 * a catalog that only declares `search` never shows up here, and one that only
 * pages never shows up there.
 *
 * The selection is kept in memory for the session: a profile change drops it
 * with the rest of the state.
 */
class DiscoverViewModel(
    account: AccountRepository,
    private val catalogs: CatalogRepository,
    private val settings: SettingsRepository = InMemorySettingsRepository(),
) : AccountScopedViewModel(account) {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Every browsable catalog of the profile, so a type change needs no re-read. */
    private var sources: List<CatalogRef> = emptyList()

    /** Bumped by every new selection, so a late page cannot overwrite a newer one. */
    private var requestId = 0

    override fun onAccountReady() = load(force = true)

    override fun onAccountCleared() = clear()

    /** Reads the catalogs the addons offer. Does nothing when loaded, unless [force]. */
    fun load(force: Boolean = false) {
        if (!account.state.value.isSignedIn) {
            clear()
            return
        }
        if (loadJob?.isActive == true) return
        if (_state.value.hasLoaded && !force) return
        loadJob = viewModelScope.launch { reloadSources() }
    }

    /** Shows the catalogs of [type], starting with its first page. */
    fun selectType(type: ContentType) {
        if (type == _state.value.selectedType) return
        val refs = sources.filter { it.type == type }
        if (refs.isEmpty()) return
        _state.update {
            it.copy(
                selectedType = type,
                catalogs = refs,
                selectedCatalogKey = refs.first().key,
                selectedGenre = null,
            )
        }
        persistSelection()
        restart()
    }

    /** Shows [key]'s first page. */
    fun selectCatalog(key: String) {
        if (key == _state.value.selectedCatalogKey) return
        if (_state.value.catalogs.none { it.key == key }) return
        _state.update { it.copy(selectedCatalogKey = key, selectedGenre = null) }
        persistSelection()
        restart()
    }

    /** Filters the selected catalog by [genre]; `null` is "all genres". */
    fun selectGenre(genre: String?) {
        if (genre == _state.value.selectedGenre) return
        _state.update { it.copy(selectedGenre = genre) }
        persistSelection()
        restart()
    }

    /** Appends the next page of the selected catalog. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        val catalog = current.selectedCatalog ?: return
        if (!account.state.value.isSignedIn) return
        val id = requestId
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoadingMore = true) }
                val skip = current.items.size
                val page =
                    try {
                        runCatchingCancellable { catalogs.items(catalog, pageExtras(catalog, current.selectedGenre, skip)) }
                            .getOrThrow()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        if (id == requestId) _state.update { it.copy(isLoadingMore = false, hasMore = false) }
                        return@launch
                    }
                if (id != requestId) return@launch
                val seen = current.items.mapTo(mutableSetOf()) { "${it.type}:${it.id}" }
                val fresh = page.filter { seen.add("${it.type}:${it.id}") }
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + fresh,
                        // An addon that ignores `skip` would repeat its page forever.
                        hasMore = fresh.isNotEmpty() && it.items.size + fresh.size < MAX_ITEMS,
                    )
                }
            }
    }

    /** Re-reads the catalog list, keeping the selection when it still exists. */
    private suspend fun reloadSources() {
        _state.update { it.copy(isLoading = true, error = null) }
        val refs =
            try {
                runCatchingCancellable { catalogs.catalogs() }
                    .getOrThrow()
                    .filter { it.browsable }
                    .distinctBy { it.key }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.update { it.copy(isLoading = false, hasLoaded = true, error = error) }
                return
            }
        val types = refs.map { it.type }.distinct().sortedBy(::typeRank)
        sources = refs
        val current = _state.value
        val saved = settings.state.value
        val savedType = saved.discoverType?.let(::ContentType)?.takeIf { it in types }
        val type = current.selectedType?.takeIf { it in types } ?: savedType ?: types.firstOrNull()
        val typeCatalogs = refs.filter { it.type == type }
        val wantedKey = current.selectedCatalogKey ?: saved.discoverCatalogKey
        val catalog = typeCatalogs.firstOrNull { it.key == wantedKey } ?: typeCatalogs.firstOrNull()
        _state.update {
            it.copy(
                types = types,
                selectedType = type,
                catalogs = typeCatalogs,
                selectedCatalogKey = catalog?.key,
                // `loadFirstPage` resolves it against the catalog's own options, so
                // a genre the addon dropped falls back instead of breaking the feed.
                selectedGenre = current.selectedGenre ?: saved.discoverGenre,
                hasLoaded = true,
            )
        }
        persistSelection()
        loadFirstPage()
    }

    /** Saves the pickers, so the feed reopens where it was left. */
    private fun persistSelection() {
        val current = _state.value
        val type = current.selectedType ?: return
        val catalogKey = current.selectedCatalogKey
        val genre = current.selectedGenre
        val saved = settings.state.value
        if (saved.discoverType == type.value && saved.discoverCatalogKey == catalogKey && saved.discoverGenre == genre) return
        viewModelScope.launch { settings.setDiscoverSelection(type.value, catalogKey, genre) }
    }

    /** Drops whatever is on screen and loads the first page of the selection. */
    private fun restart() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFirstPage() }
    }

    private suspend fun loadFirstPage() {
        val id = ++requestId
        val catalog = _state.value.selectedCatalog
        if (catalog == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    genres = emptyList(),
                    genreRequired = false,
                    selectedGenre = null,
                    items = emptyList(),
                    hasMore = false,
                )
            }
            return
        }
        // A required genre has to be sent, so the first option is picked for the
        // user; the rest of the extras are only sent when the catalog knows them.
        val genre = resolveGenre(catalog, _state.value.selectedGenre)
        _state.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                genres = genreOptions(catalog),
                genreRequired = genreExtra(catalog)?.isRequired == true,
                selectedGenre = genre,
                items = emptyList(),
                hasMore = false,
            )
        }
        val page =
            try {
                runCatchingCancellable { catalogs.items(catalog, pageExtras(catalog, genre, skip = 0)) }.getOrThrow()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (id == requestId) _state.update { it.copy(isLoading = false, error = error) }
                return
            }
        if (id != requestId) return
        _state.update {
            it.copy(
                isLoading = false,
                items = page,
                hasMore = catalog.pages && page.isNotEmpty(),
            )
        }
    }

    private fun clear() {
        loadJob?.cancel()
        requestId++
        sources = emptyList()
        _state.value = DiscoverUiState()
    }

    private companion object {
        const val MAX_ITEMS = 2000
    }
}

private val CatalogRef.pages: Boolean get() = extras.any { it.name == CatalogExtras.SKIP }

private fun genreExtra(ref: CatalogRef) = ref.extras.firstOrNull { it.name == CatalogExtras.GENRE }

private fun genreOptions(ref: CatalogRef): List<String> = genreExtra(ref)?.options.orEmpty()

private fun resolveGenre(
    ref: CatalogRef,
    requested: String?,
): String? {
    val options = genreOptions(ref)
    return when {
        options.isEmpty() -> null
        requested != null && requested in options -> requested
        genreExtra(ref)?.isRequired == true -> options.firstOrNull()
        else -> null
    }
}

/** The extras the catalog actually accepts, so it is never sent something it ignores. */
private fun pageExtras(
    ref: CatalogRef,
    genre: String?,
    skip: Int,
): Map<String, String>? =
    buildMap {
        genre?.let { put(CatalogExtras.GENRE, it) }
        if (skip > 0 && ref.pages) put(CatalogExtras.SKIP, skip.toString())
    }.ifEmpty { null }

/** Movies, series, anime, then whatever else the addons declare. */
private fun typeRank(type: ContentType): String =
    when (type) {
        ContentTypes.MOVIE -> "0_movie"
        ContentTypes.SERIES -> "1_series"
        else ->
            when (type.value.lowercase()) {
                "anime" -> "2_anime"
                else -> "9_${type.value}"
            }
    }
