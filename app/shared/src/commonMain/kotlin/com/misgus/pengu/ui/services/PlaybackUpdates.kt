package com.misgus.pengu.ui.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Signals that a playback session closed.
 *
 * A screen that shows progress cannot see the player's last save by itself: the
 * section stays composed while the player is open, so it never reloads. The
 * player bumps [closed] *after* the final position is persisted, and the screens
 * that show progress repaint from that — one tick per playback, instead of
 * polling the repository on every periodic save (which happens every few seconds
 * while a title plays).
 */
class PlaybackUpdates {
    private val _closed = MutableStateFlow(0L)

    /** Bumped once a playback session has saved its last position and closed. */
    val closed: StateFlow<Long> = _closed.asStateFlow()

    /** Called by the player when it is done writing the last position. */
    fun notifyClosed() = _closed.update { it + 1 }
}
