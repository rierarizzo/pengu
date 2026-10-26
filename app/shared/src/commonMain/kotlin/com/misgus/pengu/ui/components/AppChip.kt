package com.misgus.pengu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType

/**
 * A chip: a compact pill for a choice, a filter or a tag.
 *
 * The selected state is the amber accent fill, the same weight the app gives a
 * chosen control elsewhere, so a row of chips reads as "one of these" instead of
 * as a line of buttons. The padding lives here and not at the call site, so every
 * chip in the app has the same height and edge rhythm; it paints no ripple, the
 * fill and the border carry the state, like the poster tiles.
 */
@Composable
fun AppChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    role: Role = Role.Button,
    fontFamily: FontFamily? = null,
) {
    val interaction = rememberInteraction()
    val scheme = MaterialTheme.colorScheme
    val content =
        when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = AppAlpha.DISABLED_CONTENT)
            selected -> scheme.onPrimary
            interaction.active -> scheme.onSurface
            else -> scheme.onSurfaceVariant
        }
    Row(
        modifier =
            modifier
                .focusRing(interaction, cornerRadius = AppCorner.small)
                .heightIn(min = ChipHeight)
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) scheme.primary else Color.Transparent)
                .motionBorder(
                    color = {
                        when {
                            selected -> Color.Transparent
                            interaction.active -> scheme.onSurfaceVariant
                            else -> scheme.outlineVariant
                        }
                    },
                    width = AppSizes.borderWidth,
                    cornerRadius = AppCorner.small,
                ).clickableSurface(interaction, onClick, enabled = enabled, role = role, indication = null)
                .semantics { this.selected = selected }
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leadingIcon?.let { icon ->
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(AppIconSize.sm))
            Spacer(Modifier.width(AppSpacing.xs))
        }
        Text(
            text = text,
            style = if (fontFamily == null) AppType.label() else AppType.label().copy(fontFamily = fontFamily),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingIcon?.let { icon ->
            Spacer(Modifier.width(AppSpacing.xs))
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(AppIconSize.sm))
        }
    }
}

private val ChipHeight = 32.dp
