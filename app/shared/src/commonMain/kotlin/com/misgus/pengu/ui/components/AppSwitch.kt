package com.misgus.pengu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.motionTween

/**
 * The app's single switch.
 *
 * Material's switch is 52x32, which reads as a large icon next to a 24dp glyph.
 * This one keeps the same behaviour and accessibility (the whole 48dp target
 * toggles, with a `Role.Switch`) but draws a compact 40x22 track with a 16dp
 * thumb, so a settings row and an addon card read at the same visual weight as
 * their other controls.
 *
 * Everything is painted by one [Canvas]: no nested background, clip, offset or
 * focus-ring layers. A card that is reordered therefore moves a single draw node
 * instead of a stack of layers, which is what used to leave the switch stale
 * until the next hover.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    val dim = if (enabled) 1f else AppAlpha.DISABLED_CONTENT
    val focused = interaction.focused && LocalInputModeManager.current.inputMode != InputMode.Touch
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motionTween(AppMotion.FAST),
        label = "switchProgress",
    )

    val trackColor = (if (checked) scheme.primary else scheme.outlineVariant).copy(alpha = dim)
    val thumbColor = (if (checked) scheme.onPrimary else scheme.onSurfaceVariant).copy(alpha = dim)
    val idleBorder = (if (checked) Color.Transparent else scheme.outline).copy(alpha = dim)
    val borderColor =
        if (focused) {
            (if (checked) scheme.onSurface else scheme.primary).copy(alpha = dim)
        } else {
            idleBorder
        }
    val borderWidth = if (focused) AppSizes.focusRing else AppSizes.borderWidth

    val descriptionModifier =
        if (description == null) {
            Modifier
        } else {
            Modifier.semantics { contentDescription = description }
        }

    Box(
        modifier =
            modifier
                .size(AppSizes.minTouchTarget)
                .then(descriptionModifier)
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interaction.source,
                    indication = null,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(AppSizes.switchTrackWidth, AppSizes.switchTrackHeight)) {
            val height = size.height
            val corner = CornerRadius(height / 2f)
            val stroke = borderWidth.toPx()
            drawRoundRect(
                color = trackColor,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, height - stroke),
                cornerRadius = CornerRadius((height - stroke) / 2f),
                style = Stroke(width = stroke),
            )
            val padding = AppSizes.switchThumbPadding.toPx()
            val thumbRadius = AppSizes.switchThumbSize.toPx() / 2f
            val travel = size.width - thumbRadius * 2f - padding * 2f
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(padding + thumbRadius + travel * progress, height / 2f),
            )
        }
    }
}
