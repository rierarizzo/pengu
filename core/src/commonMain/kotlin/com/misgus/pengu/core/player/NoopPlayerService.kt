package com.misgus.pengu.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

/**
 * [PlayerService] that plays nothing.
 *
 * Used on targets without an engine wired up yet (Android) and in tests; it
 * reports a clear error instead of crashing.
 */
class NoopPlayerService(
    private val message: String = "Playback is not available on this target yet.",
) : PlayerService {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override val videoOutput: VideoOutput? = null

    override val canPlay: Boolean = false

    override fun open(target: PlaybackTarget) {
        _state.update { it.copy(error = message) }
    }

    override fun playOrPause() = Unit

    override fun pause() = Unit

    override fun seek(position: Duration) = Unit

    override fun seekBy(offset: Duration) = Unit

    override fun setVolume(volume: Double) = Unit

    override fun setSpeed(speed: Float) = Unit

    override fun selectAudioTrack(track: MediaTrack) = Unit

    override fun selectSubtitleTrack(track: MediaTrack?) = Unit

    override fun stop() {
        _state.value = PlayerState()
    }

    override fun dispose() = Unit
}
