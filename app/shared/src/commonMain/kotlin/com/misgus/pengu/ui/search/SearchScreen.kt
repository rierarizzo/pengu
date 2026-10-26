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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_clear
import com.misgus.pengu.app.shared.generated.resources.discover_title
import com.misgus.pengu.app.shared.generated.resources.search_failed_title
import com.misgus.pengu.app.shared.generated.resources.search_no_results_title
import com.misgus.pengu.app.shared.generated.resources.search_nothing_matched
import com.misgus.pengu.app.shared.generated.resources.search_placeholder
import com.misgus.pengu.app.shared.generated.resources.search_title
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.isLandscape
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.ui.components.AppChip
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.AppSearchField
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterCard
import com.misgus.pengu.ui.components.SectionHeader
import com.misgus.pengu.ui.components.SectionTitle
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
import com.misgus.pengu.ui.theme.motionItem
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
    val services = LocalAppServices.current
    val settingsRepository = services.settings
    val settings by settingsRepository.state.collectAsStateWithLifecycle()
    val chrome = services.chrome
    // `/` arrives here as a counter rather than a callback: see `AppChrome`.
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(chrome.searchFocusRequest) {
        if (chrome.searchFocusRequest > 0) {
            searchFocus.requestFocus()
            chrome.consumeSearchFocus()
        }
    }

    LaunchedEffect(Unit) { discoverViewModel.load() }

    Column(Modifier.fillMaxSize()) {
        // The title names what is underneath: the discover feed while the query
        // is empty, the search results once there is one, like Nuvio.
        SectionTitle(
            stringResource(
                if (state.query.isBlank()) Res.string.discover_title else Res.string.search_title,
            ),
        )
        SearchField(query = state.query, onQueryChange = viewModel::onQueryChange, focusRequester = searchFocus)
        Box(Modifier.weight(1f)) {
            if (state.query.isBlank()) {
                Column(Modifier.fillMaxSize()) {
                    if (settings.recentSearches.isNotEmpty()) {
                        RecentSearches(
                            queries = settings.recentSearches,
                            onPick = viewModel::onQueryChange,
                            onClear = viewModel::clearHistory,
                        )
                    }
                    DiscoverFeed(
                        state = discoverState,
                        hideLabels = settings.hideLabels,
                        onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                        onSelectType = discoverViewModel::selectType,
                        onSelectCatalog = discoverViewModel::selectCatalog,
                        onSelectGenre = discoverViewModel::selectGenre,
                        onLoadMore = discoverViewModel::loadMore,
                        modifier = Modifier.weight(1f),
                    )
                }
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

/** Recent searches as chips, offered while the field is empty. */
@Composable
private fun RecentSearches(
    queries: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    val windowSize = LocalWindowSize.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = windowSize.screenPadding, end = AppSpacing.sm, bottom = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            items(queries, key = { it }) { query ->
                AppChip(
                    text = query,
                    onClick = { onPick(query) },
                )
            }
        }
        AppIconButton(
            icon = MaterialIcons.Close,
            contentDescription = stringResource(Res.string.action_clear),
            onClick = onClear,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    AppSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(Res.string.search_placeholder),
        clearDescription = stringResource(Res.string.action_clear),
        modifier =
            Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .padding(
                    start = LocalWindowSize.current.screenPadding,
                    end = LocalWindowSize.current.screenPadding,
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
        else -> {
            val screenPadding = LocalWindowSize.current.screenPadding
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.sections, key = { it.key }) { section ->
                    SectionHeader(section.title)
                    ResultsRail(section.items, posterSize, hideLabels, onOpenDetail)
                }
                item(key = "search-bottom") { Spacer(Modifier.height(screenPadding)) }
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
        // Slack for the hover scale, which grows past the card and would be
        // clipped by the rail's viewport.
        modifier = Modifier.height(railHeight + AppSpacing.hoverSlack * 2),
        contentPadding = PaddingValues(horizontal = windowSize.screenPadding, vertical = AppSpacing.hoverSlack),
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
                modifier = motionItem(),
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
