package com.misgus.pengu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScale
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.motionLayer
import com.misgus.pengu.ui.theme.motionTween

/**
 * Icon-only action: a header control (back, refresh, close, see all), a
 * player control, or a toggle such as the detail screen's save/rate/watched.
 *
 * Material's `IconButton` paints a state-layer box around the glyph, which reads
 * as a second square on an already square control. This one keeps the app's 48dp
 * target but lets the glyph carry the state instead: it brightens on hover or
 * focus, turns amber when [selected], and steps down while pressed, with no
 * background fill or ripple. A keyboard or D-pad focus still draws the shared
 * focus ring (a mouse click does not), so a remote has a visible target. It dims
 * when disabled or loading, and can sit on a media [scrim] so it stays legible
 * over any image. One component covers every icon case instead of each screen
 * rolling its own.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    loading: Boolean = false,
    scrim: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = AppIconSize.md,
    size: Dp = AppSizes.minTouchTarget,
    shape: Shape = MaterialTheme.shapes.small,
    focusRingCorner: Dp = AppCorner.small,
    iconModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    val contentTint =
        when {
            !enabled -> tint.copy(alpha = AppAlpha.DISABLED_CONTENT)
            selected -> scheme.primary
            // The control paints no state-layer box, so the brighter glyph is
            // the only hover/focus cue.
            interaction.active -> scheme.onSurface
            else -> tint
        }
    // While the real state is unknown, dim the glyph instead of showing it as
    // "off": it is not that it is not saved, it is that we do not know yet.
    val loadingAlpha by animateFloatAsState(
        targetValue = if (loading) AppAlpha.ACTION_LOADING else 1f,
        animationSpec = motionTween(AppMotion.FAST),
        label = "iconButtonAlpha",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (interaction.pressed) AppScale.PRESS else 1f,
        animationSpec = motionTween(AppMotion.FAST),
        label = "iconButtonPressScale",
    )
    // Only the media scrim fills the control; selected and interaction change
    // the glyph, not the surface.
    val baseBackground = if (scrim) AppColors.mediaScrim else Color.Transparent
    Box(
        modifier =
            modifier
                .focusRing(interaction, cornerRadius = focusRingCorner)
                .size(size)
                .motionLayer(scale = { pressScale }, alpha = { loadingAlpha })
                .clip(shape)
                .background(baseBackground)
                .clickableSurface(interaction, onClick, enabled = enabled && !loading, indication = null),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentTint,
            modifier = iconModifier.size(iconSize),
        )
    }
}
