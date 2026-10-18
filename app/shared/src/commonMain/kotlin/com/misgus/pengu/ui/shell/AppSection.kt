package com.misgus.pengu.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import com.misgus.pengu.ui.icons.FluentIcons

/**
 * The sections reachable from the navigation.
 *
 * The order here is the order shown in the rail and the bottom bar. The glyphs
 * are the Fluent set (`FluentIcons`); selection is the tint alone.
 */
enum class AppSection(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", FluentIcons.Home),
    SEARCH("Search", FluentIcons.Search),
    LIBRARY("Library", FluentIcons.Bookmark),
    CALENDAR("Calendar", FluentIcons.Calendar),
    SETTINGS("Settings", FluentIcons.Settings),
}
