package com.misgus.pengu.ui.player

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.ui.support.FakeProgressRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class PlaybackProgressReporterTest {
    @Test
    fun resumesTheSavedPosition() =
        runTest {
            val reporter =
                PlaybackProgressReporter(
                    repository = progress(),
                    settings = InMemorySettingsRepository(AppSettings(resumePlayback = true)),
                    target = target(startFromBeginning = false),
                )

            assertEquals(30.minutes, reporter.resumePosition())
        }

    @Test
    fun startFromBeginningSkipsTheSavedPosition() =
        runTest {
            val reporter =
                PlaybackProgressReporter(
                    repository = progress(),
                    settings = InMemorySettingsRepository(AppSettings(resumePlayback = true)),
                    target = target(startFromBeginning = true),
                )

            assertNull(reporter.resumePosition())
        }

    private fun target(startFromBeginning: Boolean): PlaybackTarget =
        PlaybackTarget.fromContent(
            type = ContentType("movie"),
            id = "tt1",
            title = "A movie",
            source = "https://example/movie.mp4",
            startFromBeginning = startFromBeginning,
        )

    private fun progress(): FakeProgressRepository =
        FakeProgressRepository(
            progress =
                listOf(
                    WatchProgress(
                        id = "",
                        contentId = "tt1",
                        contentType = ContentType("movie"),
                        position = 30.minutes,
                        duration = 60.minutes,
                        progressKey = "tt1",
                    ),
                ),
        )
}
