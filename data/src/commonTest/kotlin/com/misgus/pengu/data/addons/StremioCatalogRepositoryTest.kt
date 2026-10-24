package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.repository.AddonRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StremioCatalogRepositoryTest {
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun keepsCatalogsWhateverTypeTheAddonDeclares() =
        runTest {
            val manifest =
                """
                {
                  "id": "test",
                  "name": "Test",
                  "version": "1.0.0",
                  "catalogs": [
                    {"type": "movie", "id": "top", "name": "Top"},
                    {"type": "anime", "id": "anime", "name": "Anime"},
                    {"type": "series", "id": "search", "name": "Search", "extra": [{"name": "search", "isRequired": true}]}
                  ]
                }
                """.trimIndent()
            val engine = MockEngine { respond(manifest, HttpStatusCode.OK, headers) }
            val addons =
                object : AddonRepository {
                    override suspend fun all(): List<Addon> = listOf(Addon(id = "a", url = "https://addon.test/manifest.json"))
                }
            val repository =
                StremioCatalogRepository(
                    addons = addons,
                    clients = AddonClientPool(HttpClient(engine)),
                )

            val refs = repository.catalogs()

            // Every declared catalog is kept, whatever type it names; the
            // browsable filter (a required extra) is applied by the callers.
            assertEquals(listOf("movie|top", "anime|anime", "series|search"), refs.map { "${it.type}|${it.id}" })
        }
}
