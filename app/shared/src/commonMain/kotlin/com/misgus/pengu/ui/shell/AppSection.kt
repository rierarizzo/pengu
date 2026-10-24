package com.misgus.pengu.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import com.misgus.pengu.ui.icons.MaterialIcons

/**
 * The sections of the app, in the order shown in the rail and the bottom bar.
 *
 * The glyphs are the Material Symbols Rounded set (`MaterialIcons`); selection is
 * the tint alone.
 */
enum class AppSection(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", MaterialIcons.Home),
    SEARCH("Search", MaterialIcons.Search),
    LIBRARY("Library", MaterialIcons.Bookmark),
    SETTINGS("Settings", MaterialIcons.Settings),
}
