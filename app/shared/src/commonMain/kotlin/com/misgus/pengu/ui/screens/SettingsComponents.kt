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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.misgus.pengu.ui.components.AppChip
import com.misgus.pengu.ui.components.AppSwitch
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.handCursor
import com.misgus.pengu.ui.components.interactionHighlight
import com.misgus.pengu.ui.components.mirrorInRtl
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.platform.isDesktop
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppBottomSheetShape
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
 * The categories are a stack of filled cards: the compact index pairs each label
 * with a chevron, and the wide sidebar keeps the groups and marks the open page.
 * A page is a stack of [SettingsPanel]s, each a filled, borderless card of rows
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
 * They keep the groups of the pages themselves; each entry is a
 * [SettingsNavCard] and the open page is the selected one.
 */
@Composable
fun SettingsSidebar(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SettingsCategory.grouped.forEach { (group, categories) ->
            SettingsGroupLabel(stringResource(group.label))
            categories.forEach { category ->
                SettingsNavCard(
                    category = category,
                    onClick = { onSelect(category) },
                    selected = category == selected,
                )
            }
        }
    }
}

/** Compact-window index: the same groups and categories as the sidebar. */
@Composable
fun SettingsIndex(
    onOpen: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SettingsCategory.grouped.forEach { (group, categories) ->
            SettingsGroupLabel(stringResource(group.label))
            categories.forEach { category ->
                SettingsNavCard(category = category, onClick = { onOpen(category) })
            }
        }
    }
}

/**
 * One category as a filled card.
 *
 * On a compact window it is the index entry: a semibold label and a chevron.
 * With [selected] set it is the wide sidebar's item instead, where the open page
 * takes the `surfaceSelected` fill and the accent label in place of the chevron.
 * Hover, focus and press blend the shared state layer over the fill, so the card
 * keeps its own colour while it answers the pointer.
 */
@Composable
fun SettingsNavCard(
    category: SettingsCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    val interaction = rememberInteraction()
    val active = selected == true
    val card = AppColors.surfaceCard
    val fill =
        if (active) {
            MaterialTheme.colorScheme.primary
                .copy(alpha = AppAlpha.SELECTED_CARD)
                .compositeOver(card)
        } else {
            card
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(interactionHighlight(interaction, fill))
                .focusRing(interaction)
                .clickableSurface(interaction, onClick, role = if (selected == null) Role.Button else Role.Tab)
                .padding(AppSizes.cardPadding)
                .semantics { selected?.let { this.selected = it } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(category.label),
            style = AppType.itemTitle().copy(fontWeight = FontWeight.SemiBold),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected == null) {
            Spacer(Modifier.width(AppSpacing.sm))
            Icon(
                imageVector = MaterialIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppIconSize.md).mirrorInRtl(),
            )
        }
    }
}

/** Title and description of the selected page. */
@Composable
fun SettingsCategoryHeader(category: SettingsCategory) {
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

/** Bold label that separates the cards of one page. */
@Composable
fun SettingsSubsection(
    title: String,
    subtitle: String? = null,
) {
    Column {
        Text(
            title,
            style = AppType.bodySecondary().copy(fontWeight = FontWeight.SemiBold),
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

/**
 * Card that frames a run of rows.
 *
 * It shares the fill, radius and borderless edge of [SettingsNavCard], so the
 * index and the pages are made of the same surface.
 */
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
                .background(AppColors.surfaceCard),
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
fun SettingsDivider(startInset: Dp = AppSizes.cardPadding) {
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
            Text(title, style = AppType.itemTitle().copy(fontWeight = FontWeight.SemiBold))
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
                    .padding(AppSizes.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSizes.cardPadding),
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
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                description = title,
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
    Column(Modifier.fillMaxWidth().padding(AppSizes.cardPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppType.itemTitle().copy(fontWeight = FontWeight.SemiBold))
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
    /** Optional family per option, so a typeface picker previews its own fonts. */
    labelFamily: @Composable (T) -> FontFamily? = { null },
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppType.itemTitle().copy(fontWeight = FontWeight.SemiBold))
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
                    family = labelFamily(option),
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
    family: FontFamily? = null,
) {
    // One chip for the whole app: the settings choices read like the library's
    // filters instead of like a second, private design.
    AppChip(
        text = label,
        onClick = onClick,
        selected = selected,
        role = Role.RadioButton,
        fontFamily = family,
    )
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
                    .border(AppSizes.borderWidth, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
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
                        modifier = Modifier.handCursor(),
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
        shape = AppBottomSheetShape,
        containerColor = AppColors.surfaceSelected,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppSpacing.md)) {
            Text(
                title,
                style = AppType.sectionTitle(),
                modifier = Modifier.padding(AppSizes.cardPadding).sectionHeading(),
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
