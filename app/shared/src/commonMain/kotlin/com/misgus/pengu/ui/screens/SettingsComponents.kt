package com.misgus.pengu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.interactionHighlight
import com.misgus.pengu.ui.components.mirrorInRtl
import com.misgus.pengu.ui.components.navContentColor
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.platform.isDesktop
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/*
 * Building blocks of the settings screen: the sidebar and index that pick a
 * category, the page header, and the list rows the pages are made of.
 *
 * A page is a stack of [SettingsPanel]s, each a filled, bordered box of rows
 * divided by inset hairlines. The controls are not stock Material: the switch
 * carries the accent, a whole toggle row is the target, short choices are chips
 * and a longer list opens a popover on desktop and a bottom sheet on touch, so a
 * setting reads like part of the app instead of a form.
 */

/** Small caps-style label above a cluster of categories. */
@Composable
fun SettingsGroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = AppType.label().copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AppSpacing.md, top = AppSpacing.md, bottom = AppSpacing.xs),
    )
}

/**
 * Categories at the side of a wide window.
 *
 * The selected entry keeps a filled surface, an accent icon tile and a heavier
 * label; every entry answers hover, focus and press with the shared state layer,
 * and announces itself as a selected tab to accessibility services.
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
    val iconColor = navContentColor(selected, interaction)
    val textColor = navContentColor(selected, interaction, selectedColor = MaterialTheme.colorScheme.onSurface)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.sm)
                .clip(MaterialTheme.shapes.medium)
                .focusRing(interaction)
                .clickableSurface(interaction, onClick, role = Role.Tab, indication = null)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(AppIconSize.md),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Text(
            stringResource(category.label),
            style = AppType.itemTitle().copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = textColor,
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
                .focusRing(interaction)
                .clickableSurface(interaction, onClick, indication = null)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = navContentColor(selected = false, interaction, idleColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(AppIconSize.md),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(category.label), style = AppType.itemTitle().copy(fontWeight = FontWeight.Medium))
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
            imageVector = MaterialIcons.ChevronRight,
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = AppAlpha.SELECTED_SURFACE)),
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
        Text(
            title,
            style = AppType.cardTitle().copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.sectionHeading(),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Filled, bordered box that frames a run of rows. */
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
                .background(MaterialTheme.colorScheme.background)
                .border(AppSizes.borderWidth, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        content = content,
    )
}

/**
 * Hairline between two rows of a [SettingsPanel].
 *
 * It starts at [startInset] instead of the panel edge, so it lines up with the
 * row copy rather than cutting the panel in half.
 */
@Composable
fun SettingsDivider(startInset: Dp = AppSpacing.lg) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset),
        thickness = AppSizes.borderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A titled row with an optional description and a trailing control.
 *
 * It is the base every other row builds on, so a toggle, a select and a plain
 * value all keep the same padding and title hierarchy. When [onClick] is set the
 * whole row is the target, not just the control on its end.
 */
@Composable
fun SettingsRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        Column(
            modifier = Modifier.weight(1f).padding(end = if (trailing != null) AppSpacing.md else 0.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Text(title, style = AppType.itemTitle().copy(fontWeight = FontWeight.Medium))
            if (description != null) {
                Text(
                    description,
                    style = AppType.bodySecondary(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
    if (onClick != null) {
        val interaction = rememberInteraction()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(interactionHighlight(interaction))
                    .focusRing(interaction)
                    .clickableSurface(interaction, onClick)
                    .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Row that flips a boolean.
 *
 * The row and the switch both toggle it; the switch only carries the state, so
 * tapping anywhere on the row works and the control keeps its accent colours.
 */
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
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = settingsSwitchColors(),
            )
        },
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
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppType.itemTitle().copy(fontWeight = FontWeight.Medium))
            Text(
                description,
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
        SettingsSelect(
            title = title,
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Row that picks one value from a short list, as chips.
 *
 * A binary or three-way choice is faster to hit than a menu, and the chips keep
 * the options visible instead of hiding them behind a tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SettingsChipRow(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppType.itemTitle().copy(fontWeight = FontWeight.Medium))
            Text(
                description,
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            options.forEach { option ->
                SettingsChip(
                    label = label(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun SettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val fill = if (selected) AppColors.surfaceSelected else Color.Transparent
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(interactionHighlight(interaction, fill))
                .border(AppSizes.borderWidth, borderColor, MaterialTheme.shapes.small)
                .focusRing(interaction)
                .clickableSurface(interaction, onClick, role = Role.RadioButton)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppType.buttonLabel(),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Dropdown that shows the current value and opens the rest.
 *
 * Desktop opens a popover next to the trigger; a touch device opens a bottom
 * sheet, where the options sit in thumb reach and each row is a full-width
 * target. The list is the same in both, so the setting is defined once.
 */
@Composable
fun <T> SettingsSelect(
    title: String,
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
                    .background(AppColors.surfaceSelected)
                    .border(AppSizes.borderWidth, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .focusRing(interaction)
                    .clickableSurface(interaction, { expanded = true }, role = Role.DropdownList)
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
                imageVector = MaterialIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppIconSize.sm),
            )
        }
        if (isDesktop) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = AppColors.surfaceSelected,
                shape = MaterialTheme.shapes.medium,
            ) {
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
                                    imageVector = MaterialIcons.Check,
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
    if (!isDesktop && expanded) {
        SettingsSelectSheet(
            title = title,
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelect,
            onDismiss = { expanded = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsSelectSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    // The sheet is only composed while expanded, so it starts hidden and lets
    // ModalBottomSheet animate it in; skipping the partial state keeps the short
    // option list on a single, fully expanded stop.
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surfaceSelected,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppSpacing.md)) {
            Text(
                title,
                style = AppType.sectionTitle(),
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md).sectionHeading(),
            )
            SettingsDivider(startInset = 0.dp)
            options.forEach { option ->
                SettingsRow(
                    title = label(option),
                    onClick = {
                        onSelect(option)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    trailing = {
                        if (option == selected) {
                            Icon(
                                imageVector = MaterialIcons.Check,
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

/**
 * Colours of a settings switch.
 *
 * The accent fills the track and the thumb stays dark, so a switch reads as part
 * of the app instead of a stock Material control.
 */
@Composable
private fun settingsSwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
    )
