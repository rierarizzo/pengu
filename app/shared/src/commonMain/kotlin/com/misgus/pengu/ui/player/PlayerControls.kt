package com.misgus.pengu.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_back
import com.misgus.pengu.app.shared.generated.resources.action_play
import com.misgus.pengu.app.shared.generated.resources.player_audio
import com.misgus.pengu.app.shared.generated.resources.player_back_10
import com.misgus.pengu.app.shared.generated.resources.player_forward_10
import com.misgus.pengu.app.shared.generated.resources.player_fullscreen
import com.misgus.pengu.app.shared.generated.resources.player_pause
import com.misgus.pengu.app.shared.generated.resources.player_subtitles
import com.misgus.pengu.app.shared.generated.resources.player_volume
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.AppSpacing
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
    val playing: Boolean,
    val position: Duration,
    val duration: Duration,
    val volume: Double,
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
    onToggleFullscreen: (() -> Unit)?,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onBack: () -> Unit,
) {
    val alpha by animateFloatAsState(if (state.visible) 1f else 0f, animationSpec = motionTween())
    Box(Modifier.fillMaxSize().alpha(alpha)) {
        TopBar(state.title, onBack, Modifier.align(Alignment.TopCenter))
        if (!state.playing) {
            AppIconButton(
                icon = FluentIcons.Play,
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
            onToggleFullscreen = onToggleFullscreen,
            onSubtitles = onSubtitles,
            onAudio = onAudio,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    AppScrim.playerTop,
                ).statusBarsPadding()
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = FluentIcons.ArrowBack,
            contentDescription = stringResource(Res.string.action_back),
            onClick = onBack,
            tint = AppColors.playerControl,
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.playerControl,
        )
    }
}

@Composable
private fun BottomBar(
    state: PlayerControlsState,
    onPlayPause: () -> Unit,
    onSeek: (Duration) -> Unit,
    onSeekBy: (Duration) -> Unit,
    onVolumeChanged: (Double) -> Unit,
    onToggleFullscreen: (() -> Unit)?,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMs = state.duration.inWholeMilliseconds
    val maxValue = if (totalMs <= 0) 1f else totalMs.toFloat()
    val value =
        state.position.inWholeMilliseconds
            .coerceIn(0, if (totalMs <= 0) 0 else totalMs)
            .toFloat()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    AppScrim.playerBottom,
                ).navigationBarsPadding()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
    ) {
        Slider(
            value = value,
            onValueChange = { onSeek(Duration.parse("${it.toLong()}ms")) },
            valueRange = 0f..maxValue,
            enabled = totalMs > 0,
            colors = SliderDefaults.colors(thumbColor = AppColors.playerControl, activeTrackColor = AppColors.playerControl),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                icon = if (state.playing) FluentIcons.Pause else FluentIcons.Play,
                contentDescription =
                    if (state.playing) {
                        stringResource(
                            Res.string.player_pause,
                        )
                    } else {
                        stringResource(Res.string.action_play)
                    },
                onClick = onPlayPause,
                tint = AppColors.playerControl,
            )
            AppIconButton(
                icon = FluentIcons.Replay,
                contentDescription = stringResource(Res.string.player_back_10),
                onClick = { onSeekBy(SEEK_BACK) },
                tint = AppColors.playerControl,
            )
            AppIconButton(
                icon = FluentIcons.Forward,
                contentDescription = stringResource(Res.string.player_forward_10),
                onClick = { onSeekBy(SEEK_FORWARD) },
                tint = AppColors.playerControl,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                "${format(state.position)} / ${format(state.duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.playerControl,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                FluentIcons.Volume,
                contentDescription = stringResource(Res.string.player_volume),
                tint = AppColors.playerControl,
                modifier = Modifier.size(AppIconSize.md),
            )
            Box(Modifier.width(110.dp)) {
                Slider(
                    value = state.volume.coerceIn(0.0, 100.0).toFloat(),
                    onValueChange = { onVolumeChanged(it.toDouble()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = AppColors.playerControl, activeTrackColor = AppColors.playerControl),
                )
            }
            AppIconButton(
                icon = FluentIcons.Subtitles,
                contentDescription = stringResource(Res.string.player_subtitles),
                onClick = onSubtitles,
                tint = AppColors.playerControl,
            )
            AppIconButton(
                icon = FluentIcons.Audiotrack,
                contentDescription = stringResource(Res.string.player_audio),
                onClick = onAudio,
                tint = AppColors.playerControl,
            )
            onToggleFullscreen?.let { toggle ->
                AppIconButton(
                    icon = FluentIcons.Fullscreen,
                    contentDescription = stringResource(Res.string.player_fullscreen),
                    onClick = toggle,
                    tint = AppColors.playerControl,
                )
            }
        }
    }
}

private fun format(value: Duration): String {
    val total = value.inWholeSeconds
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}
