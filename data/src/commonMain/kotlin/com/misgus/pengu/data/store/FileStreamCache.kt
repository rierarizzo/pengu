package com.misgus.pengu.data.store

import com.misgus.pengu.core.repository.LastStream
import com.misgus.pengu.core.repository.StreamCacheRepository
import com.misgus.pengu.data.longOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.platform.ioDispatcher
import com.misgus.pengu.data.stringOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * [StreamCacheRepository] stored as a JSON file.
 *
 * Shape: `{ "<contentId>|<videoId>": { "u": "<url>", "h": {..}, "t": <millis> } }`.
 * A broken file degrades to "nothing remembered" instead of failing, which only
 * costs the picker being shown once more. A mutex serializes the in-memory map
 * and the file it is flushed to.
 */
class FileStreamCache(
    private val path: String = AppPaths.lastStreamsFile,
) : StreamCacheRepository {
    private var cache: JsonObject? = null
    private val mutex = Mutex()

    private suspend fun load(): JsonObject =
        cache ?: withContext(ioDispatcher) {
            JsonMapStore(path).read().also { cache = it }
        }

    private suspend fun flush() {
        val data = cache ?: return
        JsonMapStore(path).write(data)
    }

    override suspend fun save(stream: LastStream) =
        mutex.withLock {
            val data = load().toMutableMap()
            data[key(stream.contentId, stream.videoId)] =
                JsonObject(
                    buildMap {
                        put("u", JsonPrimitive(stream.url))
                        put("t", JsonPrimitive(stream.resolvedAt.toEpochMilliseconds()))
                        stream.episodeTitle?.let { put("e", JsonPrimitive(it)) }
                        if (stream.httpHeaders.isNotEmpty()) {
                            put("h", JsonObject(stream.httpHeaders.mapValues { JsonPrimitive(it.value) }))
                        }
                    },
                )
            cache = JsonObject(data)
            flush()
        }

    override suspend fun find(
        contentId: String,
        videoId: String?,
    ): LastStream? =
        mutex.withLock {
            val entry = load()[key(contentId, videoId)]?.objOrNull() ?: return@withLock null
            val url = entry["u"].stringOrNull() ?: return@withLock null
            val resolvedAt = entry["t"].longOrNull() ?: return@withLock null
            LastStream(
                contentId = contentId,
                videoId = videoId,
                url = url,
                httpHeaders =
                    entry["h"]
                        .objOrNull()
                        ?.mapNotNull { (name, value) -> value.stringOrNull()?.let { name to it } }
                        ?.toMap()
                        .orEmpty(),
                resolvedAt = Instant.fromEpochMilliseconds(resolvedAt),
                episodeTitle = entry["e"].stringOrNull()?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun remove(
        contentId: String,
        videoId: String?,
    ) = mutex.withLock {
        val data = load().toMutableMap()
        if (data.remove(key(contentId, videoId)) == null) return@withLock
        cache = JsonObject(data)
        flush()
    }

    /** One entry per title and episode; a movie's has an empty video id. */
    private fun key(
        contentId: String,
        videoId: String?,
    ): String = "$contentId|${videoId.orEmpty()}"
}
