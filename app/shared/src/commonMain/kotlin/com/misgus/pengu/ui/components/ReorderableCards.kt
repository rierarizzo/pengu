package com.misgus.pengu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.motionTween
import kotlin.math.roundToInt

/** The drag affordance one card receives from [ReorderableCardColumn]. */
class ReorderableCardScope internal constructor(
    /** True while this card is following the pointer. */
    val dragging: Boolean,
    /** Put this on the handle that starts a drag. */
    val dragHandle: Modifier,
)

private data class CardBounds(
    val top: Float,
    val height: Float,
) {
    val center: Float get() = top + height / 2f
}

private data class DragState(
    val key: Any,
    val fromIndex: Int,
    val targetIndex: Int,
    val offsetY: Float,
)

/**
 * A vertical list of cards whose order is changed by dragging the handle of one
 * of them.
 *
 * The component owns the gesture and the visuals of the gap the dragged card
 * opens; the caller owns the list and the persistence. [onMove] is called once,
 * when the card is dropped, with the original and the final index, so the caller
 * can update its state optimistically and write the order in one request.
 *
 * Only the modifier handed to [ReorderableCardScope.dragHandle] starts a drag.
 * The rest of the card stays inert, so a switch, a text field or a remove action
 * inside it keeps its own gesture and a scroll stays a scroll.
 *
 * The column does not scroll: it lays every card out, so it fits inside a page
 * that already scrolls (the settings screen). That is deliberate for short
 * lists; a long list wants a lazy list with its own reordering state.
 */
@Composable
fun <T> ReorderableCardColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemSpacing: Dp = AppSpacing.sm,
    cardShape: Shape = MaterialTheme.shapes.large,
    content: @Composable (item: T, index: Int, scope: ReorderableCardScope) -> Unit,
) {
    val bounds = remember { mutableMapOf<Any, CardBounds>() }
    var drag by remember { mutableStateOf<DragState?>(null) }
    val spacingPx = with(LocalDensity.current) { itemSpacing.toPx() }
    val haptics = LocalHapticFeedback.current

    fun startDrag(
        itemKey: Any,
        index: Int,
    ) {
        if (drag != null || !enabled) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        drag = DragState(itemKey, index, index, 0f)
    }

    fun dragBy(deltaY: Float) {
        val current = drag ?: return
        val offsetY = current.offsetY + deltaY
        val draggedCenter = bounds[current.key]?.let { it.center + offsetY }
        val target =
            if (draggedCenter == null) {
                current.targetIndex
            } else {
                targetIndex(
                    fromIndex = current.fromIndex,
                    draggedCenter = draggedCenter,
                    centers = items.map { item -> bounds[key(item)]?.center ?: 0f },
                )
            }
        drag = current.copy(offsetY = offsetY, targetIndex = target)
    }

    fun endDrag() {
        val current = drag ?: return
        drag = null
        if (current.targetIndex != current.fromIndex) onMove(current.fromIndex, current.targetIndex)
    }

    fun cancelDrag() {
        drag = null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val dragging = drag?.key == itemKey
                val from = drag?.fromIndex ?: index
                val target = drag?.targetIndex ?: index
                val draggedHeight = drag?.let { bounds[it.key]?.height } ?: 0f
                val shiftTarget =
                    when {
                        drag == null || dragging -> 0f
                        from < target && index in (from + 1)..target -> -(draggedHeight + spacingPx)
                        from > target && index in target until from -> draggedHeight + spacingPx
                        else -> 0f
                    }
                val shift by animateFloatAsState(
                    targetValue = shiftTarget,
                    animationSpec = motionTween(AppMotion.FAST),
                    label = "reorderShift",
                )
                ReorderableCard(
                    item = item,
                    index = index,
                    itemKey = itemKey,
                    dragging = dragging,
                    offsetY = if (dragging) drag?.offsetY ?: 0f else 0f,
                    // The parent applies the new order on drop, so the old shift
                    // must snap to zero in the same frame: animating it would
                    // move the card twice.
                    shift = if (drag == null) 0f else shift,
                    enabled = enabled,
                    cardShape = cardShape,
                    onBounds = { bounds[itemKey] = it },
                    onDragStart = { startDrag(itemKey, index) },
                    onDrag = ::dragBy,
                    onDragEnd = ::endDrag,
                    onDragCancel = ::cancelDrag,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun <T> ReorderableCard(
    item: T,
    index: Int,
    itemKey: Any,
    dragging: Boolean,
    offsetY: Float,
    shift: Float,
    enabled: Boolean,
    cardShape: Shape,
    onBounds: (CardBounds) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    content: @Composable (item: T, index: Int, scope: ReorderableCardScope) -> Unit,
) {
    val handleDrag =
        Modifier.pointerInput(itemKey, index, enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = { onDragStart() },
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount.y)
                },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }

    // The outer box measures the settled layout, so a translated card does not
    // feed its own dragged position back into the target calculation.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .zIndex(if (dragging) 1f else 0f)
                .onGloballyPositioned { coordinates ->
                    onBounds(
                        CardBounds(
                            top = coordinates.positionInParent().y,
                            height = coordinates.size.height.toFloat(),
                        ),
                    )
                },
    ) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, (shift + offsetY).roundToInt()) }
                    .then(if (dragging) Modifier.shadow(12.dp, cardShape) else Modifier)
                    .clip(cardShape)
                    .background(AppColors.surfaceCard),
        ) {
            content(
                item,
                index,
                ReorderableCardScope(dragging = dragging, dragHandle = handleDrag),
            )
        }
    }
}

/**
 * Final index of a card dragged to [draggedCenter], from the centers of the
 * cards in their current order.
 *
 * Moving down takes the last card whose center was passed; moving up takes the
 * first one. Kept pure so the gesture math is unit-tested.
 */
internal fun targetIndex(
    fromIndex: Int,
    draggedCenter: Float,
    centers: List<Float>,
): Int {
    if (fromIndex !in centers.indices) return fromIndex
    if (draggedCenter > centers[fromIndex]) {
        var target = fromIndex
        for (index in fromIndex + 1 until centers.size) {
            if (draggedCenter > centers[index]) target = index else break
        }
        return target
    }
    if (draggedCenter < centers[fromIndex]) {
        for (index in 0 until fromIndex) {
            if (draggedCenter < centers[index]) return index
        }
    }
    return fromIndex
}
