package com.misgus.pengu.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.misgus.pengu.core.player.MediaTrack
import com.misgus.pengu.core.player.PlaybackTarget
import kotlinx.coroutines.Dispatchers
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.Subtitle as MediaSubtitle

/**
 * [PlayerService] backed by **mediamp's ExoPlayer (media3) backend**.
 *
 * mediamp resolves its all-in-one bundle to ExoPlayer on Android; the backend
 * embeds a `PlayerView` through `AndroidView`, so the Compose controls stack on
 * top of the video in the same window. The shared [MediampPlayerAdapter] holds
 * the contract logic; this class wires the ExoPlayer engine: its surface, the
 * volume on the native player (ExoPlayer exposes no mediamp
 * `AudioLevelController`) and the media3 workaround for external subtitles.
 */
class MediampPlayerService(
    context: Context,
) : MediampPlayerAdapter(ExoPlayerMediampPlayer(context, Dispatchers.Main.immediate)) {
    // ExoPlayer requires the constructing thread to be the UI thread; the
    // composition root builds this on `Activity.onCreate`.
    private val exoPlayer: ExoPlayerMediampPlayer get() = player as ExoPlayerMediampPlayer

    /** The external subtitle currently attached to the session, if any. */
    private var externalSubtitle: MediaSubtitle? = null

    override fun createVideoOutput(): VideoSurfaceRenderer =
        object : VideoSurfaceRenderer {
            @Composable
            override fun Draw(modifier: Modifier) {
                ExoPlayerMediampPlayerSurface(exoPlayer, modifier)
            }
        }

    override fun onOpen(target: PlaybackTarget) {
        externalSubtitle = null
    }

    override fun extraFilesFor(target: PlaybackTarget): MediaExtraFiles =
        externalSubtitle?.let { MediaExtraFiles(listOf(it)) } ?: MediaExtraFiles.EMPTY

    override fun setVolume(volume: Double) {
        val level = volume.coerceIn(0.0, 100.0)
        // The ExoPlayer backend does not expose mediamp's AudioLevelController, so
        // the volume goes straight to the native player.
        onMain { exoPlayer.impl.volume = (level / 100.0).toFloat() }
        updateState { it.copy(volume = level) }
    }

    override fun selectSubtitleTrack(track: MediaTrack?) {
        val uri = track?.uri
        if (track != null && !uri.isNullOrEmpty()) {
            // Workaround: media3 only accepts side-loaded subtitles at prepare
            // time, so reload the source with the file attached.
            val target = openedTarget ?: return
            val subtitle = MediaSubtitle(uri, subtitleMimeType(uri), track.language, track.title)
            externalSubtitle = subtitle
            setExternalSubtitleId(track.id)
            updateState { it.copy(selectedSubtitleId = track.id) }
            reload(target)
            return
        }
        if (track == null && externalSubtitle != null) {
            // Turning an external subtitle off also means dropping the side-loaded
            // file from the session.
            val target = openedTarget ?: return
            externalSubtitle = null
            setExternalSubtitleId(null)
            updateState { it.copy(selectedSubtitleId = null) }
            reload(target)
            return
        }
        setExternalSubtitleId(null)
        selectEmbeddedSubtitle(track)
    }

    override fun onStop() {
        externalSubtitle = null
    }

    override fun onDispose() {
        externalSubtitle = null
    }

    /** Reloads [target], keeping the current position and play intent. */
    private fun reload(target: PlaybackTarget) {
        val position = player.currentPositionMillis.value
        val playing = player.state.value.playWhenReady
        load(target, extraFilesFor(target), playWhenReady = playing, startPositionMillis = position)
    }
}

/** Best-effort MIME type for a side-loaded subtitle, inferred from its file extension. */
private fun subtitleMimeType(uri: String): String? =
    when (
        uri
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "")
            .lowercase()
    ) {
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        "srt" -> "application/x-subrip"
        else -> null
    }
