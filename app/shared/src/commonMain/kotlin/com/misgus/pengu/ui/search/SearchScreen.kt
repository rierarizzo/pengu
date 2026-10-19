package com.misgus.pengu.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_clear
import com.misgus.pengu.app.shared.generated.resources.search_failed_title
import com.misgus.pengu.app.shared.generated.resources.search_no_results_title
import com.misgus.pengu.app.shared.generated.resources.search_nothing_matched
import com.misgus.pengu.app.shared.generated.resources.search_placeholder
import com.misgus.pengu.app.shared.generated.resources.search_type_movies
import com.misgus.pengu.app.shared.generated.resources.search_type_other
import com.misgus.pengu.app.shared.generated.resources.search_type_series
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.ui.components.AppSearchField
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.MetaPosterCard
import com.misgus.pengu.ui.components.SectionHeader
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

/**
 * Search section: a free-text search across every searchable addon catalog,
 * with the discover feed underneath the bar while the query is empty.
 *
 * Discover used to be its own section; it lives here now. Results are grouped in
 * Movies / Series / Other rails.
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

    LaunchedEffect(Unit) { discoverViewModel.load() }

    Column(Modifier.fillMaxSize()) {
        SearchField(query = state.query, onQueryChange = viewModel::onQueryChange)
        Box(Modifier.weight(1f)) {
            if (state.query.isBlank()) {
                DiscoverFeed(
                    state = discoverState,
                    items = discoverState.items,
                    hideLabels = settings.hideLabels,
                    onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                    onLoadMore = discoverViewModel::loadMore,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SearchResults(
                    state = state,
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
                    top = LocalWindowSize.current.screenPadding,
                    bottom = AppSpacing.md,
                ),
    )
}

@Composable
private fun SearchResults(
    state: SearchUiState,
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
        state.results.isEmpty() && state.hasSearched ->
            Message(
                icon = FluentIcons.Inbox,
                title = stringResource(Res.string.search_no_results_title),
                message = stringResource(Res.string.search_nothing_matched, state.query.trim()),
            )
        else -> {
            val movies = state.results.filter { it.type == ContentTypes.MOVIE }
            val series = state.results.filter { it.type == ContentTypes.SERIES }
            val other = state.results.filter { it.type != ContentTypes.MOVIE && it.type != ContentTypes.SERIES }
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
