package com.misgus.pengu.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.empty_nothing_to_show
import com.misgus.pengu.app.shared.generated.resources.library_empty_message
import com.misgus.pengu.app.shared.generated.resources.library_error_title
import com.misgus.pengu.app.shared.generated.resources.library_title
import com.misgus.pengu.core.model.LibraryItem
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

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    hideLabels: Boolean,
    onOpen: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSize = LocalWindowSize.current
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
        else ->
            // The grid owns the padding through contentPadding: the scroll area
            // reaches the window edges, so the last row is never clipped.
            LazyVerticalGrid(
                columns = gridCells(windowSize),
                modifier = modifier.fillMaxSize(),
                // No top inset: the title above already ends with its own gap.
                contentPadding =
                    PaddingValues(
                        start = windowSize.screenPadding,
                        end = windowSize.screenPadding,
                        bottom = windowSize.screenPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
            ) {
                items(state.items, key = { it.contentId }) { item ->
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
