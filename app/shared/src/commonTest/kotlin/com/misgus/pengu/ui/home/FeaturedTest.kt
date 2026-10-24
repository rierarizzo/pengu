package com.misgus.pengu.ui.home

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeaturedTest {
    private fun ref(id: String) =
        CatalogRef(
            addonName = "Addon",
            addonBaseUrl = "https://addon",
            type = ContentType("movie"),
            id = id,
            name = id,
        )

    private fun movie(id: String) = MetaPreview(id = id, type = ContentTypes.MOVIE, name = id)

    private fun series(id: String) = MetaPreview(id = id, type = ContentTypes.SERIES, name = id)

    private fun row(
        id: String,
        items: List<MetaPreview>,
    ) = CatalogRow(ref(id), id, items)

    @Test
    fun `takes from every row and mixes movies and series`() {
        val rows =
            listOf(
                row("movies", (1..4).map { movie("m$it") }),
                row("series", (1..4).map { series("s$it") }),
            )

        val featured = pickFeatured(rows, count = 4, random = Random(1))

        assertEquals(4, featured.size)
        assertTrue(featured.any { it.type == ContentTypes.MOVIE })
        assertTrue(featured.any { ContentTypes.isSeries(it.type) })
        assertTrue(featured.any { it.id.startsWith("m") })
        assertTrue(featured.any { it.id.startsWith("s") })
    }

    @Test
    fun `prefers items that are not on screen for a rotation`() {
        val rows = listOf(row("all", (1..10).map { movie("m$it") }))
        val current =
            rows
                .flatMap { it.items }
                .take(6)
                .map { it.type to it.id }
                .toSet()

        val featured = pickFeatured(rows, count = 6, random = Random(1), exclude = current)

        assertEquals(6, featured.size)
        // Only four items are off screen, so they are all picked and the rest is
        // filler; the rotation still shows something new.
        assertTrue(featured.map { it.id }.containsAll((7..10).map { "m$it" }))
    }

    @Test
    fun `drops the current set when the pool can fill the whole rotation`() {
        val rows = listOf(row("all", (1..12).map { movie("m$it") }))
        val current =
            rows
                .flatMap { it.items }
                .take(6)
                .map { it.type to it.id }
                .toSet()

        val featured = pickFeatured(rows, count = 6, random = Random(1), exclude = current)

        assertEquals(6, featured.size)
        assertTrue(featured.none { it.type to it.id in current })
    }

    @Test
    fun `returns the whole pool when it is smaller than the requested count`() {
        val rows = listOf(row("few", listOf(movie("m1"), series("s1"))))

        val featured = pickFeatured(rows, count = 6, random = Random(1))

        assertEquals(2, featured.size)
    }

    @Test
    fun `is stable for a given seed`() {
        val rows =
            listOf(
                row("movies", (1..8).map { movie("m$it") }),
                row("series", (1..8).map { series("s$it") }),
            )

        val first = pickFeatured(rows, count = 6, random = Random(42)).map { it.id }
        val second = pickFeatured(rows, count = 6, random = Random(42)).map { it.id }

        assertEquals(first, second)
    }

    @Test
    fun `keeps well rated titles ahead of low rated ones`() {
        val rows =
            listOf(
                row(
                    "all",
                    listOf(
                        movie("low").copy(imdbRating = 4.0),
                        movie("good").copy(imdbRating = 8.0),
                        movie("ok").copy(imdbRating = 6.5),
                    ),
                ),
            )

        val featured = pickFeatured(rows, count = 2, random = Random(1))

        assertEquals(setOf("good", "ok"), featured.map { it.id }.toSet())
    }

    @Test
    fun `fills with low rated titles only when good ones are not enough`() {
        val rows =
            listOf(
                row(
                    "all",
                    listOf(
                        movie("low").copy(imdbRating = 4.0),
                        movie("good").copy(imdbRating = 8.0),
                    ),
                ),
            )

        val featured = pickFeatured(rows, count = 2, random = Random(1))

        assertEquals(2, featured.size)
        assertTrue(featured.any { it.id == "good" })
        assertTrue(featured.any { it.id == "low" })
    }
}
