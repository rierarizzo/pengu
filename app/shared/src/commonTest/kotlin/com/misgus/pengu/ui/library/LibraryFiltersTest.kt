package com.misgus.pengu.ui.library

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LibraryFiltersTest {
    private fun item(
        id: String,
        name: String,
        year: String?,
        series: Boolean = false,
        added: Long = 0,
    ) = LibraryItem(
        id = id,
        contentId = id,
        contentType = if (series) ContentTypes.SERIES else ContentTypes.MOVIE,
        name = name,
        releaseInfo = year,
        addedAt = Instant.fromEpochSeconds(added),
    )

    private val items =
        listOf(
            item("a", "Beta", "2020", series = true, added = 1),
            item("b", "alpha", "2018", added = 3),
            item("c", "Gamma", "2022", series = true, added = 2),
        )

    private fun visible(
        filter: LibraryFilter = LibraryFilter.All,
        onlyUnwatched: Boolean = false,
        sort: LibrarySort = LibrarySort.Recent,
        watched: Set<String> = emptySet(),
    ) = visibleLibraryItems(items, filter, onlyUnwatched, sort, watched).map { it.id }

    @Test
    fun filtersByKind() {
        assertEquals(listOf("c", "a"), visible(filter = LibraryFilter.Series))
        assertEquals(listOf("b"), visible(filter = LibraryFilter.Movies))
    }

    @Test
    fun unwatchedStacksOnEveryKind() {
        // Its own axis: it narrows whatever kind is picked instead of replacing it.
        assertEquals(listOf("c", "a"), visible(onlyUnwatched = true, watched = setOf("b")))
        assertEquals(listOf("a"), visible(filter = LibraryFilter.Series, onlyUnwatched = true, watched = setOf("c")))
        assertEquals(emptyList(), visible(filter = LibraryFilter.Movies, onlyUnwatched = true, watched = setOf("b")))
    }

    @Test
    fun sortsByAddedTitleAndYear() {
        assertEquals(listOf("b", "c", "a"), visible())
        // Case-insensitive, so "alpha" comes before "Beta".
        assertEquals(listOf("b", "a", "c"), visible(sort = LibrarySort.Title))
        assertEquals(listOf("c", "a", "b"), visible(sort = LibrarySort.Year))
    }
}
