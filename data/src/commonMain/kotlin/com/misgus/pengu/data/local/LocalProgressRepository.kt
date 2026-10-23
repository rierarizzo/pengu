package com.misgus.pengu.data.local

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.ProgressThresholds
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.data.backend.watchProgressFromRow
import com.misgus.pengu.data.backend.watchProgressToRow
import com.misgus.pengu.data.intOrNull
import com.misgus.pengu.data.store.JsonFileStore
import com.misgus.pengu.data.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * [ProgressRepository] backed by local JSON files.
 *
 * Progress is deduped by `progress_key` (falling back to `content_id` +
 * `video_id`). Watched markers live in a second file; when it is missing,
 * completed progress is still reported as watched. A mutex serializes the
 * read-modify-write cycles.
 */
class LocalProgressRepository(
    private val store: JsonFileStore,
    private val watched: JsonFileStore? = null,
) : ProgressRepository {
    private val _changes = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = _changes.asStateFlow()

    private val mutex = Mutex()

    override suspend fun all(): List<WatchProgress> =
        mutex.withLock {
            store
                .read()
                .map(::watchProgressFromRow)
                .sortedWith(compareByDescending { it.lastWatched })
        }

    override suspend fun watchedEntries(candidateIds: Collection<String>): List<WatchedEntry> =
        mutex.withLock {
            val candidates = candidateIds.toSet()
            if (candidates.isEmpty()) return@withLock emptyList()
            val result = mutableListOf<WatchedEntry>()

            watched?.read()?.forEach { row ->
                val contentId = row["content_id"].stringOrNull() ?: return@forEach
                if (contentId !in candidates) return@forEach
                result +=
                    WatchedEntry(
                        contentId = contentId,
                        contentType = ContentType(row["content_type"].stringOrNull() ?: ""),
                        season = row["season"].intOrNull()?.let(::Season),
                        episode = row["episode"].intOrNull()?.let(::Episode),
                    )
            }

            store.read().map(::watchProgressFromRow).forEach { entry ->
                if (entry.contentId !in candidates) return@forEach
                if ((entry.fraction ?: 0.0) < ProgressThresholds.COMPLETION) return@forEach
                result +=
                    WatchedEntry(
                        contentId = entry.contentId,
                        contentType = entry.contentType,
                        season = entry.season,
                        episode = entry.episode,
                    )
            }
            result
        }

    override suspend fun save(progress: WatchProgress) =
        mutex.withLock {
            val rows = store.read()
            val key = keyOfProgress(progress)
            val row = watchProgressToRow(progress)
            val withId = JsonObject(row + ("id" to JsonPrimitive(progress.id.ifEmpty { key })))
            store.write(rows.filterNot { keyOfRow(it) == key } + withId)
            _changes.value += 1
        }

    override suspend fun remove(contentId: String) =
        mutex.withLock {
            val rows = store.read()
            store.write(rows.filterNot { it["content_id"].stringOrNull() == contentId })
            _changes.value += 1
        }

    override suspend fun markWatched(
        contentId: String,
        contentType: ContentType,
        season: Season?,
        episode: Episode?,
    ) = mutex.withLock {
        val target = watched ?: return@withLock
        val key = markerKey(contentId, season, episode)
        val rows = target.read()
        val newRow =
            buildMap<String, kotlinx.serialization.json.JsonElement> {
                put("content_id", JsonPrimitive(contentId))
                put("content_type", JsonPrimitive(contentType.value))
                put("watched_at", JsonPrimitive(Clock.System.now().toEpochMilliseconds()))
                season?.let { put("season", JsonPrimitive(it.number)) }
                episode?.let { put("episode", JsonPrimitive(it.number)) }
            }
        target.write(rows.filterNot { markerKeyOfRow(it) == key } + JsonObject(newRow))
        _changes.value += 1
    }

    override suspend fun unmarkWatched(
        contentId: String,
        season: Season?,
        episode: Episode?,
    ) = mutex.withLock {
        val target = watched ?: return@withLock
        val key = markerKey(contentId, season, episode)
        target.write(target.read().filterNot { markerKeyOfRow(it) == key })
        _changes.value += 1
    }

    private fun keyOfProgress(progress: WatchProgress): String {
        val explicit = progress.progressKey
        if (!explicit.isNullOrEmpty()) return explicit
        return "${progress.contentId}|${progress.videoId ?: ""}"
    }

    private fun keyOfRow(row: JsonObject): String {
        val explicit = row["progress_key"].stringOrNull()
        if (!explicit.isNullOrEmpty()) return explicit
        return "${row["content_id"].stringOrNull()}|${row["video_id"].stringOrNull() ?: ""}"
    }

    private fun markerKey(
        contentId: String,
        season: Season?,
        episode: Episode?,
    ) = "$contentId|${season?.number ?: -1}|${episode?.number ?: -1}"

    private fun markerKeyOfRow(row: JsonObject) =
        markerKey(
            row["content_id"].stringOrNull() ?: "",
            row["season"].intOrNull()?.let(::Season),
            row["episode"].intOrNull()?.let(::Episode),
        )
}
