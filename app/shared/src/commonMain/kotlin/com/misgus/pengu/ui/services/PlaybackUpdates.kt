package com.misgus.pengu.ui.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Signals that something outside a screen changed the data it shows.
 *
 * A screen that shows progress cannot see the player's last save by itself: the
 * section stays composed while the player is open, so it never reloads. The
 * player bumps [closed] *after* the final position is persisted, and the screens
 * that show progress repaint from that — one tick per playback, instead of
 * polling the repository on every periodic save (which happens every few seconds
 * while a title plays).
 *
 * [cachesCleared] is the same idea for the cache actions in Settings: the local
 * copies are gone, so the feed re-reads them from the backend.
 */
class PlaybackUpdates {
    private val _closed = MutableStateFlow(0L)

    /** Bumped once a playback session has saved its last position and closed. */
    val closed: StateFlow<Long> = _closed.asStateFlow()

    private val _cachesCleared = MutableStateFlow(0L)

    /** Bumped once the local caches were dropped from Settings. */
    val cachesCleared: StateFlow<Long> = _cachesCleared.asStateFlow()

    /** Called by the player when it is done writing the last position. */
    fun notifyClosed() = _closed.update { it + 1 }

    /** Called by the cache actions when the local copies are gone. */
    fun notifyCachesCleared() = _cachesCleared.update { it + 1 }
}
