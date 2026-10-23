package com.misgus.pengu.data.backend

import com.misgus.pengu.core.BackendException
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.ProgressRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * [ProgressRepository] backed by a Nuvio (Supabase) deployment.
 *
 * Scoped to the active profile, like the library repository.
 */
class NuvioProgressRepository(
    private val rest: NuvioRest,
    private val account: AccountRepository,
) : ProgressRepository {
    private val _changes = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = _changes.asStateFlow()

    override suspend fun all(): List<WatchProgress> {
        val rows =
            rest.select(
                "watch_progress",
                order = "last_watched.desc",
                filter = "profile_id=eq.${requireProfileId()}",
            )
        return rows.map(::watchProgressFromRow)
    }

    override suspend fun save(progress: WatchProgress) {
        val profileId = requireProfileId()
        val key = progress.progressKey
        if (key.isNullOrEmpty()) {
            // The backend dedupes on `progress_key`; without it the push would
            // create duplicate rows.
            throw BackendException("progress_key is required to save progress")
        }
        // The sync RPC is the canonical write path: it also derives the watched
        // markers from completed entries.
        rest.callRpc(
            "sync_push_watch_progress",
            JsonObject(
                mapOf(
                    "p_entries" to JsonArray(listOf(watchProgressToRow(progress, profileId))),
                    "p_profile_id" to JsonPrimitive(profileId),
                ),
            ),
        )
        _changes.value += 1
    }

    override suspend fun remove(contentId: String) {
        rest.delete(
            "watch_progress",
            filter = "profile_id=eq.${requireProfileId()}&content_id=eq.${postgrestLiteral(contentId)}",
        )
        _changes.value += 1
    }

    override suspend fun watchedEntries(candidateIds: Collection<String>): List<WatchedEntry> {
        val ids = candidateIds.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return emptyList()
        val profileId = requireProfileId()
        // Chunks are independent, so they go out together instead of one query
        // per 40 ids.
        return coroutineScope {
            ids
                .chunked(CHUNK_SIZE)
                .map { chunk ->
                    async {
                        val rows =
                            rest.select(
                                "watched_items",
                                filter =
                                    "profile_id=eq.$profileId&content_id=in.(${
                                        chunk.joinToString(",") { postgrestLiteral(it) }
                                    })",
                            )
                        rows.map(::watchedEntryFromRow)
                    }
                }.awaitAll()
                .flatten()
        }
    }

    override suspend fun markWatched(
        contentId: String,
        contentType: ContentType,
        season: Season?,
        episode: Episode?,
    ) {
        val profileId = requireProfileId()
        val item =
            buildMap<String, kotlinx.serialization.json.JsonElement> {
                put("content_id", JsonPrimitive(contentId))
                put("content_type", JsonPrimitive(contentType.value))
                put("watched_at", JsonPrimitive(Clock.System.now().toEpochMilliseconds()))
                season?.let { put("season", JsonPrimitive(it.number)) }
                episode?.let { put("episode", JsonPrimitive(it.number)) }
            }
        rest.callRpc(
            "sync_push_watched_items",
            JsonObject(
                mapOf(
                    "p_items" to JsonArray(listOf(JsonObject(item))),
                    "p_profile_id" to JsonPrimitive(profileId),
                ),
            ),
        )
        _changes.value += 1
    }

    override suspend fun unmarkWatched(
        contentId: String,
        season: Season?,
        episode: Episode?,
    ) {
        val profileId = requireProfileId()
        val key =
            buildMap<String, kotlinx.serialization.json.JsonElement> {
                put("content_id", JsonPrimitive(contentId))
                season?.let { put("season", JsonPrimitive(it.number)) }
                episode?.let { put("episode", JsonPrimitive(it.number)) }
            }
        rest.callRpc(
            "sync_delete_watched_items",
            JsonObject(
                mapOf(
                    "p_keys" to JsonArray(listOf(JsonObject(key))),
                    "p_profile_id" to JsonPrimitive(profileId),
                ),
            ),
        )
        _changes.value += 1
    }

    private fun requireProfileId(): Int = account.requireProfileId()

    private companion object {
        const val CHUNK_SIZE = 40
    }
}
