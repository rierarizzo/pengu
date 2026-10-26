package com.misgus.pengu.ui.library

import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.library_filter_all
import com.misgus.pengu.app.shared.generated.resources.library_filter_movies
import com.misgus.pengu.app.shared.generated.resources.library_filter_series
import com.misgus.pengu.app.shared.generated.resources.library_sort_recent
import com.misgus.pengu.app.shared.generated.resources.library_sort_title
import com.misgus.pengu.app.shared.generated.resources.library_sort_year
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import org.jetbrains.compose.resources.StringResource

/** Which kind of saved title the grid shows. */
enum class LibraryFilter(
    val label: StringResource,
) {
    All(Res.string.library_filter_all),
    Movies(Res.string.library_filter_movies),
    Series(Res.string.library_filter_series),
}

/** How the grid is ordered. */
enum class LibrarySort(
    val label: StringResource,
) {
    Recent(Res.string.library_sort_recent),
    Title(Res.string.library_sort_title),
    Year(Res.string.library_sort_year),
}

/**
 * The library as the grid draws it: filtered, then ordered.
 *
 * Kind and "unwatched" are separate axes, so the second one stacks on top of any
 * kind instead of being a fourth kind that excludes the other three.
 *
 * Pure, so the screen keeps no copy of the list and the rules stay testable
 * without a ViewModel.
 */
fun visibleLibraryItems(
    items: List<LibraryItem>,
    filter: LibraryFilter,
    onlyUnwatched: Boolean,
    sort: LibrarySort,
    watchedIds: Set<String>,
): List<LibraryItem> {
    val byKind =
        when (filter) {
            LibraryFilter.All -> items
            LibraryFilter.Movies -> items.filter { it.contentType == ContentTypes.MOVIE }
            LibraryFilter.Series -> items.filter { ContentTypes.isSeries(it.contentType) }
        }
    val filtered = if (onlyUnwatched) byKind.filter { it.contentId !in watchedIds } else byKind
    return when (sort) {
        LibrarySort.Recent -> filtered.sortedByDescending { it.addedAt }
        LibrarySort.Title -> filtered.sortedBy { it.name.lowercase() }
        LibrarySort.Year -> filtered.sortedWith(compareByDescending<LibraryItem> { it.year ?: 0 }.thenBy { it.name.lowercase() })
    }
}
