package com.misgus.pengu.ui.home

import com.misgus.pengu.core.model.ContentType
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
    fun showsHowFarInThePlaybackIs() {
        assertEquals("12:34 / 45:00", timecode(progress(position = 12, duration = 45)))
    }

    @Test
    fun hasNoTimecodeWithoutALength() {
        assertNull(timecode(progress(position = 12, duration = null)))
    }
}
