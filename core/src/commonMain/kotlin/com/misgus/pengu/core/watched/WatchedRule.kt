package com.misgus.pengu.core.watched

import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.Season
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/**
 * Identity of a watched-history or progress row: a title or one of its
 * episodes.
 *
 * A data class, so it has structural equality and can be used as a `Set`/`Map`
 * key.
 */
data class WatchedKey(
    val contentId: String,
    val season: Season? = null,
    val episode: Episode? = null,
)

/**
 * Whether every released main-season episode of [meta] is watched or completed.
 *
 * This is Nuvio's rule: only main seasons (`season > 0`) count, only episodes
 * that already aired count, and a series with no episodes is not "watched".
 *
 * Pure on purpose — no repositories and no clock — so it can be unit-tested
 * directly and reused by any caller.
 */
fun hasWatchedAllMainSeasonEpisodes(
    meta: MetaDetail,
    watched: Set<WatchedKey>,
    completed: Set<WatchedKey>,
    today: Instant,
): Boolean {
    val episodes = meta.videos.filter { (it.season?.number ?: 0) > 0 && isEpisodeAired(it.released, today) }
    if (episodes.isEmpty()) return false
    return episodes.all { video ->
        val key = WatchedKey(meta.id, video.season, video.episode)
        key in watched || key in completed
    }
}

/** `false` only when [released] is a future date; an unknown date means aired. */
fun isEpisodeAired(
    released: String?,
    today: Instant,
): Boolean {
    if (released.isNullOrEmpty()) return true
    val parsed = parseReleaseInstant(released) ?: return true
    return parsed <= today
}

private fun parseReleaseInstant(released: String): Instant? {
    runCatching { return Instant.parse(released) }
    runCatching { return LocalDate.parse(released).atStartOfDayIn(TimeZone.UTC) }
    return null
}
