package com.misgus.pengu.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width class, following Material's breakpoints.
 *
 * It is resolved once at the shell root and read through [LocalWindowSize], so a
 * screen picks a layout without measuring the window itself.
 */
enum class WindowSize {
    /** Phone portrait, or a narrow window: below 600dp. */
    COMPACT,

    /** Phone landscape, tablet, desktop or TV: 600dp and up. */
    EXPANDED,
    ;

    companion object {
        /** Breakpoint between [COMPACT] and [EXPANDED]. */
        val CompactMaxWidth: Dp = 600.dp

        /** Classifies [width]. */
        fun of(width: Dp): WindowSize =
            when {
                width < CompactMaxWidth -> COMPACT
                else -> EXPANDED
            }
    }
}

/**
 * [WindowSize] of the window the app is rendering into.
 *
 * Defaults to [WindowSize.EXPANDED], so a preview or a test without the provider
 * gets the desktop layout.
 */
val LocalWindowSize = staticCompositionLocalOf { WindowSize.EXPANDED }

/**
 * Width of the app window in dp.
 *
 * A surface that lives outside the shell's constraints needs it: a `Dialog`
 * opens its own window, so a fixed width there can exceed the app window.
 * Defaults to [Dp.Infinity], which leaves such a caller unconstrained.
 */
val LocalWindowWidth = staticCompositionLocalOf { Dp.Infinity }

/**
 * Height of the app window in dp.
 *
 * A surface that cannot measure its own viewport — one inside a vertical scroll,
 * where the incoming height constraint is unbounded — reads it to cap its size.
 * Defaults to [Dp.Infinity], which leaves such a caller uncapped.
 */
val LocalWindowHeight = staticCompositionLocalOf { Dp.Infinity }

/**
 * Height of the status bar (and any display cutout), in dp.
 *
 * Zero on desktop, where no system chrome draws over the window. Android runs
 * edge-to-edge, so a surface adds it wherever it must not paint under the bars.
 */
val safeTopInset: Dp
    @Composable get() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** Height of the navigation bar / gesture area, in dp. Zero on desktop. */
val safeBottomInset: Dp
    @Composable get() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Side padding for grids, rails, headers and form fields; smaller on a phone. */
val WindowSize.screenPadding: Dp
    get() = if (this == WindowSize.COMPACT) AppSpacing.md else AppSpacing.xl

/** Gap between grid cells and rail cards; tighter on a phone. */
val WindowSize.gridSpacing: Dp
    get() = if (this == WindowSize.COMPACT) AppSpacing.sm else AppSpacing.md
