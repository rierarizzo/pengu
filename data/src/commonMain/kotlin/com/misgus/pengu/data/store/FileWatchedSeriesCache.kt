package com.misgus.pengu.data.store

import com.misgus.pengu.core.watched.CachedSeriesState
import com.misgus.pengu.core.watched.WatchedSeriesCache
import com.misgus.pengu.data.boolOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.platform.ioDispatcher
import com.misgus.pengu.data.stringOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [WatchedSeriesCache] stored as a JSON file.
 *
 * Shape: `{ "<profileKey>": { "<seriesId>": { "s": "<signature>", "w": true } } }`.
 * Read and writes are best effort on the read side: a broken file degrades to
 * "no cache" (so the series are recomputed) instead of failing. A mutex
 * serializes the in-memory map and the file it is flushed to.
 */
class FileWatchedSeriesCache(
    private val path: String = AppPaths.watchedSeriesCacheFile,
) : WatchedSeriesCache {
    private var cache: JsonObject? = null
    private val mutex = Mutex()

    private suspend fun load(): JsonObject =
        cache ?: withContext(ioDispatcher) {
            JsonMapStore(path).read().also { cache = it }
        }

    private suspend fun save() {
        val data = cache ?: return
        JsonMapStore(path).write(data)
    }

    override suspend fun read(
        profileKey: String,
        seriesId: String,
    ): CachedSeriesState? =
        mutex.withLock {
            val profile = load()[profileKey]?.objOrNull() ?: return@withLock null
            val entry = profile[seriesId]?.objOrNull() ?: return@withLock null
            val signature = entry["s"].stringOrNull() ?: return@withLock null
            val watched = entry["w"].boolOrNull() ?: return@withLock null
            CachedSeriesState(signature, watched)
        }

    override suspend fun writeAll(
        profileKey: String,
        states: Map<String, CachedSeriesState>,
    ) = mutex.withLock {
        if (states.isEmpty()) return@withLock
        val data = load().toMutableMap()
        val profile = data[profileKey]?.objOrNull()?.toMutableMap() ?: mutableMapOf()
        for ((seriesId, state) in states) {
            profile[seriesId] =
                JsonObject(
                    mapOf(
                        "s" to JsonPrimitive(state.signature),
                        "w" to JsonPrimitive(state.watched),
                    ),
                )
        }
        data[profileKey] = JsonObject(profile)
        cache = JsonObject(data)
        save()
    }

    override suspend fun clear(profileKey: String) =
        mutex.withLock {
            val data = load().toMutableMap()
            data.remove(profileKey)
            cache = JsonObject(data)
            save()
        }
}
