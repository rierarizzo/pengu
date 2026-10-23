package com.misgus.pengu.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppSizes

/**
 * Hover / focus / pressed state of a custom interactive surface.
 *
 * Compose has no shared "hovered" concept for plain [Modifier.clickable] targets,
 * so this is remembered once per element and reused by both the click handling
 * and the visual feedback.
 */
class InteractionState internal constructor(
    val source: MutableInteractionSource,
    val hovered: Boolean,
    val focused: Boolean,
    val pressed: Boolean,
) {
    /** True while the pointer is over the element or it holds keyboard focus. */
    val active: Boolean get() = hovered || focused
}

@Composable
fun rememberInteraction(): InteractionState {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    return InteractionState(source, hovered, focused, pressed)
}

/**
 * Click handling for a custom surface, with the Material ripple state layer.
 *
 * [role] defaults to [Role.Button], so a custom clickable surface is announced as
 * a button by accessibility services instead of an unlabelled node. Pass
 * `indication = null` for a surface that draws its own hover feedback (a poster
 * that scales on hover), so the Material hover state layer does not grey it.
 * [onClickLabel] names the action itself when the surface's text describes the
 * item rather than the gesture (a row that plays the stream it lists), so a
 * screen reader announces "Play" instead of reading the copy twice.
 */
@Composable
fun Modifier.clickableSurface(
    state: InteractionState,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    indication: Indication? = ripple(),
    onClickLabel: String? = null,
): Modifier =
    clickable(
        interactionSource = state.source,
        indication = indication,
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
        onClick = onClick,
    ).handCursor(enabled)

/**
 * [clickableSurface] with a long-press action.
 *
 * The poster actions menu uses it so the same surface keeps its click target on
 * touch, where there is no secondary button; desktop adds
 * [onSecondaryClick] on top.
 */
fun Modifier.combinedClickableSurface(
    state: InteractionState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    indication: Indication? = ripple(),
    onLongClickLabel: String? = null,
): Modifier =
    combinedClickable(
        interactionSource = state.source,
        indication = indication,
        enabled = enabled,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    ).handCursor(enabled)

/**
 * Calls [onClick] with the pointer position on a secondary-button press (a right
 * click on desktop).
 *
 * Compose's clickable surfaces only handle the primary button, so the poster
 * menu needs its own pointer loop. It runs on the [PointerEventPass.Initial] pass
 * and consumes the press, so the click target underneath never sees a right
 * click as a play/open request. The position is local to this surface, which is
 * also the anchor of the menu that opens there.
 */
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier =
    pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onClick(event.changes.first().position)
                }
            }
        }
    }

/**
 * Pointer cursor of an interactive surface.
 *
 * Compose has no shared cursor for clickable targets — neither `clickable` nor
 * any Material control sets one — so the hand is applied explicitly here and
 * the arrow stays over a disabled surface, which cannot be clicked.
 */
fun Modifier.handCursor(enabled: Boolean = true): Modifier = pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

/**
 * Content colour of a navigation item, following the rail's language.
 *
 * The selected item keeps [selectedColor]; a hovered, focused or pressed one
 * brightens to `onSurface`; the rest stay at [idleColor]. The app rail draws
 * icon-only items and a settings row draws an icon with copy, so the selected
 * colour is a parameter (the glyph uses the accent, the label the text colour)
 * while the hover step is the same everywhere.
 */
@Composable
fun navContentColor(
    selected: Boolean,
    state: InteractionState,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    idleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): Color =
    when {
        selected -> selectedColor
        state.active || state.pressed -> MaterialTheme.colorScheme.onSurface
        else -> idleColor
    }

/**
 * Background tint for [state], on top of [base].
 *
 * The state layer uses the `onSurface` role, per Material's state-layer spec, so
 * it adapts to the theme instead of a hard-coded white. It is blended over
 * [base] rather than replacing it, so a selected surface keeps its fill while it
 * is hovered or pressed.
 */
@Composable
fun interactionHighlight(
    state: InteractionState,
    base: Color = Color.Transparent,
): Color {
    val layer = MaterialTheme.colorScheme.onSurface
    val alpha =
        when {
            state.pressed -> AppAlpha.INTERACT_PRESSED
            state.focused -> AppAlpha.INTERACT_FOCUS
            state.hovered -> AppAlpha.INTERACT_HOVER
            else -> return base
        }
    return layer.copy(alpha = alpha).compositeOver(base)
}

/**
 * Rounded border drawn in the draw phase.
 *
 * Pass `{ state.value }` so an animated colour is read while drawing instead of
 * in composition: `Modifier.border` would recompose the card every frame while
 * the colour animates.
 */
fun Modifier.motionBorder(
    color: () -> Color,
    width: Dp,
    cornerRadius: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color(),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = width.toPx()),
        )
    }

/**
 * Keyboard/D-pad focus ring for a custom interactive surface.
 *
 * One definition so every focusable control draws the same ring, instead of
 * repeating `motionBorder({ if (focused) colour else transparent }, …)`. A
 * pointer click also grants focus, but a ring on a mouse click reads as noise,
 * so the ring is drawn only when the input mode is not touch: a remote or a
 * gamepad keeps a visible target, while the mouse keeps the clean, box-less
 * look.
 */
@Composable
fun Modifier.focusRing(
    state: InteractionState,
    color: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = AppCorner.small,
    width: Dp = AppSizes.focusRing,
): Modifier {
    val keyboardFocused = state.focused && LocalInputModeManager.current.inputMode != InputMode.Touch
    return motionBorder(
        color = { if (keyboardFocused) color else Color.Transparent },
        width = width,
        cornerRadius = cornerRadius,
    )
}
