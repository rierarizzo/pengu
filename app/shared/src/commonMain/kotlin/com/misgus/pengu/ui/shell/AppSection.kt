package com.misgus.pengu.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import com.misgus.pengu.ui.icons.FluentIcons

/**
 * The sections of the app, in the order shown in the rail and the bottom bar.
 *
 * [navigation] is what the chrome lists; a section can keep its route and screen
 * with [inNavigation] off, so a section under rework disappears from the
 * navigation without being deleted. The glyphs are the Fluent set
 * (`FluentIcons`); selection is the tint alone.
 */
enum class AppSection(
    val label: String,
    val icon: ImageVector,
    /** Whether the section is listed in the rail and the bottom bar. */
    val inNavigation: Boolean = true,
) {
    HOME("Home", FluentIcons.Home),
    SEARCH("Search", FluentIcons.Search),
    LIBRARY("Library", FluentIcons.Bookmark),

    // Hidden while its agenda rewrite is pending; the screen and route stay.
    CALENDAR("Calendar", FluentIcons.Calendar, inNavigation = false),
    SETTINGS("Settings", FluentIcons.Settings),
    ;

    companion object {
        /** Sections listed in the chrome, in display order. */
        val navigation: List<AppSection> = entries.filter { it.inNavigation }
    }
}
