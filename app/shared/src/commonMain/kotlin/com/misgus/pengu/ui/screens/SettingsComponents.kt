package com.misgus.pengu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.interactionHighlight
import com.misgus.pengu.ui.components.mirrorInRtl
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import org.jetbrains.compose.resources.stringResource

/*
 * Building blocks of the settings screen: the sidebar and index that pick a
 * category, the page header, and the list rows the pages are made of.
 *
 * A page is a stack of [SettingsPanel]s, each a bordered box of rows divided by
 * [SettingsDivider]. The rows keep the title/description/control rhythm the
 * screen shares with every other list, so a toggle, a select and a navigable row
 * read as one family instead of three controls that happened to land together.
 */

/** Small caps-style label above a cluster of categories. */
@Composable
fun SettingsGroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = AppType.label(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AppSpacing.md, top = AppSpacing.md, bottom = AppSpacing.xs),
    )
}

/**
 * Categories at the side of a wide window.
 *
 * The selected entry keeps a filled surface and the accent glyph; every entry
 * answers hover, focus and press with the shared state layer, and announces
 * itself as a selected tab to accessibility services.
 */
@Composable
fun SettingsSidebar(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsCategory.grouped.forEach { (group, categories) ->
            SettingsGroupLabel(stringResource(group.label))
            categories.forEach { category ->
                SettingsSidebarItem(
                    category = category,
                    selected = category == selected,
                    onClick = { onSelect(category) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSidebarItem(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val base = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.sm)
                .clip(MaterialTheme.shapes.medium)
                .background(interactionHighlight(interaction, base))
                .focusRing(interaction)
                .clickableSurface(interaction, onClick, role = Role.Tab)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppIconSize.md),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Text(
            stringResource(category.label),
            style = AppType.itemTitle(),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact-window index of every category, grouped like the sidebar. */
@Composable
fun SettingsIndex(
    onOpen: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsCategory.grouped.forEach { (group, categories) ->
            SettingsGroupLabel(stringResource(group.label))
            categories.forEach { category ->
                SettingsNavRow(category = category, onClick = { onOpen(category) })
            }
        }
    }
}

/** One drill-down entry of the compact index. */
@Composable
private fun SettingsNavRow(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.xxs)
                .clip(MaterialTheme.shapes.medium)
                .background(interactionHighlight(interaction))
                .focusRing(interaction)
                .clickableSurface(interaction, onClick)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppIconSize.md),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(category.label), style = AppType.itemTitle())
            Text(
                stringResource(category.subtitle),
                style = AppType.caption(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Icon(
            imageVector = FluentIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppIconSize.sm).mirrorInRtl(),
        )
    }
}

/** Title and description of the selected page, with its category glyph. */
@Composable
fun SettingsCategoryHeader(category: SettingsCategory) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(AppSizes.settingsBadge)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = AppAlpha.SETTINGS_BADGE)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppIconSize.md),
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column {
            Text(
                stringResource(category.label),
                style = AppType.sectionTitle(),
                modifier = Modifier.sectionHeading(),
            )
            Text(
                stringResource(category.subtitle),
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Heading that separates the groups of rows inside one page. */
@Composable
fun SettingsSubsection(
    title: String,
    subtitle: String? = null,
) {
    Column {
        Text(title, style = AppType.cardTitle(), modifier = Modifier.sectionHeading())
        if (subtitle != null) {
            Text(
                subtitle,
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bordered, rounded box that frames a run of rows. */
@Composable
fun SettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .border(AppSizes.borderWidth, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        content = content,
    )
}

/** Hairline between two rows of a [SettingsPanel]. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        thickness = AppSizes.borderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A titled row with an optional description and a trailing control.
 *
 * It is the base every other row builds on, so a toggle, a select and a plain
 * value all keep the same padding and title hierarchy.
 */
@Composable
fun SettingsRow(
    title: String,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.itemTitle())
            if (description != null) {
                Text(
                    description,
                    style = AppType.bodySecondary(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(AppSpacing.md))
            trailing()
        }
    }
}

/** Row that flips a boolean. */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        description = description,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** Row that picks one value from a list, with the control below the copy. */
@Composable
fun <T> SettingsSelectRow(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(AppSpacing.lg)) {
        Text(title, style = AppType.itemTitle())
        Text(
            description,
            style = AppType.bodySecondary(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.sm))
        SettingsSelect(
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Dropdown that shows the current value and opens the rest.
 *
 * It is the app's own control rather than Material's `ExposedDropdownMenuBox`:
 * a settings value is a single line and the menu is the only state, so the box,
 * the chevron and the menu items stay on the app's surfaces and row rhythm.
 */
@Composable
fun <T> SettingsSelect(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = rememberInteraction()
    Box(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSizes.controlHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(AppSizes.borderWidth, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .focusRing(interaction)
                    .clickableSurface(interaction, { expanded = true })
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label(selected),
                style = AppType.body(),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Icon(
                imageVector = FluentIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppIconSize.sm),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = AppType.body()) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(
                                imageVector = FluentIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppIconSize.sm),
                            )
                        }
                    },
                )
            }
        }
    }
}
