package com.misgus.pengu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class MetadataEnrichmentTest {
    private val tmdb =
        TmdbTitle(
            tmdbId = 603,
            mediaType = TmdbMediaType.MOVIE,
            title = "The Matrix",
            poster = "https://img/poster.jpg",
            textlessPoster = "https://img/textless.jpg",
            backdrop = "https://img/backdrop.jpg",
            logo = "https://img/logo.png",
            overview = "A hacker learns the truth.",
            releaseDate = "1999-03-31",
            runtimeMinutes = 136,
            genres = listOf("Action"),
            countries = listOf("United States of America"),
            cast = listOf(MetaPerson("Keanu Reeves", "Neo", "https://img/neo.jpg")),
            imdbId = "tt0133093",
        )

    @Test
    fun aPreviewKeepsTheAddonArtworkAndFillsTheRest() {
        val preview =
            MetaPreview(
                id = "tt0133093",
                type = ContentTypes.MOVIE,
                name = "The Matrix",
                poster = "https://addon/poster.jpg",
            )

        val enriched = preview.enrichedWith(tmdb)

        assertEquals("tt0133093", enriched.id)
        assertEquals("https://addon/poster.jpg", enriched.poster)
        assertEquals("https://img/backdrop.jpg", enriched.background)
        assertEquals("https://img/textless.jpg", enriched.textlessPoster)
        assertEquals("https://img/logo.png", enriched.logo)
        assertEquals("1999", enriched.releaseInfo)
    }

    @Test
    fun aDetailTakesTmdbArtworkAndCopyAndKeepsTheAddonIds() {
        val video = MetaVideo(id = "tt0133093:1:1", title = "Pilot", season = Season(1), episode = Episode(1))
        val detail =
            MetaDetail(
                id = "tt0133093",
                type = ContentTypes.MOVIE,
                name = "The Matrix",
                poster = "https://addon/poster.jpg",
                description = "From the addon",
                imdbRating = 8.7,
                videos = listOf(video),
            )

        val merged = detail.withTmdbPriority(tmdb)

        assertNotNull(merged)
        // The addon keeps what addresses playback, and the score TMDB does not carry.
        assertEquals("tt0133093", merged.id)
        assertEquals(listOf(video), merged.videos)
        assertEquals(8.7, merged.imdbRating)
        // Everything TMDB knows wins, even when the addon had a value.
        assertEquals("https://img/poster.jpg", merged.poster)
        assertEquals("https://img/backdrop.jpg", merged.background)
        assertEquals("https://img/textless.jpg", merged.textlessPoster)
        assertEquals("https://img/logo.png", merged.logo)
        assertEquals("A hacker learns the truth.", merged.description)
        assertEquals("1999", merged.releaseInfo)
        assertEquals(136, merged.runtimeMinutes)
        assertEquals(listOf(MetaPerson("Keanu Reeves", "Neo", "https://img/neo.jpg")), merged.cast)
    }

    @Test
    fun aDetailKeepsTheAddonFieldTmdbHasNothingFor() {
        val detail =
            MetaDetail(
                id = "tt0133093",
                type = ContentTypes.MOVIE,
                name = "The Matrix",
                poster = "https://addon/poster.jpg",
                logo = "https://addon/logo.png",
                description = "From the addon",
                genres = listOf("Sci-Fi"),
            )
        val silent =
            tmdb.copy(
                poster = null,
                textlessPoster = null,
                backdrop = null,
                logo = null,
                overview = null,
                genres = emptyList(),
                cast = emptyList(),
            )

        val merged = detail.withTmdbPriority(silent)

        assertNotNull(merged)
        assertEquals("https://addon/poster.jpg", merged.poster)
        assertEquals("https://addon/logo.png", merged.logo)
        assertEquals("From the addon", merged.description)
        assertEquals(listOf("Sci-Fi"), merged.genres)
    }

    @Test
    fun aNullDetailBecomesAPageFromTmdbAlone() {
        val merged = (null as MetaDetail?).withTmdbPriority(tmdb)

        assertNotNull(merged)
        assertEquals("tmdb:603", merged.id)
        assertEquals(ContentTypes.MOVIE, merged.type)
        assertEquals("The Matrix", merged.name)
        assertEquals("1999", merged.releaseInfo)
        assertEquals("1999-03-31", merged.released)
        assertEquals("United States of America", merged.country)
    }

    @Test
    fun aNullTmdbLeavesEverythingUntouched() {
        val preview = MetaPreview(id = "tt1", type = ContentTypes.MOVIE, name = "Movie")

        assertSame(preview, preview.enrichedWith(null))
        assertNull((null as MetaDetail?).withTmdbPriority(null))
    }

    @Test
    fun aTmdbTitleBecomesAPrefixedPreview() {
        val preview = tmdb.toMetaPreview()

        assertEquals("tmdb:603", preview.id)
        assertEquals(ContentTypes.MOVIE, preview.type)
        assertEquals("The Matrix", preview.name)
        assertEquals("https://img/poster.jpg", preview.poster)
    }

    @Test
    fun aSeriesTmdbTitleMapsToTheSeriesType() {
        val series = tmdb.copy(mediaType = TmdbMediaType.SERIES)

        assertEquals(ContentTypes.SERIES, series.toMetaPreview().type)
    }
}
