package com.misgus.pengu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * toggles, with a `Role.Switch` and the Material ripple) but draws a compact
 * 40x22 track with a 16dp thumb, so a settings row and an addon card read at the
 * same visual weight as their other controls.
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
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motionTween(AppMotion.FAST),
        label = "switchProgress",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.outlineVariant,
        animationSpec = motionTween(AppMotion.FAST),
        label = "switchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = motionTween(AppMotion.FAST),
        label = "switchThumb",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else scheme.outline,
        animationSpec = motionTween(AppMotion.FAST),
        label = "switchBorder",
    )
    val trackShape = RoundedCornerShape(AppSizes.switchTrackHeight / 2f)
    val travel =
        AppSizes.switchTrackWidth -
            AppSizes.switchThumbSize -
            AppSizes.switchThumbPadding * 2f

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
                    indication = ripple(),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AppSizes.switchTrackWidth, AppSizes.switchTrackHeight)
                    .clip(trackShape)
                    .background(trackColor)
                    .border(AppSizes.borderWidth, borderColor, trackShape)
                    .focusRing(
                        state = interaction,
                        color = if (checked) scheme.onSurface else scheme.primary,
                        cornerRadius = AppSizes.switchTrackHeight / 2f,
                    ).alpha(if (enabled) 1f else AppAlpha.DISABLED_CONTENT),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = AppSizes.switchThumbPadding + travel * progress)
                        .size(AppSizes.switchThumbSize)
                        .background(thumbColor, CircleShape),
            )
        }
    }
}
