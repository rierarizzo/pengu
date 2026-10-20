package com.misgus.pengu.core.player

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** A selectable audio or subtitle track. */
data class MediaTrack(
    val id: String,
    val title: String? = null,
    val language: String? = null,
    /** Subtitle file URL, when the track is an external addon subtitle. */
    val uri: String? = null,
    val isExternal: Boolean = false,
)

/** Immutable snapshot of the player state. */
data class PlayerState(
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
    /**
     * End of the data buffered ahead of [position], or `null` when the engine does
     * not report it (a local file, or a backend without a cache).
     *
     * `bufferedPosition - position` is the lookahead the player can play without
     * waiting on the network; the pre-roll uses it to reveal only when there is
     * some, instead of trusting a timer. A value at or behind [position] means
     * nothing is cached ahead.
     */
    val bufferedPosition: Duration? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val completed: Boolean = false,
    /** Volume, `0`–`100`. */
    val volume: Double = 100.0,
    /** Last engine error, or `null`. */
    val error: String? = null,
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val selectedAudioId: String? = null,
    /** `null` means subtitles off. */
    val selectedSubtitleId: String? = null,
)

/**
 * Wraps the native playback engine so no composable talks to it directly.
 *
 * The player is isolated behind this interface (see the architecture rules);
 * the desktop implementation lives in the composition root.
 */
interface PlayerService {
    val state: StateFlow<PlayerState>

    /**
     * Platform handle for the video surface (on desktop, a Compose frame
     * source), or `null` while no engine is available.
     *
     * The UI casts it to the platform type in `jvmMain`, which keeps the UI
     * layer independent from the concrete engine.
     */
    val videoOutput: VideoOutput?

    /**
     * Whether this engine can actually play media on the current target.
     *
     * The UI reads this flag instead of type-checking the implementation (for
     * example a `NoopPlayerService` on a target without an engine), so the
     * capability lives in the contract and survives new engines.
     */
    val canPlay: Boolean

    /** Opens [target]. */
    fun open(target: PlaybackTarget)

    fun playOrPause()

    /**
     * Pauses playback, keeping the loaded media.
     *
     * Used by the app lifecycle (backgrounding) and worth keeping distinct from
     * [playOrPause] so a lifecycle callback never *starts* playback by accident.
     */
    fun pause()

    fun seek(position: Duration)

    fun seekBy(offset: Duration)

    fun setVolume(volume: Double)

    fun selectAudioTrack(track: MediaTrack)

    /** Pass `null` to turn subtitles off. */
    fun selectSubtitleTrack(track: MediaTrack?)

    /** Stops playback and clears the current media. */
    fun stop()

    fun dispose()
}
