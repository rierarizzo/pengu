package com.misgus.pengu.ui.screens

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EpisodeWatchStateTest {
    private fun progress(
        season: Int,
        episode: Int,
        position: Int,
        duration: Int = 50,
    ) = WatchProgress(
        id = "tt1:$season:$episode",
        contentId = "tt1",
        contentType = ContentTypes.SERIES,
        season = Season(season),
        episode = Episode(episode),
        position = position.minutes,
        duration = duration.minutes,
    )

    @Test
    fun splitsWatchedPartWayAndFinishedEpisodes() {
        val state =
            episodeWatchState(
                entries = listOf(WatchedEntry("tt1", ContentTypes.SERIES, Season(1), Episode(2))),
                progressEntries =
                    listOf(
                        progress(season = 1, episode = 1, position = 10),
                        progress(season = 1, episode = 3, position = 48),
                    ),
            )

        assertEquals(setOf(1 to 2, 1 to 3), state.watched)
        // The part-way episode keeps its bar; the finished one is watched instead.
        assertEquals(mapOf((1 to 1) to 0.2f), state.progress)
        assertTrue(state.isWatched(1 to 3))
        assertFalse(state.isWatched(1 to 1))
    }

    @Test
    fun aTitleLevelMarkerCoversEveryEpisode() {
        val state =
            episodeWatchState(
                entries = listOf(WatchedEntry("tt1", ContentTypes.SERIES)),
                progressEntries = listOf(progress(season = 2, episode = 7, position = 5)),
            )

        assertTrue(state.titleWatched)
        assertTrue(state.isWatched(2 to 7))
        // The card keeps the leftover progress row but draws the badge instead,
        // because a watched episode never shows a bar.
        assertEquals(0.1f, state.fraction(2 to 7))
    }

    @Test
    fun aTitleLevelProgressEntryIsNotAnEpisode() {
        val state =
            episodeWatchState(
                entries = emptyList(),
                progressEntries =
                    listOf(
                        WatchProgress(
                            id = "tt1",
                            contentId = "tt1",
                            contentType = ContentTypes.SERIES,
                            position = 10.minutes,
                            duration = 50.minutes,
                        ),
                    ),
            )

        assertEquals(emptyMap(), state.progress)
        assertNull(state.fraction(1 to 1))
    }
}
