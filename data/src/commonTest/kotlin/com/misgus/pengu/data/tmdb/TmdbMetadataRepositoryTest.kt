package com.misgus.pengu.data.tmdb

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.core.repository.TmdbLookup
import com.misgus.pengu.core.repository.TmdbVerifyResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbMetadataRepositoryTest {
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun resolvesAnImdbIdThenFetchesTheDetail() =
        runTest {
            val paths = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    paths += request.url.encodedPath
                    when {
                        request.url.encodedPath.endsWith("/find/tt0133093") ->
                            respond("""{"movie_results":[{"id":603}],"tv_results":[]}""", HttpStatusCode.OK, headers)
                        request.url.encodedPath.endsWith("/movie/603") ->
                            respond(detailJson(), HttpStatusCode.OK, headers)
                        else -> respond("{}", HttpStatusCode.OK, headers)
                    }
                }
            val repository = repository(engine, apiKey = "key")

            val title = repository.lookup(TmdbLookup(ContentTypes.MOVIE, "tt0133093", "The Matrix", 1999))

            assertEquals(603, title?.tmdbId)
            assertTrue(paths.any { it.endsWith("/find/tt0133093") })
            assertTrue(paths.any { it.endsWith("/movie/603") })
        }

    @Test
    fun makesNoRequestWithoutAKey() =
        runTest {
            var calls = 0
            val engine =
                MockEngine {
                    calls += 1
                    respond("{}", HttpStatusCode.OK, headers)
                }
            val repository = repository(engine, apiKey = "")

            assertNull(repository.lookup(TmdbLookup(ContentTypes.MOVIE, "tt0133093", "The Matrix", 1999)))
            assertEquals(0, calls)
        }

    @Test
    fun mapsRecommendationsToPreviews() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath.endsWith("/find/tt0133093") ->
                            respond("""{"movie_results":[{"id":603}],"tv_results":[]}""", HttpStatusCode.OK, headers)
                        request.url.encodedPath.endsWith("/movie/603/recommendations") ->
                            respond(
                                """{"results":[{"id":604,"title":"Reloaded","poster_path":"/p.jpg","release_date":"2003-05-15"}]}""",
                                HttpStatusCode.OK,
                                headers,
                            )
                        else -> respond("{}", HttpStatusCode.OK, headers)
                    }
                }
            val repository = repository(engine, apiKey = "key")

            val items = repository.recommendations(TmdbLookup(ContentTypes.MOVIE, "tt0133093", "The Matrix", 1999))

            assertEquals(1, items.size)
            assertEquals("tmdb:604", items.single().id)
            assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", items.single().poster)
        }

    @Test
    fun reportsAnInvalidKey() =
        runTest {
            val engine =
                MockEngine {
                    respond("{}", HttpStatusCode.Unauthorized, headers)
                }

            assertEquals(TmdbVerifyResult.INVALID_KEY, repository(engine, apiKey = "").verify("bad"))
        }

    @Test
    fun acceptsAWorkingKey() =
        runTest {
            val engine =
                MockEngine {
                    respond("{}", HttpStatusCode.OK, headers)
                }

            assertEquals(TmdbVerifyResult.VALID, repository(engine, apiKey = "").verify("good"))
        }

    private fun repository(
        engine: MockEngine,
        apiKey: String,
    ) = TmdbMetadataRepository(HttpClient(engine) { expectSuccess = false }, InMemorySettingsRepository(AppSettings(tmdbApiKey = apiKey)))

    private fun detailJson(): String =
        """
        {"id":603,"title":"The Matrix","poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg",
         "release_date":"1999-03-31","runtime":136,"external_ids":{"imdb_id":"tt0133093"}}
        """.trimIndent()
}
