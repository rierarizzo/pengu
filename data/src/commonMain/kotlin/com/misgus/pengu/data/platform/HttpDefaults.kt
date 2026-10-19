package com.misgus.pengu.data.platform

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage

/**
 * Plugins every engine gets, on top of whatever the platform adds.
 *
 * A request that never answers must not leave the UI loading forever, so the
 * timeouts are finite. [cacheStorage] is the platform's on-disk store: with it
 * installed, a manifest or catalog page an addon declared as cacheable is read
 * from disk instead of the network, which is what makes a warm start paint
 * before the first round-trip finishes.
 *
 * `Cache-Control: no-cache` stays meaningful: the cache revalidates the entry
 * with the addon (`If-None-Match` / `If-Modified-Since`) instead of returning
 * it blind. Nothing here asks for compression; the addon client sets
 * `Accept-Encoding` per request and decodes the body itself.
 */
internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installHttpDefaults(cacheStorage: CacheStorage? = null) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MS
        connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MS
        socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MS
    }
    if (cacheStorage != null) {
        install(HttpCache) {
            publicStorage(cacheStorage)
            privateStorage(cacheStorage)
        }
    }
}
