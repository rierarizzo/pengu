package com.misgus.pengu.core.watched

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.Season
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class WatchedRuleTest {
    private val today = Instant.parse("2024-01-10T00:00:00Z")

    private fun series(vararg videos: MetaVideo): MetaDetail =
        MetaDetail(
            id = "tt1",
            type = ContentType("series"),
            name = "Show",
            videos = videos.toList(),
        )

    @Test
    fun notWatchedWhenAnAiredEpisodeIsMissing() {
        val meta =
            series(
                MetaVideo(id = "tt1:1:1", season = Season(1), episode = Episode(1), released = "2024-01-01"),
                MetaVideo(id = "tt1:1:2", season = Season(1), episode = Episode(2), released = "2024-01-01"),
            )
        val watched = setOf(WatchedKey("tt1", Season(1), Episode(1)))
        assertFalse(hasWatchedAllMainSeasonEpisodes(meta, watched, emptySet(), today))
    }

    @Test
    fun watchedWhenEveryAiredEpisodeIsWatchedOrCompleted() {
        val meta =
            series(
                MetaVideo(id = "tt1:1:1", season = Season(1), episode = Episode(1), released = "2024-01-01"),
                MetaVideo(id = "tt1:1:2", season = Season(1), episode = Episode(2), released = "2024-01-01"),
            )
        val watched = setOf(WatchedKey("tt1", Season(1), Episode(1)))
        val completed = setOf(WatchedKey("tt1", Season(1), Episode(2)))
        assertTrue(hasWatchedAllMainSeasonEpisodes(meta, watched, completed, today))
    }

    @Test
    fun ignoresFutureEpisodesAndSpecials() {
        val meta =
            series(
                MetaVideo(id = "tt1:1:1", season = Season(1), episode = Episode(1), released = "2024-01-01"),
                MetaVideo(id = "tt1:1:2", season = Season(1), episode = Episode(2), released = "2030-01-01"),
                MetaVideo(id = "tt1:0:1", season = Season(0), episode = Episode(1), released = "2024-01-01"),
            )
        val watched = setOf(WatchedKey("tt1", Season(1), Episode(1)))
        assertTrue(hasWatchedAllMainSeasonEpisodes(meta, watched, emptySet(), today))
    }

    @Test
    fun seriesWithNoAiredEpisodesIsNotWatched() {
        val meta =
            series(
                MetaVideo(id = "tt1:1:1", season = Season(1), episode = Episode(1), released = "2030-01-01"),
            )
        assertFalse(hasWatchedAllMainSeasonEpisodes(meta, emptySet(), emptySet(), today))
    }

    @Test
    fun unknownReleaseDateCountsAsAired() {
        assertTrue(isEpisodeAired(null, today))
        assertTrue(isEpisodeAired("not-a-date", today))
        assertFalse(isEpisodeAired("2030-01-01", today))
    }
}
