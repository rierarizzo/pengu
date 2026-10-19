package com.misgus.pengu.data.platform

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import io.ktor.util.date.plus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class CacheStorageWithFallbackTtlTest {
    private val ttl = 5.minutes
    private val responseTime = GMTDate(1_000_000L)

    @Test
    fun givesAFallbackLifetimeToAResponseThatDeclaresNothing() =
        runTest {
            val delegate = RecordingStorage()
            val storage = CacheStorageWithFallbackTtl(delegate, ttl)

            storage.store(url(), response(headers = Headers.Empty))

            val stored = assertNotNull(delegate.stored)
            assertEquals(responseTime.timestamp + ttl.inWholeMilliseconds, stored.expires.timestamp)
        }

    @Test
    fun keepsTheLifetimeTheAddonDeclared() =
        runTest {
            val delegate = RecordingStorage()
            val storage = CacheStorageWithFallbackTtl(delegate, ttl)

            val declared = responseTime + 60_000L
            storage.store(
                url(),
                response(
                    headers =
                        Headers.build {
                            append(HttpHeaders.CacheControl, "public, max-age=60")
                        },
                    expires = declared,
                ),
            )

            assertEquals(declared.timestamp, assertNotNull(delegate.stored).expires.timestamp)
        }

    @Test
    fun neverOverridesARevalidationInstruction() =
        runTest {
            val declarations =
                listOf(
                    // `no-cache` means "store it, but ask me before reusing it".
                    Headers.build { append(HttpHeaders.CacheControl, "no-cache") },
                    Headers.build { append(HttpHeaders.CacheControl, "public, must-revalidate") },
                    // A validator makes the next check a cheap `304`.
                    Headers.build { append(HttpHeaders.ETag, "\"v1\"") },
                    Headers.build { append(HttpHeaders.LastModified, "Mon, 01 Jan 2024 00:00:00 GMT") },
                    Headers.build { append(HttpHeaders.Expires, "Mon, 01 Jan 2024 00:00:00 GMT") },
                )
            for (declared in declarations) {
                val delegate = RecordingStorage()
                val storage = CacheStorageWithFallbackTtl(delegate, ttl)

                storage.store(url(), response(headers = declared))

                assertEquals(
                    responseTime.timestamp,
                    assertNotNull(delegate.stored).expires.timestamp,
                    "expected $declared to stay immediately stale",
                )
            }
        }

    @Test
    fun delegatesEveryOtherOperation() =
        runTest {
            val delegate = RecordingStorage()
            val storage = CacheStorageWithFallbackTtl(delegate, ttl)

            storage.find(url(), emptyMap())
            storage.findAll(url())
            storage.remove(url(), emptyMap())
            storage.removeAll(url())
            storage.clear()

            assertTrue(delegate.findCalled)
            assertTrue(delegate.findAllCalled)
            assertTrue(delegate.removeCalled)
            assertTrue(delegate.removeAllCalled)
            assertTrue(delegate.clearCalled)
        }

    private fun url() = Url("https://addon.example/catalog/movie/top.json")

    private fun response(
        headers: Headers,
        expires: GMTDate = responseTime,
    ) = CachedResponseData(
        url = url(),
        statusCode = HttpStatusCode.OK,
        requestTime = responseTime,
        responseTime = responseTime,
        version = HttpProtocolVersion.HTTP_1_1,
        expires = expires,
        headers = headers,
        varyKeys = emptyMap(),
        body = "{}".encodeToByteArray(),
    )

    private class RecordingStorage : CacheStorage {
        var stored: CachedResponseData? = null
        var findCalled = false
        var findAllCalled = false
        var removeCalled = false
        var removeAllCalled = false
        var clearCalled = false

        override suspend fun store(
            url: Url,
            data: CachedResponseData,
        ) {
            stored = data
        }

        override suspend fun find(
            url: Url,
            varyKeys: Map<String, String>,
        ): CachedResponseData? {
            findCalled = true
            return null
        }

        override suspend fun findAll(url: Url): Set<CachedResponseData> {
            findAllCalled = true
            return emptySet()
        }

        override suspend fun remove(
            url: Url,
            varyKeys: Map<String, String>,
        ) {
            removeCalled = true
        }

        override suspend fun removeAll(url: Url) {
            removeAllCalled = true
        }

        override suspend fun clear() {
            clearCalled = true
        }
    }
}
