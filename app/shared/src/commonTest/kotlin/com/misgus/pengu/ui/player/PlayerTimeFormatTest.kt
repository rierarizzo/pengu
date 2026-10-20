package com.misgus.pengu.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlayerTimeFormatTest {
    @Test
    fun padsSecondsUnderAMinute() {
        assertEquals("0:03", formatPlayerTime(3.seconds))
    }

    @Test
    fun keepsMinutesUnderAnHour() {
        assertEquals("56:01", formatPlayerTime(56.minutes + 1.seconds))
    }

    @Test
    fun addsHoursOnlyWhenNeeded() {
        assertEquals("1:02:03", formatPlayerTime(1.hours + 2.minutes + 3.seconds))
    }

    @Test
    fun clampsNegativeTimes() {
        assertEquals("0:00", formatPlayerTime((-3).seconds))
    }
}
