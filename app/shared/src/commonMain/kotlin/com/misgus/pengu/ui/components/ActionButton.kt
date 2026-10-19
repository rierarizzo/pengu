package com.misgus.pengu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScale
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.motionLayer
import com.misgus.pengu.ui.theme.motionTween

/**
 * Visual weight of an [ActionButton], from highest to lowest emphasis.
 *
 * A screen has one primary call to action: [Accent] where the action is the
 * hero of the screen (the billboards' "Stream Now"), otherwise [Filled]. Every
 * other action steps down through [Tonal], [Outline] and [Text]. Keeping the
 * weights in an enum (instead of booleans) means a new variant is one branch
 * here, not another parameter on every call site.
 */
enum class ActionButtonStyle {
    /** High-contrast inverse surface: the default primary call to action. */
    Filled,

    /** Amber brand fill: the primary call to action on a media billboard. */
    Accent,

    /** Translucent on-surface fill: a secondary action, readable over artwork. */
    Tonal,

    /** Transparent with an outline: a low-emphasis action. */
    Outline,

    /** Label only: the lowest emphasis, for an inline or tertiary action. */
    Text,
}

/**
 * The app's single call-to-action button, used for every labelled action.
 *
 * The enabled, disabled and content colours come from the scheme per [style],
 * never from the call site, and the disabled/loading pairs are derived here so a
 * busy or unavailable button reads as such. The content colour is left to the
 * button's own `LocalContentColor`, so a disabled label dims with its container
 * instead of staying at full strength; the focused control draws a ring so
 * keyboard and D-pad navigation stay visible; and the whole button steps down a
 * little while pressed, matching the poster and icon feedback elsewhere.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    style: ActionButtonStyle = ActionButtonStyle.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // The brand colour has one source: the scheme's primary roles. The filled
    // call to action is the one high-contrast (light) surface on the dark UI.
    val baseContainer =
        when (style) {
            ActionButtonStyle.Filled -> scheme.inverseSurface
            ActionButtonStyle.Accent -> scheme.primary
            ActionButtonStyle.Tonal -> scheme.onSurface.copy(alpha = AppAlpha.HERO_ACTION)
            ActionButtonStyle.Outline, ActionButtonStyle.Text -> Color.Transparent
        }
    val baseContent =
        when (style) {
            ActionButtonStyle.Filled -> scheme.inverseOnSurface
            ActionButtonStyle.Accent -> scheme.onPrimary
            ActionButtonStyle.Tonal, ActionButtonStyle.Outline, ActionButtonStyle.Text -> scheme.onSurface
        }
    // A selected low-emphasis button (season picker, filter) takes the brand
    // colour: an amber label over a faint amber fill, so the choice reads at a
    // glance without shouting like a call to action.
    val container =
        if (selected && style == ActionButtonStyle.Tonal) {
            scheme.primary.copy(alpha = AppAlpha.NAV_SELECTED)
        } else {
            baseContainer
        }
    val content =
        if (selected && (style == ActionButtonStyle.Tonal || style == ActionButtonStyle.Outline || style == ActionButtonStyle.Text)) {
            scheme.primary
        } else {
            baseContent
        }
    val disabledContainer =
        when (style) {
            ActionButtonStyle.Filled, ActionButtonStyle.Accent, ActionButtonStyle.Tonal ->
                scheme.onSurface.copy(alpha = AppAlpha.DISABLED_CONTAINER)
            ActionButtonStyle.Outline, ActionButtonStyle.Text -> Color.Transparent
        }
    val disabledContent = scheme.onSurface.copy(alpha = AppAlpha.DISABLED_CONTENT)
    // The amber ring disappears on the amber fill, so the featured button uses
    // the on-surface role for its focus ring instead.
    val focusRing = if (style == ActionButtonStyle.Accent) scheme.onSurface else scheme.primary
    val interaction = rememberInteraction()
    val pressScale by animateFloatAsState(
        targetValue = if (interaction.pressed) AppScale.PRESS else 1f,
        animationSpec = motionTween(AppMotion.FAST),
        label = "actionPressScale",
    )
    // A call to action carries a larger glyph than an inline action, so the icon
    // reads at the same visual weight as the label.
    val iconSize =
        if (style == ActionButtonStyle.Outline || style == ActionButtonStyle.Text) {
            AppIconSize.sm
        } else {
            AppIconSize.md
        }
    // The primary weights get a label one size up, so the billboard's call to
    // action reads above the secondary actions next to it.
    val labelStyle =
        if (style == ActionButtonStyle.Filled || style == ActionButtonStyle.Accent) {
            AppType.ctaLabel()
        } else {
            AppType.buttonLabel()
        }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = disabledContainer,
                disabledContentColor = disabledContent,
            ),
        border =
            if (style == ActionButtonStyle.Outline) {
                BorderStroke(width = AppSizes.borderWidth, color = scheme.outline)
            } else {
                null
            },
        // The app is flat: the container, outline and scrim carry the surface,
        // so no button raises a shadow over the artwork.
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        contentPadding =
            PaddingValues(
                // Low-emphasis buttons sit inline, so they keep a tighter
                // horizontal rhythm than a call to action.
                horizontal =
                    if (style == ActionButtonStyle.Outline || style == ActionButtonStyle.Text) {
                        AppSpacing.md
                    } else {
                        AppSpacing.lg
                    },
                vertical = AppSpacing.sm,
            ),
        interactionSource = interaction.source,
        modifier =
            modifier
                .focusRing(interaction, color = focusRing)
                .height(AppSizes.controlHeight)
                .motionLayer(scale = { pressScale }),
    ) {
        when {
            loading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = AppSizes.progressStroke,
                    color = LocalContentColor.current,
                )
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
        if (loading || icon != null) Spacer(Modifier.width(AppSpacing.sm))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle)
        if (trailingIcon != null) {
            Spacer(Modifier.width(AppSpacing.xs))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}
