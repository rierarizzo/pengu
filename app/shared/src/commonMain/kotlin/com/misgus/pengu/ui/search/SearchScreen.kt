package com.misgus.pengu.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_clear
import com.misgus.pengu.app.shared.generated.resources.search_all_genres
import com.misgus.pengu.app.shared.generated.resources.search_discover
import com.misgus.pengu.app.shared.generated.resources.search_failed_title
import com.misgus.pengu.app.shared.generated.resources.search_no_results_title
import com.misgus.pengu.app.shared.generated.resources.search_nothing_matched
import com.misgus.pengu.app.shared.generated.resources.search_nothing_matched_typed
import com.misgus.pengu.app.shared.generated.resources.search_nothing_matched_typed_genre
import com.misgus.pengu.app.shared.generated.resources.search_placeholder
import com.misgus.pengu.app.shared.generated.resources.search_type_all
import com.misgus.pengu.app.shared.generated.resources.search_type_movies
import com.misgus.pengu.app.shared.generated.resources.search_type_movies_lower
import com.misgus.pengu.app.shared.generated.resources.search_type_other
import com.misgus.pengu.app.shared.generated.resources.search_type_series
import com.misgus.pengu.app.shared.generated.resources.search_type_series_lower
import com.misgus.pengu.app.shared.generated.resources.search_type_titles
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.AppSearchField
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.MetaPosterCard
import com.misgus.pengu.ui.components.SectionHeader
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.discover.DiscoverFeed
import com.misgus.pengu.ui.discover.DiscoverViewModel
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.ContentNavigator
import com.misgus.pengu.ui.shell.ContentRoute
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.cellWidth
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.posterCardHeight
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/** Which title types the search results and the discover feed show. */
enum class MediaTypeFilter {
    ALL,
    MOVIES,
    SERIES,
    ;

    /** `true` when [preview] belongs to the selected type. */
    fun matches(preview: MetaPreview): Boolean =
        when (this) {
            ALL -> true
            MOVIES -> preview.type == ContentTypes.MOVIE
            SERIES -> preview.type == ContentTypes.SERIES
        }
}

/**
 * Search section: a free-text search across every searchable addon catalog,
 * with the discover feed underneath the bar while the query is empty.
 *
 * Discover used to be its own section; it lives here now. Two dropdowns — the
 * title type and the genre — narrow both the feed and the search results.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    discoverViewModel: DiscoverViewModel,
    navigator: ContentNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.state.collectAsStateWithLifecycle()
    val settingsRepository = LocalAppServices.current.settings
    val settings by settingsRepository.state.collectAsStateWithLifecycle()
    var typeFilter by remember { mutableStateOf(MediaTypeFilter.ALL) }
    var genreFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { discoverViewModel.load() }

    // Genre options come from what has actually loaded, so the menu never lists
    // a genre the addons do not return.
    val genres =
        remember(discoverState.items, state.results) {
            (discoverState.items + state.results)
                .flatMap { it.genres }
                .distinct()
                .sorted()
        }
    val matches: (MetaPreview) -> Boolean = { preview ->
        typeFilter.matches(preview) && (genreFilter == null || genreFilter in preview.genres)
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query = state.query, onQueryChange = viewModel::onQueryChange)
        if (state.query.isBlank()) {
            Text(
                text = stringResource(Res.string.search_discover),
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier
                        .padding(
                            start = LocalWindowSize.current.screenPadding,
                            end = LocalWindowSize.current.screenPadding,
                            top = AppSpacing.lg,
                        ).sectionHeading(),
            )
        }
        FilterRow(
            typeFilter = typeFilter,
            onTypeSelect = { typeFilter = it },
            genres = genres,
            genreFilter = genreFilter,
            onGenreSelect = { genreFilter = it },
        )
        Box(Modifier.weight(1f)) {
            if (state.query.isBlank()) {
                DiscoverFeed(
                    state = discoverState,
                    items = discoverState.items.filter(matches),
                    hideLabels = settings.hideLabels,
                    onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                    onLoadMore = discoverViewModel::loadMore,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SearchResults(
                    state = state,
                    matches = matches,
                    emptyMessage = emptyMessage(state, typeFilter, genreFilter),
                    posterSize = settings.posterSize,
                    hideLabels = settings.hideLabels,
                    onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    AppSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(Res.string.search_placeholder),
        clearDescription = stringResource(Res.string.action_clear),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = LocalWindowSize.current.screenPadding,
                    end = LocalWindowSize.current.screenPadding,
                    top = AppSpacing.lg,
                ),
    )
}

/** The type and genre dropdowns, shared by the discover feed and the results. */
@Composable
private fun FilterRow(
    typeFilter: MediaTypeFilter,
    onTypeSelect: (MediaTypeFilter) -> Unit,
    genres: List<String>,
    genreFilter: String?,
    onGenreSelect: (String?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalWindowSize.current.screenPadding, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val typeLabels =
            mapOf(
                MediaTypeFilter.ALL to stringResource(Res.string.search_type_all),
                MediaTypeFilter.MOVIES to stringResource(Res.string.search_type_movies),
                MediaTypeFilter.SERIES to stringResource(Res.string.search_type_series),
            )
        val allGenres = stringResource(Res.string.search_all_genres)
        FilterDropdown(
            options = MediaTypeFilter.entries,
            selected = typeFilter,
            optionLabel = { typeLabels.getValue(it) },
            onSelect = onTypeSelect,
        )
        FilterDropdown(
            options = listOf<String?>(null) + genres,
            selected = genreFilter,
            optionLabel = { it ?: allGenres },
            onSelect = onGenreSelect,
        )
    }
}

/** A compact Material outlined dropdown: it shows the selection and opens a menu. */
@Composable
private fun <T> FilterDropdown(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        ActionButton(
            text = optionLabel(selected),
            onClick = { expanded = true },
            style = ActionButtonStyle.Outline,
            trailingIcon = FluentIcons.ChevronDown,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    matches: (MetaPreview) -> Boolean,
    emptyMessage: String,
    posterSize: PosterSize,
    hideLabels: Boolean,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    when {
        state.isSearching && state.results.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null && state.results.isEmpty() ->
            Message(
                icon = FluentIcons.Error,
                title = stringResource(Res.string.search_failed_title),
                message = stringResource(state.error.toUserMessage()),
            )
        state.results.none(matches) && state.hasSearched ->
            Message(
                icon = FluentIcons.Inbox,
                title = stringResource(Res.string.search_no_results_title),
                message = emptyMessage,
            )
        else -> {
            val visible = state.results.filter(matches)
            val movies = visible.filter(MediaTypeFilter.MOVIES::matches)
            val series = visible.filter(MediaTypeFilter.SERIES::matches)
            val other = visible.filter { !MediaTypeFilter.MOVIES.matches(it) && !MediaTypeFilter.SERIES.matches(it) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (movies.isNotEmpty()) {
                    SectionHeader(stringResource(Res.string.search_type_movies))
                    ResultsRail(movies, posterSize, hideLabels, onOpenDetail)
                }
                if (series.isNotEmpty()) {
                    SectionHeader(stringResource(Res.string.search_type_series))
                    ResultsRail(series, posterSize, hideLabels, onOpenDetail)
                }
                if (other.isNotEmpty()) {
                    SectionHeader(stringResource(Res.string.search_type_other))
                    ResultsRail(other, posterSize, hideLabels, onOpenDetail)
                }
                Spacer(Modifier.height(AppSpacing.xl))
            }
        }
    }
}

@Composable
private fun emptyMessage(
    state: SearchUiState,
    typeFilter: MediaTypeFilter,
    genre: String?,
): String {
    val query = state.query.trim()
    if (typeFilter == MediaTypeFilter.ALL && genre == null) {
        return stringResource(Res.string.search_nothing_matched, query)
    }
    val type =
        when (typeFilter) {
            MediaTypeFilter.ALL -> stringResource(Res.string.search_type_titles)
            MediaTypeFilter.MOVIES -> stringResource(Res.string.search_type_movies_lower)
            MediaTypeFilter.SERIES -> stringResource(Res.string.search_type_series_lower)
        }
    return if (genre != null) {
        stringResource(Res.string.search_nothing_matched_typed_genre, type, genre, query)
    } else {
        stringResource(Res.string.search_nothing_matched_typed, type, query)
    }
}

@Composable
private fun ResultsRail(
    previews: List<MetaPreview>,
    posterSize: PosterSize,
    hideLabels: Boolean,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    val windowSize = LocalWindowSize.current
    val cardWidth = posterSize.cellWidth(windowSize)
    LazyRow(
        modifier = Modifier.height(posterCardHeight(cardWidth, hideLabels)),
        contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
    ) {
        items(previews, key = { "${it.type}:${it.id}" }) { item ->
            MetaPosterCard(
                preview = item,
                width = cardWidth,
                hideLabels = hideLabels,
                onTap = { onOpenDetail(LibraryItem.fromPreview(item)) },
            )
        }
    }
}
