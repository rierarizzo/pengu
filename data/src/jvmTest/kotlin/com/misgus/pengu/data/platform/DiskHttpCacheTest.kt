package com.misgus.pengu.data.platform

import com.misgus.pengu.data.addons.StremioAddonClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The addon path against a real on-disk cache.
 *
 * This is the pair that has to keep working together: a response is stored
 * compressed and served back compressed, so the client's own gzip decoding must
 * run on both a network answer and a cache hit.
 */
class DiskHttpCacheTest {
    private fun tempDir(): File =
        File.createTempFile("pengu-cache", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    private fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(text.encodeToByteArray()) }
            output.toByteArray()
        }

    @Test
    fun servesARepeatedCatalogFromDiskWithoutAskingTheAddonAgain() =
        runTest {
            var requests = 0
            val manifest =
                """{"id":"test","name":"Test","version":"1.0.0","resources":["catalog"],""" +
                    """"types":["movie"],"catalogs":[{"type":"movie","id":"top","name":"Top"}]}"""
            val catalog = """{"metas":[{"id":"tt1","type":"movie","name":"Movie"}]}"""
            val engine =
                MockEngine { request ->
                    requests++
                    val body = if (request.url.encodedPath.endsWith("manifest.json")) manifest else catalog
                    respond(
                        content = gzip(body),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ContentEncoding to listOf("gzip"),
                                // A shared-cache lifetime the addon publishes itself.
                                HttpHeaders.CacheControl to listOf("public, max-age=600"),
                            ),
                    )
                }
            val client = HttpClient(engine) { installHttpDefaults(diskCacheStorage(tempDir().path)) }
            val addon = StremioAddonClient("https://addon.example", client)

            val loadedManifest = addon.fetchManifest()
            val first = addon.fetchCatalog("movie", "top")
            val second = addon.fetchCatalog("movie", "top")

            // A manifest and two catalog reads, two round-trips: the repeated
            // catalog came from the disk.
            assertEquals(2, requests)
            assertEquals("Test", loadedManifest.name)
            assertEquals(first, second)
            assertEquals("Movie", first.single().name)
        }

    @Test
    fun reusesAResponseThatDeclaresNoFreshnessAtAll() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content = gzip("""{"metas":[]}"""),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ContentEncoding to listOf("gzip"),
                            ),
                    )
                }
            val client = HttpClient(engine) { installHttpDefaults(diskCacheStorage(tempDir().path)) }
            val addon = StremioAddonClient("https://addon.example", client)

            addon.fetchCatalog("movie", "top")
            addon.fetchCatalog("movie", "top")

            assertEquals(1, requests)
        }

    @Test
    fun asksTheAddonAgainWhenTheResponseSaysNotToReuseIt() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content = gzip("""{"metas":[]}"""),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ContentEncoding to listOf("gzip"),
                                HttpHeaders.CacheControl to listOf("no-cache"),
                            ),
                    )
                }
            val client = HttpClient(engine) { installHttpDefaults(diskCacheStorage(tempDir().path)) }
            val addon = StremioAddonClient("https://addon.example", client)

            addon.fetchCatalog("movie", "top")
            addon.fetchCatalog("movie", "top")

            assertEquals(2, requests)
        }

    @Test
    fun decodesAGzippedBodyThatWasNeverCached() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = gzip("""{"metas":[{"id":"tt9","type":"movie","name":"Gzipped"}]}"""),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.ContentEncoding to listOf("gzip"),
                                HttpHeaders.CacheControl to listOf("no-store"),
                            ),
                    )
                }
            val client = HttpClient(engine) { installHttpDefaults(diskCacheStorage(tempDir().path)) }
            val addon = StremioAddonClient("https://addon.example", client)

            assertEquals("Gzipped", addon.fetchCatalog("movie", "top").single().name)
            assertTrue(File(tempDir().path).exists())
        }
}
