package com.misgus.pengu.data.tmdb

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.data.appJson
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals("Keanu Reeves", title.cast.single().name)
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
              "genres": [{"id": 28, "name": "Action"}],
              "production_countries": [{"iso_3166_1": "US", "name": "United States of America"}],
              "external_ids": {"imdb_id": "tt0133093"},
              "credits": {"cast": [{"name": "Keanu Reeves", "character": "Neo", "profile_path": "/neo.jpg"}]},
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
