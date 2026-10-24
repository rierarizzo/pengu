package com.misgus.pengu.ui.search

import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.CatalogExtras
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.common.AccountScopedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One catalog's answer to the query, shown as a rail of its own. */
data class SearchSection(
    /** Stable key for the rail, so a re-search does not reuse the old one. */
    val key: String,
    /** The catalog the results come from. */
    val title: String,
    val items: List<MetaPreview>,
)

/** Immutable state of the Search section. */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    /** One section per catalog that answered, in the addons' order. */
    val sections: List<SearchSection> = emptyList(),
    /** `true` once a query has completed, so "no results" is not shown before. */
    val hasSearched: Boolean = false,
    val error: Throwable? = null,
)

/**
 * Searches every catalog that declares the `search` extra, in parallel, and
 * keeps one rail per catalog, like Nuvio.
 *
 * Merging the answers would hide which addon found what and would drop the same
 * title found by a second catalog, so each one keeps its own section; a catalog
 * that answers nothing simply has no rail. [CatalogRef.searches] decides what is
 * asked: a catalog that needs an extra the app cannot fill is skipped instead of
 * being queried with a bare term.
 *
 * The query is debounced so typing does not fire a request per keystroke.
 */
class SearchViewModel(
    account: AccountRepository,
    private val catalogs: CatalogRepository,
) : AccountScopedViewModel(account) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** Profile the cached searchable catalogs belong to, or `null` when unset. */
    private var catalogProfile: Int? = null
    private var searchable: List<CatalogRef> = emptyList()

    private var requestId = 0

    override fun onAccountReady() = Unit

    override fun onAccountCleared() = clear()

    /** Updates the query and schedules a debounced search. */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            requestId++
            _state.update {
                it.copy(isSearching = false, sections = emptyList(), hasSearched = false, error = null)
            }
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MS)
                search(trimmed)
            }
    }

    private suspend fun search(query: String) {
        val id = ++requestId
        _state.update { it.copy(isSearching = true, error = null) }
        try {
            val sections =
                coroutineScope {
                    searchableCatalogs()
                        .map { ref ->
                            async {
                                val items =
                                    runCatchingCancellable {
                                        catalogs.items(ref, mapOf(CatalogExtras.SEARCH to query))
                                    }.getOrDefault(emptyList())
                                ref to items
                            }
                        }.awaitAll()
                }.mapNotNull { (ref, items) ->
                    if (items.isEmpty()) {
                        null
                    } else {
                        SearchSection(
                            key = "${ref.key}:${query.lowercase()}",
                            title = ref.name,
                            items = items,
                        )
                    }
                }
            if (id != requestId) return
            _state.update { it.copy(isSearching = false, sections = sections, hasSearched = true) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (id != requestId) return
            _state.update { it.copy(isSearching = false, error = error, hasSearched = true) }
        }
    }

    /** Searchable catalogs for the active profile, fetched once and cached. */
    private suspend fun searchableCatalogs(): List<CatalogRef> {
        val profile =
            account.state.value.activeProfile
                ?.profileId
        if (profile == catalogProfile && searchable.isNotEmpty()) return searchable
        val refs = catalogs.catalogs().filter { it.searches }
        catalogProfile = profile
        searchable = refs
        return refs
    }

    private fun clear() {
        searchJob?.cancel()
        requestId++
        catalogProfile = null
        searchable = emptyList()
        _state.value = SearchUiState()
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
