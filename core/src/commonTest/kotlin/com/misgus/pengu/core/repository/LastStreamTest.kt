package com.misgus.pengu.core.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LastStreamTest {
    private val resolvedAt = Instant.fromEpochMilliseconds(1_000_000)
    private val stream =
        LastStream(
            contentId = "tt1",
            videoId = "tt1:1:1",
            url = "https://debrid/expiring-link.mkv",
            resolvedAt = resolvedAt,
        )

    @Test
    fun isFreshWithinTheCacheWindow() {
        assertTrue(stream.isFresh(StreamUrlCache.SIX_HOURS, resolvedAt + 5.hours))
    }

    @Test
    fun isStaleOnceTheCacheWindowHasPassed() {
        assertFalse(stream.isFresh(StreamUrlCache.SIX_HOURS, resolvedAt + 7.hours))
    }

    @Test
    fun offNeverTrustsARememberedUrl() {
        assertFalse(stream.isFresh(StreamUrlCache.OFF, resolvedAt))
    }
}
