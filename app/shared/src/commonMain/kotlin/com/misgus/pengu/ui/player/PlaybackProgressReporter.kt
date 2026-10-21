package com.misgus.pengu.ui.player

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.ProgressThresholds
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Saves watch progress for the playing [PlaybackTarget].
 *
 * Writes at most once per [INTERVAL] while playing, plus on pause, completion
 * and exit. A target without a content id (the `--play` shortcut) is ignored.
 */
class PlaybackProgressReporter(
    private val repository: ProgressRepository,
    private val settings: SettingsRepository,
    private val target: PlaybackTarget,
) {
    private var position: Duration = Duration.ZERO
    private var duration: Duration = Duration.ZERO
    private var lastSaved: Duration = Duration.ZERO
    private var resumeResolved = false

    /**
     * The last write that failed, so the player can say the position was not
     * stored instead of losing it quietly.
     */
    var lastFailure: Throwable? = null
        private set

    /** Saved position to resume from, when it is worth resuming. */
    suspend fun resumePosition(): Duration? {
        if (!target.canReportProgress || resumeResolved) return null
        resumeResolved = true
        if (!settings.state.value.resumePlayback) return null
        return runCatchingCancellable {
            val entry = find(repository.all())
            val savedPosition = entry?.position
            if (savedPosition == null || savedPosition <= Duration.ZERO) return@runCatchingCancellable null
            val savedDuration = entry.duration
            if (savedDuration != null && savedDuration > Duration.ZERO) {
                val fraction =
                    savedPosition.inWholeMilliseconds.toDouble() /
                        savedDuration.inWholeMilliseconds
                if (fraction < ProgressThresholds.RESUME_MIN || fraction > ProgressThresholds.RESUME_MAX) {
                    return@runCatchingCancellable null
                }
            } else if (savedPosition < Duration.parse("5s")) {
                return@runCatchingCancellable null
            }
            savedPosition
        }.getOrNull()
    }

    /** Records the latest playback state. [flush] persists it when it is due. */
    fun update(
        position: Duration,
        duration: Duration,
    ) {
        this.position = position
        this.duration = duration
    }

    /** Persists the current state, unless [force] when it is too soon. */
    suspend fun flush(force: Boolean = false) {
        if (!target.canReportProgress) return
        if (duration <= Duration.ZERO) return
        if (!force && (position - lastSaved).absoluteValue < INTERVAL) return
        if (position <= Duration.ZERO && lastSaved <= Duration.ZERO) return
        lastSaved = position
        runCatchingCancellable {
            repository.save(
                WatchProgress(
                    id = "",
                    contentId = target.contentId!!,
                    contentType = target.contentType ?: ContentTypes.MOVIE,
                    videoId = target.videoId,
                    season = target.season,
                    episode = target.episode,
                    position = position,
                    duration = duration,
                    lastWatched = Clock.System.now(),
                    progressKey = target.progressKey,
                ),
            )
        }.onSuccess { lastFailure = null }
            .onFailure { lastFailure = it }
    }

    private fun find(entries: List<WatchProgress>): WatchProgress? {
        val key = target.progressKey
        for (entry in entries) {
            if (key != null && entry.progressKey == key) return entry
            if (entry.contentId == target.contentId &&
                entry.season == target.season &&
                entry.episode == target.episode
            ) {
                return entry
            }
        }
        return null
    }

    private companion object {
        val INTERVAL = 10.seconds
    }
}
