package com.misgus.pengu.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_back
import com.misgus.pengu.app.shared.generated.resources.action_play
import com.misgus.pengu.app.shared.generated.resources.player_audio
import com.misgus.pengu.app.shared.generated.resources.player_back_10
import com.misgus.pengu.app.shared.generated.resources.player_forward_10
import com.misgus.pengu.app.shared.generated.resources.player_fullscreen
import com.misgus.pengu.app.shared.generated.resources.player_mute
import com.misgus.pengu.app.shared.generated.resources.player_pause
import com.misgus.pengu.app.shared.generated.resources.player_seek
import com.misgus.pengu.app.shared.generated.resources.player_subtitles
import com.misgus.pengu.app.shared.generated.resources.player_unmute
import com.misgus.pengu.app.shared.generated.resources.player_volume
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.motionTween
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * Read-only values the player controls render.
 *
 * Grouped so the composable takes one state plus callbacks instead of a long
 * list of positional values.
 */
data class PlayerControlsState(
    val title: String,
    /** Episode code and name under the title, or `null` for a movie. */
    val subtitle: String?,
    val playing: Boolean,
    val position: Duration,
    val duration: Duration,
    val volume: Double,
    val subtitlesOn: Boolean,
    /** Engine playback rate, `1.0` at normal speed. */
    val playbackSpeed: Float = 1f,
    val visible: Boolean,
)

/**
 * Overlay controls for the player: back, title, seek bar, play/pause, time,
 * volume, subtitles, audio and fullscreen.
 *
 * Pure: it takes plain values and callbacks, so it can be rendered and tested
 * without a native player.
 */
@Composable
fun PlayerControls(
    state: PlayerControlsState,
    onPlayPause: () -> Unit,
    onSeek: (Duration) -> Unit,
    onSeekBy: (Duration) -> Unit,
    onVolumeChanged: (Double) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: (() -> Unit)?,
    onCycleSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Composing the controls only while they are on screen keeps a hidden
        // overlay from swallowing clicks on the invisible seek bar or buttons.
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn(motionTween()),
            exit = fadeOut(motionTween()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopBar(state.title, state.subtitle, onBack, Modifier.align(Alignment.TopCenter))
                if (!state.playing) {
                    AppIconButton(
                        icon = MaterialIcons.Play,
                        contentDescription = stringResource(Res.string.action_play),
                        onClick = onPlayPause,
                        tint = AppColors.playerControl,
                        size = 64.dp,
                        iconSize = 40.dp,
                        shape = CircleShape,
                        focusRingCorner = 32.dp,
                        scrim = true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                BottomBar(
                    state = state,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSeekBy = onSeekBy,
                    onVolumeChanged = onVolumeChanged,
                    onToggleMute = onToggleMute,
                    onToggleFullscreen = onToggleFullscreen,
                    onCycleSpeed = onCycleSpeed,
                    onSubtitles = onSubtitles,
                    onAudio = onAudio,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Back control and the now-playing copy: the title on the first line and the
 * episode code with its name on the second, so a long show title never pushes
 * the episode out of view.
 */
@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(AppScrim.playerTop)
                .statusBarsPadding()
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = MaterialIcons.ArrowBack,
            contentDescription = stringResource(Res.string.action_back),
            onClick = onBack,
            tint = AppColors.playerControl,
            iconSize = PLAYER_ICON_SIZE,
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppType.playerTitle(),
                color = AppColors.playerControl,
            )
            subtitle?.let {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppType.caption(),
                    color = AppColors.playerControl.copy(alpha = AppAlpha.PLAYER_TEXT_SECONDARY),
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    state: PlayerControlsState,
    onPlayPause: () -> Unit,
    onSeek: (Duration) -> Unit,
    onSeekBy: (Duration) -> Unit,
    onVolumeChanged: (Double) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: (() -> Unit)?,
    onCycleSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // The scrim starts above the bar, so the fade begins over the video
                // instead of reading as a dark strip glued under the controls.
                .background(AppScrim.playerBottom)
                .padding(top = AppSpacing.lg)
                .navigationBarsPadding(),
    ) {
        PlayerSeekBar(
            position = state.position,
            duration = state.duration,
            contentDescription = stringResource(Res.string.player_seek),
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                icon = if (state.playing) MaterialIcons.Pause else MaterialIcons.Play,
                contentDescription =
                    if (state.playing) {
                        stringResource(Res.string.player_pause)
                    } else {
                        stringResource(Res.string.action_play)
                    },
                onClick = onPlayPause,
                tint = AppColors.playerControl,
                iconSize = PLAYER_ICON_SIZE,
            )
            AppIconButton(
                icon = MaterialIcons.Replay10,
                contentDescription = stringResource(Res.string.player_back_10),
                onClick = { onSeekBy(SEEK_BACK) },
                tint = AppColors.playerControl,
                iconSize = PLAYER_ICON_SIZE,
            )
            AppIconButton(
                icon = MaterialIcons.Forward10,
                contentDescription = stringResource(Res.string.player_forward_10),
                onClick = { onSeekBy(SEEK_FORWARD) },
                tint = AppColors.playerControl,
                iconSize = PLAYER_ICON_SIZE,
            )
            VolumeControl(
                volume = state.volume,
                onVolumeChanged = onVolumeChanged,
                onToggleMute = onToggleMute,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                "${formatPlayerTime(state.position)} / ${formatPlayerTime(state.duration)}",
                style = AppType.label(),
                color = AppColors.playerControl.copy(alpha = AppAlpha.PLAYER_TEXT_SECONDARY),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            ActionButton(
                text = formatSpeed(state.playbackSpeed),
                onClick = onCycleSpeed,
                style = ActionButtonStyle.Text,
            )
            AppIconButton(
                icon = MaterialIcons.Subtitles,
                contentDescription = stringResource(Res.string.player_subtitles),
                onClick = onSubtitles,
                selected = state.subtitlesOn,
                tint = AppColors.playerControl,
                iconSize = PLAYER_ICON_SIZE,
            )
            AppIconButton(
                icon = MaterialIcons.GraphicEq,
                contentDescription = stringResource(Res.string.player_audio),
                onClick = onAudio,
                tint = AppColors.playerControl,
                iconSize = PLAYER_ICON_SIZE,
            )
            onToggleFullscreen?.let { toggle ->
                AppIconButton(
                    icon = MaterialIcons.Fullscreen,
                    contentDescription = stringResource(Res.string.player_fullscreen),
                    onClick = toggle,
                    tint = AppColors.playerControl,
                    iconSize = PLAYER_ICON_SIZE,
                )
            }
        }
    }
}

/**
 * Speaker control that doubles as the mute toggle.
 *
 * The glyph follows the level, and the slider expands only while the pointer is
 * over the group, so the row keeps the reference's single-icon rhythm. The
 * volume is still reachable from the keyboard (up/down) and from `M`.
 */
@Composable
private fun VolumeControl(
    volume: Double,
    onVolumeChanged: (Double) -> Unit,
    onToggleMute: () -> Unit,
) {
    val interaction = rememberInteraction()
    val muted = volume <= 0.0
    val volumeDescription = stringResource(Res.string.player_volume)
    Row(
        modifier = Modifier.hoverable(interaction.source),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon =
                when {
                    muted -> MaterialIcons.VolumeMute
                    volume < 50.0 -> MaterialIcons.VolumeDown
                    else -> MaterialIcons.Volume
                },
            contentDescription = stringResource(if (muted) Res.string.player_unmute else Res.string.player_mute),
            onClick = onToggleMute,
            tint = AppColors.playerControl,
            iconSize = PLAYER_ICON_SIZE,
        )
        AnimatedVisibility(
            visible = interaction.hovered,
            enter = fadeIn(motionTween(AppMotion.FAST)) + expandHorizontally(motionTween(AppMotion.FAST)),
            exit = fadeOut(motionTween(AppMotion.FAST)) + shrinkHorizontally(motionTween(AppMotion.FAST)),
        ) {
            Slider(
                value = volume.coerceIn(0.0, 100.0).toFloat(),
                onValueChange = { onVolumeChanged(it.toDouble()) },
                valueRange = 0f..100f,
                modifier =
                    Modifier
                        .width(VOLUME_SLIDER_WIDTH)
                        .semantics { contentDescription = volumeDescription },
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = AppColors.playerControl.copy(alpha = AppAlpha.PLAYER_TRACK),
                    ),
            )
        }
    }
}

/** Timecode shared by the control row and the seek preview. */
internal fun formatPlayerTime(value: Duration): String {
    val total = value.inWholeSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    val secondsText = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secondsText"
    } else {
        "$minutes:$secondsText"
    }
}

/** Rate as the speed control reads it: `1×`, `1.25×`. */
internal fun formatSpeed(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

private val VOLUME_SLIDER_WIDTH = 96.dp

/** Player glyphs read one step above the in-row default, over the video. */
private val PLAYER_ICON_SIZE = AppIconSize.lg
