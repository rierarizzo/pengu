package com.misgus.pengu.ui.streams

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.Season

/** What the stream picker should look up. */
data class StreamsRequest(
    val type: ContentType,
    val id: String,
    val title: String,
    val year: String? = null,
    /**
     * The title's own id, the episode's season and number, when the caller knows
     * them: they are what turns the picked stream into a playback target that
     * reports progress against the right title (see `PlaybackTarget.fromContent`).
     */
    val contentId: String? = null,
    val season: Season? = null,
    val episode: Episode? = null,
    /** Name of the episode, so the player can remember it for the continue card. */
    val episodeTitle: String? = null,
)
