package com.misgus.pengu.ui.discover

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.discover_context
import com.misgus.pengu.app.shared.generated.resources.discover_empty_message
import com.misgus.pengu.app.shared.generated.resources.discover_empty_title
import com.misgus.pengu.app.shared.generated.resources.discover_error_title
import com.misgus.pengu.app.shared.generated.resources.discover_filter_catalog
import com.misgus.pengu.app.shared.generated.resources.discover_filter_genre
import com.misgus.pengu.app.shared.generated.resources.discover_filter_type
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.isLandscape
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterCard
import com.misgus.pengu.ui.components.SkeletonPosterGrid
import com.misgus.pengu.ui.components.contentTypeLabel
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.gridCells
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/**
 * The discover feed: one catalog at a time, chosen with the pickers above the
 * grid, exactly like Nuvio.
 *
 * Only the catalogs the addons open for browsing are offered — a search-only
 * catalog is not here, and one that needs an extra only its addon knows is not
 * either — so the flags the addon declares decide what the feed can show. The
 * genre picker carries the options the selected catalog declares, and the grid
 * pages the selected catalog as it is scrolled.
 *
 * It is embedded in the Search section, so it owns no header and no trigger: the
 * caller loads it.
 */
@Composable
fun DiscoverFeed(
    state: DiscoverUiState,
    hideLabels: Boolean,
    onOpenDetail: (LibraryItem) -> Unit,
    onSelectType: (ContentType) -> Unit,
    onSelectCatalog: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSize = LocalWindowSize.current
    Column(modifier.fillMaxSize()) {
        // The pickers sit above the scroll like the search field does, so they keep
        // their place while the grid moves and the spacing below them is explicit
        // instead of the grid's own content padding.
        if (state.types.isNotEmpty()) {
            DiscoverFilters(
                state = state,
                onSelectType = onSelectType,
                onSelectCatalog = onSelectCatalog,
                onSelectGenre = onSelectGenre,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = windowSize.screenPadding,
                            end = windowSize.screenPadding,
                            bottom = AppSpacing.md,
                        ),
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                // Nothing painted yet, or the first page of a new selection on its way.
                state.items.isEmpty() && (state.isLoading || !state.hasLoaded) -> SkeletonPosterGrid(hideLabels)
                state.error != null && state.items.isEmpty() ->
                    Message(
                        icon = MaterialIcons.Error,
                        title = stringResource(Res.string.discover_error_title),
                        message = stringResource(state.error.toUserMessage()),
                    )
                // No browsable catalog at all (every enabled addon is search-only), or
                // the chosen catalog answered nothing.
                state.items.isEmpty() ->
                    Message(
                        icon = MaterialIcons.Compass,
                        title = stringResource(Res.string.discover_empty_title),
                        message = stringResource(Res.string.discover_empty_message),
                    )
                else ->
                    DiscoverGrid(
                        state = state,
                        hideLabels = hideLabels,
                        onOpenDetail = onOpenDetail,
                        onLoadMore = onLoadMore,
                    )
            }
        }
    }
}

@Composable
private fun DiscoverGrid(
    state: DiscoverUiState,
    hideLabels: Boolean,
    onOpenDetail: (LibraryItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val windowSize = LocalWindowSize.current
    val items = state.items
    // `rememberUpdatedState` keeps the count current inside the derived state;
    // capturing `items` directly would freeze it at the first composition and
    // make `shouldLoadMore` permanently true, loading every page at once.
    val itemCount by rememberUpdatedState(items.size)
    val canPage by rememberUpdatedState(state.hasMore)
    val shouldLoadMore by remember {
        derivedStateOf {
            val last =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= itemCount - 8
        }
    }
    LaunchedEffect(shouldLoadMore, itemCount, canPage) {
        if (shouldLoadMore && canPage) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = gridCells(windowSize),
        modifier = Modifier.fillMaxSize(),
        // No top inset: the pickers above already end with their own gap.
        contentPadding =
            PaddingValues(
                start = windowSize.screenPadding,
                end = windowSize.screenPadding,
                bottom = windowSize.screenPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
        horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
    ) {
        items(items, key = { "${it.type}:${it.id}" }) { item ->
            val libraryItem = LibraryItem.fromPreview(item)
            PosterCard(
                name = item.name,
                poster = item.poster,
                caption = item.releaseInfo,
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
        if (state.isLoading || state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "discover-loading") {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/**
 * Type, catalog and genre pickers, then the addon and type the grid comes from.
 *
 * The genre picker only appears when the selected catalog declares genres, and
 * "all genres" is offered only when the addon does not require one.
 */
@Composable
private fun DiscoverFilters(
    state: DiscoverUiState,
    onSelectType: (ContentType) -> Unit,
    onSelectCatalog: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allGenres = stringResource(Res.string.discover_filter_genre)
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            FilterDropdown(
                label = state.selectedType?.let { typeLabel(it) } ?: stringResource(Res.string.discover_filter_type),
                options = state.types.map { type -> FilterOption(type.value, typeLabel(type)) },
                selectedKey = state.selectedType?.value,
                enabled = true,
                onSelected = { key -> state.types.firstOrNull { it.value == key }?.let(onSelectType) },
            )
            FilterDropdown(
                label = state.selectedCatalog?.name ?: stringResource(Res.string.discover_filter_catalog),
                options =
                    state.catalogs.map { catalog ->
                        val addon = catalog.addonName.takeIf { it.isNotBlank() }
                        FilterOption(catalog.key, if (addon == null) catalog.name else "${catalog.name} · $addon")
                    },
                selectedKey = state.selectedCatalogKey,
                enabled = state.catalogs.isNotEmpty(),
                onSelected = onSelectCatalog,
            )
            if (state.genres.isNotEmpty()) {
                FilterDropdown(
                    label = state.selectedGenre ?: allGenres,
                    options =
                        buildList {
                            if (!state.genreRequired) add(FilterOption("", allGenres))
                            state.genres.forEach { genre -> add(FilterOption(genre, genre)) }
                        },
                    selectedKey = state.selectedGenre ?: "",
                    enabled = true,
                    onSelected = { key -> onSelectGenre(key.ifBlank { null }) },
                )
            }
        }
        state.selectedCatalog?.let { catalog ->
            Text(
                text = stringResource(Res.string.discover_context, catalog.addonName, typeLabel(catalog.type)),
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
        }
    }
}

/** "Movies" / "Series", or the raw Stremio type when the app has no name for it. */
@Composable
private fun typeLabel(type: ContentType): String = contentTypeLabel(type)

private data class FilterOption(
    val key: String,
    val label: String,
)

/** A chip that opens [options] in a menu. */
@Composable
private fun FilterDropdown(
    label: String,
    options: List<FilterOption>,
    selectedKey: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    MaterialIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(AppSpacing.md),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        if (option.key != selectedKey) onSelected(option.key)
                    },
                )
            }
        }
    }
}
