package com.misgus.pengu.core.watched

/** A remembered "fully watched" result for one series. */
data class CachedSeriesState(
    /**
     * Signature of the watched input this result was computed from. When the
     * input changes (a new episode is watched, a new day), it stops matching.
     */
    val signature: String,
    val watched: Boolean,
)

/**
 * Remembers the per-series "fully watched" results so the metadata lookup is
 * not repeated on every load.
 *
 * Nuvio keeps an equivalent cache locally (`fullyWatchedSeriesKeys`); it is not
 * synced through the account backend.
 */
interface WatchedSeriesCache {
    suspend fun read(
        profileKey: String,
        seriesId: String,
    ): CachedSeriesState?

    /** Stores several series at once, so a resolve can persist in one write. */
    suspend fun writeAll(
        profileKey: String,
        states: Map<String, CachedSeriesState>,
    )

    /** Forgets every series of [profileKey]. */
    suspend fun clear(profileKey: String)
}

/** Cache that remembers nothing: every resolve recomputes. */
class NoopWatchedSeriesCache : WatchedSeriesCache {
    override suspend fun read(
        profileKey: String,
        seriesId: String,
    ): CachedSeriesState? = null

    override suspend fun writeAll(
        profileKey: String,
        states: Map<String, CachedSeriesState>,
    ) = Unit

    override suspend fun clear(profileKey: String) = Unit
}
