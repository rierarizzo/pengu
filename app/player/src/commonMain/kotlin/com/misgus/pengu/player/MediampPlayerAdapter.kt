package com.misgus.pengu.player

import com.misgus.pengu.core.player.MediaTrack
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.player.PlayerService
import com.misgus.pengu.core.player.PlayerState
import com.misgus.pengu.core.player.VideoOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.errorOrNull
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.features.MediaMetadata
import org.openani.mediamp.metadata.AudioTrack
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared [PlayerService] adapter over mediamp's [MediampPlayer].
 *
 * The desktop and Android composition roots each extend it with their concrete
 * backend, overriding only what differs per engine: how the surface is drawn,
 * the volume (mediamp exposes it through different features) and how an external
 * addon subtitle is attached. State, position, duration, track candidates, seek
 * and the lifecycle live here once.
 */
abstract class MediampPlayerAdapter protected constructor(
    protected val player: MediampPlayer,
) : PlayerService {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    // Track groups expose `candidates` as a plain Flow, so the latest emission is
    // kept to resolve a selection back to the candidate object.
    private var audioCandidates: List<AudioTrack> = emptyList()
    private var subtitleCandidates: List<SubtitleTrack> = emptyList()

    /** Target of the current session, for an engine that has to reopen it. */
    private var currentTarget: PlaybackTarget? = null

    // Set while an external addon subtitle is attached; it wins over the
    // backend's own selection so the UI keeps the addon track marked.
    private var externalSubtitleId: String? = null

    override val canPlay: Boolean = true

    final override val videoOutput: VideoOutput by lazy { createVideoOutput() }

    init {
        scope.launch {
            player.state.collect { s ->
                _state.update {
                    it.copy(
                        playing = s.playWhenReady,
                        buffering = s.isBuffering || s.mediaStatus == MediaStatus.Opening,
                        completed = s.mediaStatus == MediaStatus.Ended,
                        error = s.errorOrNull?.message,
                    )
                }
            }
        }
        scope.launch {
            player.currentPositionMillis.collect { ms -> _state.update { it.copy(position = ms.milliseconds) } }
        }
        scope.launch {
            player.mediaProperties.collect { p ->
                _state.update { it.copy(duration = (p?.durationMillis ?: 0L).milliseconds) }
            }
        }
        // Lookahead, when the backend reports it: both engines behind this adapter
        // do (mpv's demuxer cache end, ExoPlayer's buffered position). `UNKNOWN`
        // is published as `null` so the UI can tell "no cache ahead" from "no
        // opinion" — a local file has no cache to fill.
        player.features[Buffering]?.let { buffering ->
            scope.launch {
                buffering.bufferedPositionMillis.collect { millis ->
                    _state.update {
                        it.copy(bufferedPosition = millis.takeIf { it >= 0 }?.milliseconds)
                    }
                }
            }
        }
        player.features[MediaMetadata]?.let { metadata ->
            metadata.audioTracks?.let { group ->
                scope.launch {
                    group.candidates.collect { list ->
                        audioCandidates = list
                        _state.update { it.copy(audioTracks = list.map(AudioTrack::toMediaTrack)) }
                    }
                }
                scope.launch { group.selected.collect { sel -> _state.update { it.copy(selectedAudioId = sel?.id) } } }
            }
            metadata.subtitleTracks?.let { group ->
                scope.launch {
                    group.candidates.collect { list ->
                        subtitleCandidates = list
                        _state.update { it.copy(subtitleTracks = list.map(SubtitleTrack::toMediaTrack)) }
                    }
                }
                scope.launch {
                    group.selected.collect { sel ->
                        _state.update { it.copy(selectedSubtitleId = externalSubtitleId ?: sel?.id) }
                    }
                }
            }
        }
        onInit()
    }

    /** Engine-specific setup, run once after the shared collectors are wired. */
    protected open fun onInit() {}

    /** Builds the engine's [VideoSurfaceRenderer]; called once, lazily. */
    protected abstract fun createVideoOutput(): VideoSurfaceRenderer

    override fun open(target: PlaybackTarget) {
        currentTarget = target
        externalSubtitleId = null
        onOpen(target)
        _state.value = PlayerState()
        load(target, extraFilesFor(target), playWhenReady = true, startPositionMillis = 0L)
    }

    /** Engine-specific bookkeeping when a target opens (e.g. reset an external subtitle). */
    protected open fun onOpen(target: PlaybackTarget) {}

    /** Extra files (e.g. a side-loaded subtitle) to attach when opening. */
    protected open fun extraFilesFor(target: PlaybackTarget): MediaExtraFiles = MediaExtraFiles.EMPTY

    /** The target of the current session, or `null` when nothing is loaded. */
    protected val openedTarget: PlaybackTarget? get() = currentTarget

    /** Opens [target] with [extraFiles] through the player's main dispatcher. */
    protected fun load(
        target: PlaybackTarget,
        extraFiles: MediaExtraFiles,
        playWhenReady: Boolean,
        startPositionMillis: Long,
    ) {
        onMain {
            runCatching {
                player.setMediaData(
                    UriMediaData(target.source, target.httpHeaders ?: emptyMap(), extraFiles),
                    playWhenReady = playWhenReady,
                    startPositionMillis = startPositionMillis,
                )
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Could not open the source.") }
            }
        }
    }

    override fun playOrPause() =
        onMain {
            if (player.state.value.playWhenReady) player.pause() else player.play()
        }

    override fun pause() = onMain { player.pause() }

    override fun seek(position: Duration) = onMain { player.seekTo(position.inWholeMilliseconds) }

    override fun seekBy(offset: Duration) = onMain { player.skip(offset.inWholeMilliseconds) }

    override fun selectAudioTrack(track: MediaTrack) =
        onMain {
            val candidate = audioCandidates.firstOrNull { it.id == track.id } ?: return@onMain
            player.features[MediaMetadata]?.audioTracks?.select(candidate)
        }

    /** Selects an embedded subtitle track, or turns subtitles off with `null`. */
    protected fun selectEmbeddedSubtitle(track: MediaTrack?) {
        onMain {
            if (track == null) {
                player.features[MediaMetadata]?.subtitleTracks?.select(null)
                return@onMain
            }
            val candidate = subtitleCandidates.firstOrNull { it.id == track.id } ?: return@onMain
            player.features[MediaMetadata]?.subtitleTracks?.select(candidate)
        }
    }

    /** Records (or clears) the external addon subtitle the UI should show as selected. */
    protected fun setExternalSubtitleId(id: String?) {
        externalSubtitleId = id
    }

    override fun stop() {
        currentTarget = null
        externalSubtitleId = null
        onStop()
        onMain { player.stopPlayback() }
        _state.value = PlayerState()
    }

    /** Engine-specific cleanup when playback stops. */
    protected open fun onStop() {}

    override fun dispose() {
        currentTarget = null
        externalSubtitleId = null
        onDispose()
        // `close()` is safe from any thread, which matters during `onDestroy`.
        runCatching { player.close() }
        scope.cancel()
        _state.value = PlayerState()
    }

    /** Engine-specific cleanup when the service is disposed. */
    protected open fun onDispose() {}

    /** Applies a transformation to the shared state, e.g. to report a volume change. */
    protected fun updateState(transform: (PlayerState) -> PlayerState) {
        _state.update(transform)
    }

    /** Every command must run on the player's main dispatcher (the UI thread). */
    protected fun onMain(block: suspend () -> Unit) {
        scope.launch(player.mainDispatcher) { block() }
    }
}

internal fun AudioTrack.toMediaTrack(): MediaTrack =
    MediaTrack(
        id = id,
        title = name ?: labels.firstOrNull()?.value,
        language = labels.firstOrNull()?.language,
    )

internal fun SubtitleTrack.toMediaTrack(): MediaTrack =
    MediaTrack(
        id = id,
        title = labels.firstOrNull()?.value,
        language = language,
    )
