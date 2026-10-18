package com.misgus.pengu.window

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Windows caption glyphs.
 *
 * Kept apart from the app's Fluent set on purpose: these are the native-shaped
 * minimize / maximize / restore / close glyphs, so the bar reads as Windows
 * chrome rather than as app content. The app's own icons live in
 * [com.misgus.pengu.ui.icons.FluentIcons].
 */
internal object WindowsCaptionIcons {
    val Minimize: ImageVector by lazy { caption("Minimize", REMOVE) }
    val Maximize: ImageVector by lazy { caption("Maximize", CROP_SQUARE) }
    val Restore: ImageVector by lazy { caption("Restore", FILTER_NONE) }
    val Close: ImageVector by lazy { caption("Close", CLOSE) }
}

/** Builds one caption [ImageVector] from its 24dp path. */
private fun caption(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()

private const val CLOSE =
    "M18.3 5.71a.996.996 0 0 0-1.41 0L12 10.59 7.11 5.7A.996.996 0 1 0 5.7 7.11L10.59 12 5.7 16.89a.996.996 0 1 0 1" +
        ".41 1.41L12 13.41l4.89 4.89a.996.996 0 1 0 1.41-1.41L13.41 12l4.89-4.89c.38-.38.38-1.02 0-1.4z"
private const val CROP_SQUARE =
    "M18 4H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-1 14H7c-.55 0-1-.45-1-1V7c0-.55." +
        "45-1 1-1h10c.55 0 1 .45 1 1v10c0 .55-.45 1-1 1z"
private const val FILTER_NONE =
    "M2 5c-.55 0-1 .45-1 1v15c0 1.1.9 2 2 2h15c.55 0 1-.45 1-1s-.45-1-1-1H4c-.55 0-1-.45-1-1V6c0-.55-.45-1-1-1zm19-" +
        "4H7c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V3c0-1.1-.9-2-2-2zm-1 16H8c-.55 0-1-.45-1-1V4c0-.55.45-1" +
        " 1-1h12c.55 0 1 .45 1 1v12c0 .55-.45 1-1 1z"
private const val REMOVE =
    "M18 13H6c-.55 0-1-.45-1-1s.45-1 1-1h12c.55 0 1 .45 1 1s-.45 1-1 1z"
