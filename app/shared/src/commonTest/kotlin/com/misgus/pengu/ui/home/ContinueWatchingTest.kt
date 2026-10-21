package com.misgus.pengu.ui.home

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ContinueWatchingTest {
    private fun progress(
        position: Int,
        duration: Int?,
    ) = WatchProgress(
        id = "p1",
        contentId = "m1",
        contentType = ContentType("movie"),
        position = position.minutes + 34.seconds,
        duration = duration?.minutes,
    )

    @Test
    fun showsTheMinutesLeftRoundedUp() {
        // 45:00 minus 12:34 is 32:26, which reads better as "33 min left" than 32.
        assertEquals(33, remainingMinutes(progress(position = 12, duration = 45)))
    }

    @Test
    fun hasNothingToSayWithoutALength() {
        assertNull(remainingMinutes(progress(position = 12, duration = null)))
    }

    @Test
    fun hasNothingToSayOnceThePlaybackIsOver() {
        assertNull(remainingMinutes(progress(position = 45, duration = 45)))
    }

    @Test
    fun resumesWithTheEpisodeIdProgressWasReportedWith() {
        val series =
            WatchProgress(
                id = "p2",
                contentId = "tt1",
                contentType = ContentTypes.SERIES,
                videoId = "tt1:2:5",
                season = Season(2),
                episode = Episode(5),
                position = 10.minutes,
                duration = 45.minutes,
            )

        assertEquals("tt1:2:5", resumePlayId(series))
    }

    @Test
    fun resumesAMovieWithItsContentId() {
        assertEquals("m1", resumePlayId(progress(position = 10, duration = 45)))
    }

    @Test
    fun resumesAMovieWhoseVideoIdIsStoredBlank() {
        // What the backend actually stores for a movie: an empty string, not null.
        val movie = progress(position = 10, duration = 45).copy(videoId = "")

        assertEquals("m1", resumePlayId(movie))
    }

    @Test
    fun cannotAddressAStreamWithoutTheEpisodeId() {
        val series =
            WatchProgress(
                id = "p3",
                contentId = "tt1",
                contentType = ContentTypes.SERIES,
                position = 10.minutes,
                duration = 45.minutes,
            )

        assertNull(resumePlayId(series))
    }
}
