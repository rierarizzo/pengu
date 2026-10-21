package com.misgus.pengu.core.watched

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.ProgressThresholds
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.ProgressRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock
import kotlin.time.Instant

/** A title to resolve a watched state for. */
data class WatchedCandidate(
    val id: String,
    val type: ContentType,
)

/**
 * Decides which titles the account has watched, mirroring Nuvio.
 *
 * * A movie is watched when there is a title-level marker in the watched
 *   history.
 * * A series is watched when all released main-season episodes have been
 *   watched or completed (see [hasWatchedAllMainSeasonEpisodes]).
 *
 * The metadata lookup is the expensive part, so it is bounded: it runs only for
 * series that show watched activity, with limited concurrency, and each result
 * is remembered in [cache] keyed by a signature of the watched input.
 */
class WatchedBadgeResolver(
    private val progress: ProgressRepository,
    private val metadata: MetadataRepository,
    private val cache: WatchedSeriesCache = NoopWatchedSeriesCache(),
) {
    /**
     * Content ids of [candidates] that are watched.
     *
     * [progressEntries] avoids re-reading watch progress the caller already has.
     * [profileKey] scopes the cache; [today] is injectable for tests.
     */
    suspend fun resolve(
        candidates: Collection<WatchedCandidate>,
        progressEntries: List<WatchProgress>? = null,
        profileKey: String = "",
        today: Instant? = null,
    ): Set<String> {
        if (candidates.isEmpty()) return emptySet()
        val now = today ?: Clock.System.now()

        val ids = candidates.map { it.id }.toSet()
        // Both progress reads are independent; when the caller already has the
        // full progress list, only the watched markers are fetched.
        val (entries, allProgress) =
            if (progressEntries != null) {
                progress.watchedEntries(ids) to progressEntries
            } else {
                coroutineScope {
                    val entriesDeferred = async { progress.watchedEntries(ids) }
                    val allDeferred = async { progress.all() }
                    entriesDeferred.await() to allDeferred.await()
                }
            }

        val watchedKeys =
            entries.mapTo(mutableSetOf()) {
                WatchedKey(it.contentId, it.season, it.episode)
            }
        val completedKeys =
            allProgress.mapNotNullTo(mutableSetOf()) {
                if ((it.fraction ?: 0.0) >= ProgressThresholds.COMPLETION) {
                    WatchedKey(it.contentId, it.season, it.episode)
                } else {
                    null
                }
            }

        val watched = mutableSetOf<String>()
        val series = mutableListOf<WatchedCandidate>()
        for (candidate in candidates) {
            if (!ContentTypes.isSeries(candidate.type)) {
                if (WatchedKey(candidate.id) in watchedKeys) watched.add(candidate.id)
                continue
            }
            // A manual "mark as watched" writes a title-level marker for any type.
            if (WatchedKey(candidate.id) in watchedKeys) {
                watched.add(candidate.id)
                continue
            }
            val hasActivity =
                entries.any { it.contentId == candidate.id } ||
                    completedKeys.any { it.contentId == candidate.id }
            if (hasActivity) series.add(candidate)
        }

        val toCache = mutableMapOf<String, CachedSeriesState>()
        for (batch in series.chunked(MAX_CONCURRENT_LOOKUPS)) {
            val results =
                coroutineScope {
                    batch
                        .map { candidate ->
                            async { resolveSeries(candidate, watchedKeys, completedKeys, now, profileKey) }
                        }.awaitAll()
                }
            results.forEachIndexed { index, result ->
                if (result.watched) watched.add(batch[index].id)
                result.cachedState?.let { toCache[batch[index].id] = it }
            }
        }
        // One write for the whole resolve instead of one per series.
        if (toCache.isNotEmpty()) cache.writeAll(profileKey, toCache)
        return watched
    }

    private suspend fun resolveSeries(
        candidate: WatchedCandidate,
        watchedKeys: Set<WatchedKey>,
        completedKeys: Set<WatchedKey>,
        today: Instant,
        profileKey: String,
    ): SeriesResult {
        val signature = seriesSignature(candidate.id, watchedKeys, completedKeys, today)
        val cached = cache.read(profileKey, candidate.id)
        if (cached != null && cached.signature == signature) {
            return SeriesResult(cached.watched, null)
        }

        val meta = metadata.detail(ContentTypes.SERIES, candidate.id)
        val isWatched =
            meta != null &&
                hasWatchedAllMainSeasonEpisodes(meta, watchedKeys, completedKeys, today)
        return SeriesResult(isWatched, CachedSeriesState(signature, isWatched))
    }

    private data class SeriesResult(
        val watched: Boolean,
        val cachedState: CachedSeriesState?,
    )

    private fun seriesSignature(
        seriesId: String,
        watched: Set<WatchedKey>,
        completed: Set<WatchedKey>,
        today: Instant,
    ): String {
        val watchedValues =
            watched
                .filter { it.contentId == seriesId }
                .map(::serialize)
                .sorted()
        val completedValues =
            completed
                .filter { it.contentId == seriesId }
                .map(::serialize)
                .sorted()
        // A plain, readable signature: it survives restarts and is easy to
        // inspect in the cache file, unlike a truncated hash.
        val day = today.toString().substringBefore('T')
        return "$day|${watchedValues.joinToString(";")}|${completedValues.joinToString(";")}"
    }

    private fun serialize(key: WatchedKey) = "${key.contentId}|${key.season ?: -1}|${key.episode ?: -1}"

    private companion object {
        const val MAX_CONCURRENT_LOOKUPS = 6
    }
}
