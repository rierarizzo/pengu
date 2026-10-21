package com.misgus.pengu.data.tmdb

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.data.appJson
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TmdbMapperTest {
    @Test
    fun picksTheBestTextlessPosterAndTheLocalizedLogo() {
        val title = tmdbTitleFromJson(detailJson().jsonObject, TmdbMediaType.MOVIE, "es-ES")

        assertEquals(603, title.tmdbId)
        assertEquals("The Matrix", title.title)
        // The Spanish logo wins over the English one, and both over the neutral.
        assertEquals("https://image.tmdb.org/t/p/w500/logo_es.png", title.logo)
        // The largest language-neutral poster is the one a phone draws.
        assertEquals("https://image.tmdb.org/t/p/w500/textless_huge.jpg", title.textlessPoster)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", title.poster)
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop.jpg", title.backdrop)
        assertEquals(136, title.runtimeMinutes)
        assertEquals("tt0133093", title.imdbId)
        assertEquals(listOf("Action"), title.genres)
        assertEquals(listOf("United States of America"), title.countries)
        assertEquals("Released", title.status)
        assertEquals("English", title.originalLanguage)
        assertEquals(63_000_000L, title.budget)
        assertEquals(467_678_144L, title.revenue)
        assertEquals("Keanu Reeves", title.cast.single().name)
        assertEquals(listOf("Lana Wachowski"), title.director)
        assertEquals(listOf("Lilly Wachowski"), title.writer)
        assertEquals(8.2, title.voteAverage)
    }

    @Test
    fun readsTheAggregatedCrewOfASeries() {
        val title =
            tmdbTitleFromJson(
                appJson
                    .parseToJsonElement(
                        """
                        {"id":1399,"name":"Show","aggregate_credits":{"crew":[
                          {"name":"Director A","jobs":[{"job":"Director","episode_count":5}]},
                          {"name":"Writer A","jobs":[{"job":"Writer"},{"job":"Creator"}]},
                          {"name":"Producer B","jobs":[{"job":"Producer"}]}
                        ]}}
                        """.trimIndent(),
                    ).jsonObject,
                TmdbMediaType.SERIES,
                "en-US",
            )

        assertEquals(listOf("Director A"), title.director)
        assertEquals(listOf("Writer A"), title.writer)
    }

    @Test
    fun fallsBackToTheEnglishLogoWhenTheLanguageHasNone() {
        val title = tmdbTitleFromJson(detailJson().jsonObject, TmdbMediaType.MOVIE, "fr-FR")

        assertEquals("https://image.tmdb.org/t/p/w500/logo_en.png", title.logo)
    }

    @Test
    fun mapsARecommendationToAPrefixedPreview() {
        val preview =
            tmdbPreviewFromJson(
                appJson
                    .parseToJsonElement(
                        """{"id":604,"title":"The Matrix Reloaded","poster_path":"/p.jpg","release_date":"2003-05-15"}""",
                    ).jsonObject,
                ContentTypes.MOVIE,
            )

        assertEquals("tmdb:604", preview?.id)
        assertEquals(ContentTypes.MOVIE, preview?.type)
        assertEquals("The Matrix Reloaded", preview?.name)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", preview?.poster)
        assertEquals("2003", preview?.releaseInfo)
    }

    @Test
    fun mapsSeasonEpisodesAndDropsTheUnnumberedOnes() {
        val episodes =
            tmdbEpisodesFromJson(
                appJson
                    .parseToJsonElement(
                        """
                        {"episodes":[
                          {"episode_number":1,"season_number":2,"name":"Pilot","overview":"Where it starts",
                           "runtime":52,"vote_average":8.4,"still_path":"/still.jpg","air_date":"2024-03-01"},
                          {"episode_number":2,"season_number":2,"overview":"","runtime":0,"vote_average":0.0},
                          {"season_number":2,"name":"Cannot be matched"}
                        ]}
                        """.trimIndent(),
                    ).jsonObject,
            )

        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
        val first = episodes.first()
        assertEquals(2, first.seasonNumber)
        assertEquals("Pilot", first.name)
        assertEquals("Where it starts", first.overview)
        assertEquals(52, first.runtimeMinutes)
        assertEquals(8.4, first.rating)
        assertEquals("https://image.tmdb.org/t/p/w780/still.jpg", first.still)
        assertEquals("2024-03-01", first.airDate)
        // An empty overview, a zero runtime and a zeroed score are "no value".
        assertNull(episodes[1].overview)
        assertNull(episodes[1].runtimeMinutes)
        assertNull(episodes[1].rating)
    }

    private fun detailJson() =
        appJson.parseToJsonElement(
            """
            {
              "id": 603,
              "title": "The Matrix",
              "poster_path": "/poster.jpg",
              "backdrop_path": "/backdrop.jpg",
              "release_date": "1999-03-31",
              "runtime": 136,
              "status": "Released",
              "original_language": "en",
              "spoken_languages": [{"iso_639_1": "en", "name": "English"}],
              "budget": 63000000,
              "revenue": 467678144,
              "genres": [{"id": 28, "name": "Action"}],
              "production_countries": [{"iso_3166_1": "US", "name": "United States of America"}],
              "external_ids": {"imdb_id": "tt0133093"},
              "vote_average": 8.2,
              "credits": {
                "cast": [{"name": "Keanu Reeves", "character": "Neo", "profile_path": "/neo.jpg"}],
                "crew": [
                  {"name": "Lana Wachowski", "job": "Director"},
                  {"name": "Lilly Wachowski", "job": "Screenplay"},
                  {"name": "Joel Silver", "job": "Producer"}
                ]
              },
              "images": {
                "posters": [
                  {"file_path": "/poster_es.jpg", "iso_639_1": "es", "vote_average": 3.0, "vote_count": 4, "width": 1000},
                  {"file_path": "/textless.jpg", "iso_639_1": null, "vote_average": 5.5, "vote_count": 9, "width": 1000, "height": 1500},
                  {"file_path": "/textless_low.jpg", "iso_639_1": null, "vote_average": 1.0, "vote_count": 1, "width": 500, "height": 750},
                  {"file_path": "/textless_huge.jpg", "iso_639_1": null, "vote_average": 0.5, "vote_count": 1, "width": 2000, "height": 3000}
                ],
                "logos": [
                  {"file_path": "/logo_en.png", "iso_639_1": "en", "vote_average": 5.0, "width": 500},
                  {"file_path": "/logo_es.png", "iso_639_1": "es", "vote_average": 4.0, "width": 400}
                ]
              }
            }
            """.trimIndent(),
        )
}
