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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.isLandscape
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.ui.components.AppSearchField
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterCard
import com.misgus.pengu.ui.components.SectionHeader
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.discover.DiscoverFeed
import com.misgus.pengu.ui.discover.DiscoverViewModel
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.ContentNavigator
import com.misgus.pengu.ui.shell.ContentRoute
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LANDSCAPE_ART_ASPECT
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.cellWidth
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.landscapeCardWidth
import com.misgus.pengu.ui.theme.posterCardHeight
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/**
 * Search section: a free-text search across every searchable addon catalog, with
 * the discover feed underneath the bar while the query is empty.
 *
 * A search keeps one rail per catalog, like Nuvio: each addon answers with its
 * own results, so the sections say where a title came from instead of merging
 * everything into three rails that hide the source.
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
                    hideLabels = settings.hideLabels,
                    onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                    onSelectType = discoverViewModel::selectType,
                    onSelectCatalog = discoverViewModel::selectCatalog,
                    onSelectGenre = discoverViewModel::selectGenre,
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
        state.isSearching && state.sections.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null && state.sections.isEmpty() ->
            Message(
                icon = MaterialIcons.Error,
                title = stringResource(Res.string.search_failed_title),
                message = stringResource(state.error.toUserMessage()),
            )
        state.sections.isEmpty() && state.hasSearched ->
            Message(
                icon = MaterialIcons.Inbox,
                title = stringResource(Res.string.search_no_results_title),
                message = stringResource(Res.string.search_nothing_matched, state.query.trim()),
            )
        else ->
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.sections, key = { it.key }) { section ->
                    SectionHeader(section.title)
                    ResultsRail(section.items, posterSize, hideLabels, onOpenDetail)
                }
                item(key = "search-bottom") { Spacer(Modifier.height(AppSpacing.xl)) }
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
    // A landscape result (a live event, a channel) keeps its wide, shorter
    // artwork; the rail is as tall as the tallest card in it.
    val wideWidth = landscapeCardWidth(cardWidth)
    val railHeight =
        previews.maxOfOrNull { item ->
            if (item.isLandscape) {
                posterCardHeight(wideWidth, hideLabels, LANDSCAPE_ART_ASPECT)
            } else {
                posterCardHeight(cardWidth, hideLabels)
            }
        } ?: posterCardHeight(cardWidth, hideLabels)
    LazyRow(
        modifier = Modifier.height(railHeight),
        contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
    ) {
        items(previews, key = { "${it.type}:${it.id}" }) { item ->
            val libraryItem = LibraryItem.fromPreview(item)
            PosterCard(
                name = item.name,
                poster = item.poster,
                caption = item.releaseInfo,
                width = if (item.isLandscape) wideWidth else cardWidth,
                hideLabels = hideLabels,
                landscape = item.isLandscape,
                onTap = { onOpenDetail(libraryItem) },
                actions =
                    MediaActions(
                        item = libraryItem,
                        onMoreInfo = { onOpenDetail(libraryItem) },
                    ),
            )
        }
    }
}
