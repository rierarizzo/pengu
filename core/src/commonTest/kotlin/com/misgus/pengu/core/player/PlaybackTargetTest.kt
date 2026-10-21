package com.misgus.pengu.core.player

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.Season
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackTargetTest {
    @Test
    fun buildsMovieTarget() {
        val target =
            PlaybackTarget.fromContent(
                type = ContentType("movie"),
                id = "tt1254207",
                title = "Big Buck Bunny",
                source = "https://example/movie.mp4",
            )
        assertEquals("tt1254207", target.contentId)
        assertEquals("tt1254207", target.progressKey)
        assertNull(target.videoId)
        assertNull(target.season)
        assertEquals(true, target.canReportProgress)
    }

    @Test
    fun buildsEpisodeTarget() {
        val target =
            PlaybackTarget.fromContent(
                type = ContentType("series"),
                id = "tt0149460:2:16",
                title = "The Office",
                source = "https://example/ep.mp4",
            )
        assertEquals("tt0149460", target.contentId)
        assertEquals("tt0149460:2:16", target.videoId)
        assertEquals(2, target.season?.number)
        assertEquals(16, target.episode?.number)
        assertEquals("tt0149460_s2e16", target.progressKey)
    }

    @Test
    fun usesTheEpisodeTheCallerKnowsWhenTheIdCannotBeRead() {
        // What an anime addon sends: the middle part is the show's kitsu id, so the
        // id cannot be read as `content:season:episode`.
        val target =
            PlaybackTarget.fromContent(
                type = ContentType("series"),
                id = "kitsu:43248:2",
                title = "Cyberpunk: Edgerunners",
                source = "https://example/ep.mp4",
                season = Season(1),
                episode = Episode(2),
                contentId = "kitsu:50016",
            )

        assertEquals("kitsu:50016", target.contentId)
        assertEquals("kitsu:43248:2", target.videoId)
        assertEquals(1, target.season?.number)
        assertEquals(2, target.episode?.number)
        assertEquals("kitsu:50016_s1e2", target.progressKey)
    }

    @Test
    fun keepsTheIdsPrefixWhenTheCallerKnowsTheEpisode() {
        val target =
            PlaybackTarget.fromContent(
                type = ContentType("series"),
                id = "tt0149460:2:16",
                title = "The Office",
                source = "https://example/ep.mp4",
                season = Season(2),
                episode = Episode(16),
            )

        assertEquals("tt0149460", target.contentId)
        assertEquals("tt0149460_s2e16", target.progressKey)
    }

    @Test
    fun keepsContentIdsWithColons() {
        val target =
            PlaybackTarget.fromContent(
                type = ContentType("series"),
                id = "tmdb:123:1:2",
                title = "Show",
                source = "https://example/ep.mp4",
            )
        assertEquals("tmdb:123", target.contentId)
        assertEquals(1, target.season?.number)
        assertEquals(2, target.episode?.number)
        assertEquals("tmdb:123_s1e2", target.progressKey)
    }

    @Test
    fun rawTargetCannotReportProgress() {
        val target = PlaybackTarget.raw("https://example/video.mp4")
        assertEquals(false, target.canReportProgress)
        assertNull(target.progressKey)
    }
}
