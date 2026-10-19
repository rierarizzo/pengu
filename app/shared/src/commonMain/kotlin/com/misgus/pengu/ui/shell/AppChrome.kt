package com.misgus.pengu.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import com.misgus.pengu.ui.theme.AppSizes

/**
 * App-level chrome state the shell reacts to.
 *
 * The player asks for immersive mode while it is open, so the navigation rail
 * is hidden even when the window is not fullscreen (the title bar stays, so the
 * window can still be moved or closed). The compact navigation bar also folds
 * away while the content is scrolled down, so it does not eat into the content.
 */
class AppChrome {
    var immersive by mutableStateOf(false)
        private set

    /**
     * `true` while the content is being scrolled down, so the compact navigation
     * bar stays out of the way; it returns on a scroll up.
     *
     * Set by [collapseChromeOnScroll] and reset by the shell when the section or
     * the route changes, so a screen always opens with the bar expanded.
     */
    var scrollCollapsed by mutableStateOf(false)
        private set

    private var requests = 0

    /**
     * Hides the chrome until the matching [exitImmersive].
     *
     * Counted, so when a player replaces another one (autoplay) the chrome does
     * not flash back during the transition.
     */
    fun enterImmersive() {
        requests++
        immersive = true
    }

    /** Releases one immersive request. */
    fun exitImmersive() {
        if (requests > 0) requests--
        immersive = requests > 0
    }

    /** Collapses or expands the scroll-driven chrome. */
    fun updateScrollCollapsed(collapsed: Boolean) {
        scrollCollapsed = collapsed
    }
}

/**
 * Collapses [chrome] while the content is scrolled down and expands it on a
 * scroll up.
 *
 * The connection runs on the container that wraps every section, so it observes
 * the actual scrollables (grids, rails, plain scroll columns) regardless of the
 * screen. It never consumes the delta, so it only reacts to the scroll the
 * content already performs.
 */
@Composable
fun Modifier.collapseChromeOnScroll(chrome: AppChrome): Modifier {
    val threshold = with(LocalDensity.current) { AppSizes.scrollCollapseThreshold.toPx() }
    val connection =
        remember(chrome, threshold) {
            ScrollCollapseConnection(threshold, chrome::updateScrollCollapsed)
        }
    return nestedScroll(connection)
}

/**
 * Accumulates scroll deltas and flips the collapsed state once the content has
 * travelled [threshold] pixels in one direction.
 *
 * The sign follows Compose's nested scroll: a negative `y` is the content pulled
 * up (scrolling down), which collapses the chrome, and a positive `y` is pulled
 * down (scrolling up), which expands it. A reversal resets the run-up, so a flick
 * back acts at once instead of undoing the previous direction first.
 */
internal class ScrollCollapseConnection(
    private val threshold: Float,
    private val onCollapsedChange: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var travelled = 0f

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.y
        if (delta == 0f) return Offset.Zero
        if ((delta > 0f) != (travelled > 0f)) travelled = 0f
        travelled += delta
        when {
            travelled <= -threshold -> {
                onCollapsedChange(true)
                travelled = 0f
            }
            travelled >= threshold -> {
                onCollapsedChange(false)
                travelled = 0f
            }
        }
        return Offset.Zero
    }
}
