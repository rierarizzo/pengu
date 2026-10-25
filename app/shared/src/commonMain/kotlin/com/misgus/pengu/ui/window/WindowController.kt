package com.misgus.pengu.ui.window

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls the parts of the OS window the app needs to drive itself.
 *
 * The window keeps its **native decoration** (title bar, minimize, maximize,
 * close and resizing are handled by the OS). It exposes the screen work area and
 * routes fullscreen, because the player owns that toggle.
 */
interface WindowController {
    val isFullScreen: StateFlow<Boolean>

    /**
     * Whether [setFullScreen] actually does something on this target.
     *
     * Desktop can fullscreen its window; a platform that is always fullscreen
     * (Android) reports `false` so the player hides the toggle instead of
     * offering a no-op control.
     */
    val supportsFullScreen: Boolean

    /**
     * Height of the screen's work area (the display minus the taskbar), or
     * `0.dp` when it cannot be read.
     *
     * The Home billboard uses it as the reference for the height it has on a
     * maximized window, so that height is stable while the window is resized or
     * floating.
     */
    val screenWorkAreaHeight: Dp

    fun setFullScreen(value: Boolean)

    /**
     * Hides the mouse pointer, or shows it again.
     *
     * The player hides it while its overlay is hidden, the way every video
     * player does. A target with no pointer to hide leaves this a no-op.
     */
    fun setCursorHidden(hidden: Boolean) = Unit

    /**
     * Keeps the display and the system awake, or lets them idle again.
     *
     * The player holds this for as long as it is on screen: otherwise a film is
     * long enough for the OS to treat it as inactivity and blank the monitor. A
     * target whose OS does not idle out leaves this a no-op.
     */
    fun setScreenAwake(awake: Boolean) = Unit
}

/** [WindowController] that does nothing; used by previews and tests. */
class NoopWindowController : WindowController {
    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    override val supportsFullScreen: Boolean = false

    override val screenWorkAreaHeight: Dp = 0.dp

    override fun setFullScreen(value: Boolean) {
        _isFullScreen.value = value
    }
}
