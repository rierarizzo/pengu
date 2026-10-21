package com.misgus.pengu.core.player

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.Season

/**
 * What the player is asked to play, plus the identity needed to save progress.
 *
 * [source] is a direct URL or a local path. The content fields mirror the
 * backend's `watch_progress` row, so the player can report progress without
 * knowing where the request came from.
 */
data class PlaybackTarget(
    val source: String,
    val title: String,
    val contentId: String? = null,
    /** `movie`, `series`, ... */
    val contentType: ContentType? = null,
    /** Playback id of the episode, when it is one. */
    val videoId: String? = null,
    val season: Season? = null,
    val episode: Episode? = null,
    /** Headers the player must send (an addon's `proxyHeaders`). */
    val httpHeaders: Map<String, String>? = null,
    /** Name of the episode this target plays, for the screens that describe it. */
    val episodeTitle: String? = null,
) {
    /** Whether progress can be saved for this target. */
    val canReportProgress: Boolean get() = contentId != null

    /**
     * Nuvio's stable progress identity: `content_s{season}e{episode}` for an
     * episode, the content id for a movie.
     */
    val progressKey: String?
        get() {
            val id = contentId ?: return null
            val s = season
            val e = episode
            return if (s != null && e != null) "${id}_s${s}e$e" else id
        }

    companion object {
        /**
         * Builds a target from a content request and the chosen stream.
         *
         * [id] is the playable id: a movie id (`tt123`), or an episode id
         * (`tt0149460:2:16`, possibly with a prefixed content id like
         * `tmdb:123:1:2`).
         *
         * [season] and [episode] are the ones the caller already knows — the
         * addon's video object carries them — and they are what the id must be read
         * with when the id itself is ambiguous: an anime addon names an episode
         * `kitsu:43248:1`, which the trailing-numbers rule would read as content
         * `kitsu`, season 43248. [contentId] is the title's own id when it is not
         * the id's prefix. Without them the id is parsed, which is right for the
         * ids the protocol defines.
         */
        fun fromContent(
            type: ContentType,
            id: String,
            title: String,
            source: String,
            httpHeaders: Map<String, String>? = null,
            season: Season? = null,
            episode: Episode? = null,
            contentId: String? = null,
            episodeTitle: String? = null,
        ): PlaybackTarget {
            val parsed = parseEpisodeId(id)
            val resolvedSeason = season ?: parsed?.season
            val resolvedEpisode = episode ?: parsed?.episode
            val isEpisode = resolvedSeason != null && resolvedEpisode != null
            return PlaybackTarget(
                source = source,
                title = title,
                contentId = contentId ?: parsed?.contentId ?: id,
                contentType = type,
                videoId = if (isEpisode) id else null,
                season = if (isEpisode) resolvedSeason else null,
                episode = if (isEpisode) resolvedEpisode else null,
                httpHeaders = httpHeaders,
                episodeTitle = episodeTitle,
            )
        }

        /** `content:season:episode` split from the end, or `null` when it is not one. */
        private fun parseEpisodeId(id: String): ParsedEpisodeId? {
            val parts = id.split(':')
            if (parts.size < 3) return null
            // Split from the end so content ids with colons (`tmdb:123`) survive.
            val season = parts[parts.size - 2].toIntOrNull()?.let(::Season) ?: return null
            val episode = parts.last().toIntOrNull()?.let(::Episode) ?: return null
            return ParsedEpisodeId(contentId = parts.dropLast(2).joinToString(":"), season = season, episode = episode)
        }

        private data class ParsedEpisodeId(
            val contentId: String,
            val season: Season,
            val episode: Episode,
        )

        /** A source with no account identity (the `--play` development shortcut). */
        fun raw(source: String) = PlaybackTarget(source = source, title = source)
    }
}
