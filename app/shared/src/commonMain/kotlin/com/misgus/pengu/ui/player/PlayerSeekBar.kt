package com.misgus.pengu.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.motionTween
import kotlin.time.Duration

/**
 * Full-width seek bar of the player.
 *
 * A hairline track with an accent played portion, a thumb that appears while the
 * pointer is over it, and a time bubble that previews the target under the
 * pointer or the finger. Pressing seeks immediately and dragging keeps seeking,
 * so it behaves like the players it replaces instead of waiting for a release.
 * The track thickens on hover so the target is easier to read without taking
 * layout space while idle.
 */
@Composable
internal fun PlayerSeekBar(
    position: Duration,
    duration: Duration,
    contentDescription: String,
    onSeek: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMs = duration.inWholeMilliseconds
    val enabled = totalMs > 0
    var scrubbing by remember { mutableStateOf<Duration?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var hoverFraction by remember { mutableStateOf<Float?>(null) }

    val shownMs = (scrubbing ?: position).inWholeMilliseconds.coerceIn(0L, totalMs.coerceAtLeast(0L))
    val fraction = if (enabled) shownMs.toFloat() / totalMs else 0f
    val hovered = hoverFraction?.takeIf { enabled }
    val active = dragging || hovered != null
    val trackHeight by animateDpAsState(
        targetValue = if (active) HOVERED_TRACK_HEIGHT else IDLE_TRACK_HEIGHT,
        animationSpec = motionTween(),
        label = "playerSeekTrackHeight",
    )
    val trackColor = AppColors.playerControl.copy(alpha = AppAlpha.PLAYER_TRACK)
    val progressColor = MaterialTheme.colorScheme.primary
    val bubbleFraction = if (enabled) (if (dragging) fraction else hovered) else null

    val seekTo: (Float, Float) -> Unit = { x, width ->
        if (enabled && width > 0f) {
            val target = (x / width).coerceIn(0f, 1f)
            val destination = duration * target.toDouble()
            scrubbing = destination
            onSeek(destination)
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SEEK_BAR_HEIGHT)
                .semantics {
                    this.contentDescription = contentDescription
                    if (enabled) {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        setProgress { target ->
                            onSeek(duration * target.toDouble())
                            true
                        }
                    }
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    // Press, drag and tap in one gesture: the first down already
                    // seeks, so a click never waits for the release.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true
                        seekTo(down.position.x, size.width.toFloat())
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            seekTo(change.position.x, size.width.toFloat())
                            change.consume()
                        }
                        dragging = false
                        scrubbing = null
                    }
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    // Track the pointer without consuming it, so the bubble can
                    // follow the mouse while the gestures above handle the click.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val x =
                                event.changes
                                    .firstOrNull()
                                    ?.position
                                    ?.x
                            when (event.type) {
                                PointerEventType.Move, PointerEventType.Enter ->
                                    if (x != null) {
                                        val position = x / size.width
                                        hoverFraction = position.coerceIn(0f, 1f)
                                    }
                                PointerEventType.Exit -> hoverFraction = null
                                else -> Unit
                            }
                        }
                    }
                },
    ) {
        val barWidth = maxWidth
        bubbleFraction?.let { target ->
            Box(
                modifier =
                    Modifier
                        .offset(x = (barWidth * target - BUBBLE_WIDTH / 2).coerceIn(0.dp, (barWidth - BUBBLE_WIDTH).coerceAtLeast(0.dp)))
                        .width(BUBBLE_WIDTH)
                        .background(Color.Black.copy(alpha = BUBBLE_SCRIM), RoundedCornerShape(AppCorner.small))
                        .padding(vertical = AppSpacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatPlayerTime(duration * target.toDouble()),
                    style = AppType.label(),
                    color = AppColors.playerControl,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val track = trackHeight.toPx()
            val bottom = size.height - TRACK_BOTTOM_PADDING.toPx()
            val top = bottom - track
            val played = (size.width * fraction).coerceIn(0f, size.width)
            drawRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, track))
            drawRect(progressColor, topLeft = Offset(0f, top), size = Size(played, track))
            if (active) {
                drawCircle(
                    color = AppColors.playerControl,
                    radius = THUMB_RADIUS.toPx(),
                    center = Offset(played, top + track / 2),
                )
            }
        }
    }
}

/** Area the seek bar reserves, tall enough for the bubble above the track. */
private val SEEK_BAR_HEIGHT = 36.dp

private val IDLE_TRACK_HEIGHT = 3.dp

private val HOVERED_TRACK_HEIGHT = 5.dp

private val TRACK_BOTTOM_PADDING = 6.dp

private val THUMB_RADIUS = 5.dp

private val BUBBLE_WIDTH = 72.dp

private const val BUBBLE_SCRIM = 0.78f
