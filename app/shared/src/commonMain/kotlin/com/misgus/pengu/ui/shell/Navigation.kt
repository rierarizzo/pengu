package com.misgus.pengu.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.ui.components.ProfileAvatar
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing

/**
 * Vertical navigation rail for wide windows, with the profile and section items
 * centered in the window.
 *
 * It floats over the content, which reserves the rail's width, so showing or
 * hiding it never resizes the content viewport. The items are icon-only — at
 * [AppSizes.railWidth] the icons alone stay legible and the rail stays narrow —
 * and the container paints the page background, so the rail blends into the
 * window instead of reading as a separate panel.
 */
@Composable
fun LeftNav(
    selected: AppSection,
    onSelected: (AppSection) -> Unit,
    activeProfile: BackendProfile? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(AppSizes.railWidth)
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            ProfileAvatar(
                profile = activeProfile,
                size = 32.dp,
                initialStyle = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            AppSection.navigation.forEach { section ->
                SectionNavItem(
                    section = section,
                    selected = section == selected,
                    onTap = { onSelected(section) },
                    modifier = Modifier.size(AppSizes.minTouchTarget),
                )
            }
        }
    }
}

/**
 * Icon-only bottom navigation for compact windows.
 *
 * A label bar does not fit a phone, so the items are icon-only and the bar stands
 * [AppSizes.bottomBarHeight] tall instead of Material's 80dp label height. It
 * paints the page background with a hairline on its top edge, so it reads as
 * chrome rather than a separate panel, and the system-bar insets live on the
 * container so the background reaches the window edge. Like the rail, the glyph
 * carries the state (amber when selected).
 */
@Composable
fun BottomNav(
    selected: AppSection,
    onSelected: (AppSection) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                // The bar sits behind the gesture / navigation area, so it takes
                // that inset itself. Compact windows are portrait phones, where
                // the only cutout is at the top; the horizontal inset keeps a
                // landscape edge cutout clear without padding the top.
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppSizes.bottomBarHeight)
                    .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSection.navigation.forEach { section ->
                SectionNavItem(
                    section = section,
                    selected = section == selected,
                    onTap = { onSelected(section) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        // The bar shares the page colour, so without this hairline it and the
        // content above have no visible edge.
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(AppSizes.borderWidth)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * One section item, shared by the rail and the bottom bar.
 *
 * The glyph carries the state — amber when selected, brighter on hover, focus or
 * press — with no state-layer box, so both navigations read as bare icons. Press
 * joins the cue so a touch, which has no hover, still answers.
 */
@Composable
private fun SectionNavItem(
    section: AppSection,
    selected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberInteraction()
    val contentColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            interaction.active || interaction.pressed -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            modifier
                .focusRing(interaction)
                .clip(MaterialTheme.shapes.small)
                .clickableSurface(interaction, onTap, role = Role.Tab, indication = null)
                .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = section.label,
            tint = contentColor,
            modifier = Modifier.size(AppIconSize.md),
        )
    }
}
