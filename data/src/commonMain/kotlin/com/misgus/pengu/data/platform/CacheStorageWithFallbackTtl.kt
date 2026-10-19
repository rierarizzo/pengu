package com.misgus.pengu.data.platform

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.util.date.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Cache storage that gives a fallback lifetime to responses that declare none.
 *
 * A response with no `Cache-Control`, no `Expires` and no validator cannot be
 * revalidated, so a cache would have to re-fetch it in full — stalling on the
 * network the very thing the disk cache exists to avoid. A large part of the
 * Stremio ecosystem answers exactly like that, so those responses get
 * [fallbackTtl] of reuse instead.
 *
 * The fallback is deliberately narrow: anything the addon said about freshness
 * wins. `no-cache` and `must-revalidate` mean "ask me again", a `max-age` is a
 * real lifetime, and an `ETag` or a `Last-Modified` makes the next check a cheap
 * `304`, so none of them are overridden. [fallbackTtl] matches the window Home
 * uses to decide a re-entry is not worth reloading, so the cache never outlives
 * the app's own idea of "fresh enough".
 */
internal class CacheStorageWithFallbackTtl(
    private val delegate: CacheStorage,
    private val fallbackTtl: Duration = FALLBACK_CACHE_TTL,
) : CacheStorage {
    override suspend fun store(
        url: Url,
        data: CachedResponseData,
    ) = delegate.store(url, data.withFallbackExpiry())

    override suspend fun find(
        url: Url,
        varyKeys: Map<String, String>,
    ): CachedResponseData? = delegate.find(url, varyKeys)

    override suspend fun findAll(url: Url): Set<CachedResponseData> = delegate.findAll(url)

    override suspend fun remove(
        url: Url,
        varyKeys: Map<String, String>,
    ) = delegate.remove(url, varyKeys)

    override suspend fun removeAll(url: Url) = delegate.removeAll(url)

    override suspend fun clear() = delegate.clear()

    /** Whether this response carries no freshness information a cache could use. */
    private fun CachedResponseData.declaresNothing(): Boolean {
        val cacheControl = headers[HttpHeaders.CacheControl]?.lowercase().orEmpty()
        if ("no-cache" in cacheControl || "must-revalidate" in cacheControl) return false
        // `s-maxage` has no hyphen, so it needs its own check.
        if ("max-age" in cacheControl || "s-maxage" in cacheControl) return false
        if (headers[HttpHeaders.Expires] != null) return false
        return headers[HttpHeaders.ETag] == null && headers[HttpHeaders.LastModified] == null
    }

    private fun CachedResponseData.withFallbackExpiry(): CachedResponseData {
        if (!declaresNothing()) return this
        return CachedResponseData(
            url = url,
            statusCode = statusCode,
            requestTime = requestTime,
            responseTime = responseTime,
            version = version,
            expires = responseTime + fallbackTtl.inWholeMilliseconds,
            headers = headers,
            varyKeys = varyKeys,
            body = body,
        )
    }
}

/** How long a response that declares no freshness at all is reused. */
internal val FALLBACK_CACHE_TTL: Duration = 5.minutes
