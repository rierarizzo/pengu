package com.misgus.pengu.ui.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** Which control a drag is moving once the pointer commits to an axis. */
internal enum class DragAxis { Seek, Volume }

/**
 * Surface gestures of the player: tap, double tap, hold and drag.
 *
 * A double tap on either half seeks, a hold plays at double speed, and a drag
 * picks its axis from the first movement — horizontal seeks, vertical moves the
 * volume — so one finger never does both. The caller applies the result: this
 * modifier only reports the fraction dragged, which keeps the arithmetic and the
 * engine out of the gesture layer.
 */
internal fun Modifier.playerSurfaceGestures(
    enabled: Boolean,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onDrag: (axis: DragAxis, fraction: Float, done: Boolean) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .pointerInput(Unit) {
                detectTapGestures(
                    // The hold ends on release, including a cancel, so double speed
                    // never outlives the finger.
                    onPress = {
                        tryAwaitRelease()
                        onHoldEnd()
                    },
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                    onLongPress = { onHoldStart() },
                )
            }.pointerInput(Unit) {
                var total = Offset.Zero
                var axis: DragAxis? = null
                detectDragGestures(
                    onDragStart = {
                        total = Offset.Zero
                        axis = null
                    },
                    onDragEnd = {
                        axis?.let { onDrag(it, it.fraction(total, size), true) }
                        axis = null
                    },
                    onDragCancel = { axis = null },
                ) { change, amount ->
                    change.consume()
                    total += amount
                    // The axis is chosen once, from the first movement past the
                    // slop, so a slightly diagonal drag does not flip mid-gesture.
                    val picked =
                        axis ?: when {
                            abs(total.x) < DRAG_SLOP_PX && abs(total.y) < DRAG_SLOP_PX -> null
                            abs(total.x) >= abs(total.y) -> DragAxis.Seek
                            else -> DragAxis.Volume
                        }
                    axis = picked
                    picked?.let { onDrag(it, it.fraction(total, size), false) }
                }
            }
    }

private fun DragAxis.fraction(
    total: Offset,
    size: IntSize,
): Float {
    val span = if (this == DragAxis.Seek) size.width else size.height
    if (span <= 0) return 0f
    val moved = if (this == DragAxis.Seek) total.x else -total.y
    return moved / span
}

private const val DRAG_SLOP_PX = 24f
