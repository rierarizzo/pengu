package com.misgus.pengu.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.empty_nothing_to_show
import com.misgus.pengu.app.shared.generated.resources.library_empty_message
import com.misgus.pengu.app.shared.generated.resources.library_error_title
import com.misgus.pengu.app.shared.generated.resources.library_filter_empty
import com.misgus.pengu.app.shared.generated.resources.library_filter_unwatched
import com.misgus.pengu.app.shared.generated.resources.library_title
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.ui.components.AppChip
import com.misgus.pengu.ui.components.FilterChip
import com.misgus.pengu.ui.components.FilterOption
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterCard
import com.misgus.pengu.ui.components.SectionTitle
import com.misgus.pengu.ui.components.SkeletonPosterGrid
import com.misgus.pengu.ui.components.SyncingIndicator
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.ContentNavigator
import com.misgus.pengu.ui.shell.ContentRoute
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.gridCells
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/** Library section: the grid of saved titles. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    navigator: ContentNavigator,
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsRepository = LocalAppServices.current.settings
    val settings by settingsRepository.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SectionTitle(stringResource(Res.string.library_title))
            if (state.items.isNotEmpty()) {
                LibraryToolbar(
                    filter = state.filter,
                    onlyUnwatched = state.onlyUnwatched,
                    sort = state.sort,
                    onFilter = viewModel::setFilter,
                    onOnlyUnwatched = viewModel::setOnlyUnwatched,
                    onSort = viewModel::setSort,
                )
            }
            LibraryContent(
                state = state,
                hideLabels = settings.hideLabels,
                onOpen = { navigator.push(ContentRoute.Detail(it, knownSaved = true)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (state.isSyncingWatched) {
            Box(Modifier.align(Alignment.TopEnd).padding(AppSpacing.xl)) {
                SyncingIndicator()
            }
        }
    }
}

/** Filter and order chips above the grid: the library is otherwise one flat list. */
@Composable
private fun LibraryToolbar(
    filter: LibraryFilter,
    onlyUnwatched: Boolean,
    sort: LibrarySort,
    onFilter: (LibraryFilter) -> Unit,
    onOnlyUnwatched: (Boolean) -> Unit,
    onSort: (LibrarySort) -> Unit,
) {
    val windowSize = LocalWindowSize.current
    LazyRow(
        // Air under the chips, so a hovered poster in the first row has room to
        // grow instead of touching them.
        contentPadding =
            PaddingValues(
                start = windowSize.screenPadding,
                end = windowSize.screenPadding,
                bottom = AppSpacing.md,
            ),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(LibraryFilter.entries) { entry ->
            AppChip(
                text = stringResource(entry.label),
                onClick = { onFilter(entry) },
                selected = entry == filter,
            )
        }
        item {
            // Its own axis: it narrows whichever kind is picked instead of being a
            // fourth kind, so "movies I have not seen" is one tap away.
            AppChip(
                text = stringResource(Res.string.library_filter_unwatched),
                onClick = { onOnlyUnwatched(!onlyUnwatched) },
                selected = onlyUnwatched,
            )
        }
        item {
            FilterChip(
                label = stringResource(sort.label),
                options = LibrarySort.entries.map { entry -> FilterOption(entry.name, stringResource(entry.label)) },
                selectedKey = sort.name,
                enabled = true,
                onSelected = { key -> LibrarySort.entries.firstOrNull { it.name == key }?.let(onSort) },
            )
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    hideLabels: Boolean,
    onOpen: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSize = LocalWindowSize.current
    val visible =
        remember(state.items, state.filter, state.onlyUnwatched, state.sort, state.watchedIds) {
            visibleLibraryItems(state.items, state.filter, state.onlyUnwatched, state.sort, state.watchedIds)
        }
    when {
        // Not read yet counts as loading: the empty message must not flash for a
        // frame before the first request starts.
        !state.hasLoaded && state.error == null -> SkeletonPosterGrid(hideLabels, modifier)
        state.error != null && !state.hasLoaded ->
            Message(
                icon = MaterialIcons.Error,
                title = stringResource(Res.string.library_error_title),
                message = stringResource(state.error.toUserMessage()),
            )
        state.items.isEmpty() ->
            Message(
                icon = MaterialIcons.Inbox,
                title = stringResource(Res.string.empty_nothing_to_show),
                message = stringResource(Res.string.library_empty_message),
            )
        visible.isEmpty() ->
            Message(
                icon = MaterialIcons.Inbox,
                title = stringResource(Res.string.empty_nothing_to_show),
                message = stringResource(Res.string.library_filter_empty),
            )
        else ->
            // The grid owns the padding through contentPadding: the scroll area
            // reaches the window edges, so the last row is never clipped.
            LazyVerticalGrid(
                columns = gridCells(windowSize),
                modifier = modifier.fillMaxSize(),
                // Top slack only: the grid clips to its viewport, so the hover
                // scale of the first row would lose its border without it.
                contentPadding =
                    PaddingValues(
                        start = windowSize.screenPadding,
                        end = windowSize.screenPadding,
                        top = AppSpacing.hoverSlack,
                        bottom = windowSize.screenPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
            ) {
                items(visible, key = { it.contentId }) { item ->
                    PosterCard(
                        name = item.name,
                        poster = item.poster,
                        caption = item.year?.toString(),
                        progress = state.progressByContentId[item.contentId]?.fraction,
                        watched = item.contentId in state.watchedIds,
                        hideLabels = hideLabels,
                        onTap = { onOpen(item) },
                        actions =
                            MediaActions(
                                item = item,
                                saved = true,
                                watched = item.contentId in state.watchedIds,
                                onMoreInfo = { onOpen(item) },
                            ),
                    )
                }
            }
    }
}
