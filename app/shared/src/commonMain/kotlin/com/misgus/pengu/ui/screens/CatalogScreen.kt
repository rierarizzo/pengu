package com.misgus.pengu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_back
import com.misgus.pengu.app.shared.generated.resources.catalog_empty_message
import com.misgus.pengu.app.shared.generated.resources.catalog_error_title
import com.misgus.pengu.app.shared.generated.resources.empty_nothing_to_show
import com.misgus.pengu.app.shared.generated.resources.error_try_again_later
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.ui.catalog.CatalogUiState
import com.misgus.pengu.ui.catalog.CatalogViewModel
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterCard
import com.misgus.pengu.ui.components.SkeletonPosterGrid
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.gridCells
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.safeBottomInset
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/**
 * Full catalog grid, opened from a Home row's "See all".
 *
 * Scrolls to the bottom to load more pages (`skip`).
 */
@Composable
fun CatalogScreen(
    ref: CatalogRef,
    onBack: () -> Unit,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    val services = LocalAppServices.current
    val viewModel: CatalogViewModel =
        viewModel(key = "catalog-${ref.key}") {
            CatalogViewModel(services.catalogs, ref)
        }
    LaunchedEffect(ref) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsRepository = services.settings
    val settings by settingsRepository.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val windowSize = LocalWindowSize.current

    // Keep the count current inside the derived state, or it freezes at the
    // first composition and every page loads at once.
    val itemCount by rememberUpdatedState(state.items.size)
    val shouldLoadMore by remember {
        derivedStateOf {
            val last =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= itemCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore, state.items.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.padding(start = windowSize.screenPadding, top = windowSize.screenPadding, end = windowSize.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                icon = MaterialIcons.ArrowBack,
                contentDescription = stringResource(Res.string.action_back),
                onClick = onBack,
            )
            Spacer(Modifier.size(AppSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    ref.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppType.screenTitle(),
                    modifier = Modifier.sectionHeading(),
                )
                Text(ref.addonName, style = AppType.bodySecondary())
            }
        }
        Spacer(Modifier.height(AppSpacing.md))
        Box(Modifier.weight(1f)) {
            CatalogContent(state, settings.hideLabels, gridState, onOpenDetail)
        }
    }
}

@Composable
private fun CatalogContent(
    state: CatalogUiState,
    hideLabels: Boolean,
    gridState: LazyGridState,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    val windowSize = LocalWindowSize.current
    when {
        // Not read yet counts as loading: the empty message must not flash for a
        // frame before the first request starts.
        !state.hasLoaded && state.error == null -> SkeletonPosterGrid(hideLabels)
        state.error != null && !state.hasLoaded ->
            Message(
                icon = MaterialIcons.Error,
                title = stringResource(Res.string.catalog_error_title),
                message = stringResource(Res.string.error_try_again_later),
            )
        state.items.isEmpty() ->
            Message(
                icon = MaterialIcons.Inbox,
                title = stringResource(Res.string.empty_nothing_to_show),
                message = stringResource(Res.string.catalog_empty_message),
            )
        else ->
            LazyVerticalGrid(
                state = gridState,
                columns = gridCells(windowSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = windowSize.screenPadding,
                        end = windowSize.screenPadding,
                        // Room for the hover scale: the grid clips to its
                        // viewport, so without a top inset the first row loses
                        // its border.
                        top = AppSpacing.hoverSlack,
                        bottom = windowSize.screenPadding + safeBottomInset,
                    ),
                verticalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
            ) {
                items(state.items, key = { it.id }) { item ->
                    val libraryItem = LibraryItem.fromPreview(item)
                    PosterCard(
                        name = item.name,
                        poster = item.poster,
                        caption = item.releaseInfo,
                        hideLabels = hideLabels,
                        onTap = { onOpenDetail(libraryItem) },
                        actions =
                            MediaActions(
                                item = libraryItem,
                                onMoreInfo = { onOpenDetail(libraryItem) },
                            ),
                    )
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
    }
}
