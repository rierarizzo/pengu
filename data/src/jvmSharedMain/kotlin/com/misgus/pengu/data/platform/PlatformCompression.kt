package com.misgus.pengu.data.platform

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import kotlinx.io.files.Path
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

// Platform pieces the JVM and Android targets share.
//
// Both run on a JVM filesystem and a JDK class library, so the on-disk HTTP
// cache and the gzip decoder live here once instead of in each `actual`.

/**
 * On-disk store for cached HTTP responses.
 *
 * [directory] defaults to the platform cache directory; tests point it at a
 * temporary one.
 */
internal fun diskCacheStorage(directory: String = cacheDirectory()): CacheStorage =
    CacheStorageWithFallbackTtl(
        FileStorage(Path(directory, HTTP_CACHE_DIR)),
    )

actual fun gzipDecode(bytes: ByteArray): ByteArray =
    GZIPInputStream(ByteArrayInputStream(bytes)).use { stream ->
        val output = ByteArrayOutputStream(bytes.size.coerceAtLeast(DEFAULT_BUFFER_SIZE))
        stream.copyTo(output)
        output.toByteArray()
    }
