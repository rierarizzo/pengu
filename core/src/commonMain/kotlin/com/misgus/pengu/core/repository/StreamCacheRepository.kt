package com.misgus.pengu.core.repository

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The stream a title (or one episode) was last played with.
 *
 * Kept so "Continue watching" can resume without walking through the picker
 * again. Debrid sources hand out signed URLs that expire and rotate, so an entry
 * is only trusted while [AppSettings.streamUrlCache] says it is fresh; the id of
 * the stream is not stored, because the URL is all playback needs.
 */
data class LastStream(
    val contentId: String,
    /** Episode video id, or `null` for a movie. */
    val videoId: String? = null,
    val url: String,
    val httpHeaders: Map<String, String> = emptyMap(),
    val resolvedAt: Instant,
    /**
     * Name of the episode this stream plays, when it is one.
     *
     * The progress row has no column for it, so the card reads it from here
     * instead of asking the addons for metadata again.
     */
    val episodeTitle: String? = null,
) {
    /** Whether this URL may still be played under [cache], read at [now]. */
    fun isFresh(
        cache: StreamUrlCache,
        now: Instant,
    ): Boolean = cache.ttl > Duration.ZERO && resolvedAt + cache.ttl > now
}

/** Remembers the stream each title was last played with, for resuming. */
interface StreamCacheRepository {
    suspend fun save(stream: LastStream)

    /** The remembered stream for [contentId] (its [videoId] episode, or the movie), or `null`. */
    suspend fun find(
        contentId: String,
        videoId: String?,
    ): LastStream?

    /** Drops the entry, e.g. after its URL refused to play. */
    suspend fun remove(
        contentId: String,
        videoId: String?,
    )
}

/** [StreamCacheRepository] that remembers nothing (tests, or a build without storage). */
class NoopStreamCacheRepository : StreamCacheRepository {
    override suspend fun save(stream: LastStream) = Unit

    override suspend fun find(
        contentId: String,
        videoId: String?,
    ): LastStream? = null

    override suspend fun remove(
        contentId: String,
        videoId: String?,
    ) = Unit
}
