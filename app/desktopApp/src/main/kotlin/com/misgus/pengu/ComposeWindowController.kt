package com.misgus.pengu

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.misgus.pengu.ui.window.WindowController
import com.misgus.pengu.window.ScreenAwake
import com.misgus.pengu.window.WindowsWindowFrameProc
import com.misgus.pengu.window.WindowsWindowFrameState
import com.misgus.pengu.window.WindowsWindowUtils
import com.misgus.pengu.window.isWindowsOs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/** 1x1 transparent pointer: the player swaps it in while its overlay is hidden. */
private val blankCursor: Cursor by lazy {
    Toolkit
        .getDefaultToolkit()
        .createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank")
}

private fun Component.setCursorRecursively(cursor: Cursor) {
    this.cursor = cursor
    (this as? Container)?.components?.forEach { it.setCursorRecursively(cursor) }
}

/**
 * [WindowController] backed by a Compose Desktop [WindowState].
 *
 * On Windows the window keeps its native frame but the non-client area is erased
 * and hit-tested by [WindowsWindowFrameProc], so the Compose title bar behaves
 * like a real caption (native drag, snap, maximize, resize and Snap Layouts) and
 * fullscreen is a plain style flip. On the other desktop targets the window keeps
 * the native decoration and only fullscreen is routed here.
 *
 * Fullscreen on Windows is driven by [WindowsWindowUtils], which saves and
 * restores the styles, rect and maximized state. Compose's `Maximized` transition
 * never clears its fullscreen bit, so the fallback path still takes the window
 * out of fullscreen through `Floating` first and re-applies the previous
 * placement afterwards; those workarounds exist for the native-decoration path
 * and can be removed once every target uses the custom frame.
 */
class ComposeWindowController(
    private val windowState: WindowState,
) : WindowController {
    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    override val supportsFullScreen: Boolean = true

    // AWT reports the work area in the same logical (density-independent) space
    // Compose uses, so the height is a dp value without a density conversion.
    override val screenWorkAreaHeight: Dp =
        runCatching {
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .maximumWindowBounds
                .height
                .dp
        }.getOrDefault(0.dp)

    private var previousPlacement: WindowPlacement = WindowPlacement.Floating

    private var window: ComposeWindow? = null

    private var frameProc: WindowsWindowFrameProc? = null

    /**
     * Binds the native window once the composition has created it.
     *
     * On Windows it also installs the window procedure that draws the custom
     * frame. Installing is idempotent, because the composition calls this on every
     * frame; a failed install (the Skia peer not ready yet) is retried next frame.
     */
    fun attach(
        window: ComposeWindow,
        frameState: WindowsWindowFrameState,
    ) {
        this.window = window
        if (!isWindowsOs() || frameProc != null) return
        // The Skia child window may not exist on the very first frame; close the
        // half-installed frame and retry on the next composition.
        val proc = runCatching { WindowsWindowFrameProc(window, frameState) }.getOrNull()
        if (proc == null || !proc.isChildHooked) {
            proc?.close()
            return
        }
        frameProc = proc
    }

    override fun setFullScreen(value: Boolean) {
        val native = window ?: return
        if (value == _isFullScreen.value) return
        if (isWindowsOs()) {
            WindowsWindowUtils.setUndecoratedFullscreen(native, value)
            _isFullScreen.value = value
            return
        }
        val device = native.graphicsConfiguration.device
        if (value) {
            previousPlacement =
                windowState.placement
                    .takeIf { it != WindowPlacement.Fullscreen }
                    ?: WindowPlacement.Floating
            native.placement = WindowPlacement.Fullscreen
            if (device.isFullScreenSupported) {
                device.fullScreenWindow = native
            }
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            // Exclusive fullscreen clears the maximize bit on entry and restores
            // the (unmaximized) bounds on exit, so the previous placement is
            // re-applied by hand.
            if (device.fullScreenWindow === native) {
                device.fullScreenWindow = null
            }
            native.placement = WindowPlacement.Floating
            // Workaround: exclusive fullscreen restores the window bounds without
            // re-laying out the Compose scene, so the controls keep the fullscreen
            // size until the window is resized by hand. Nudge the size by a pixel
            // and back, in two dispatches so the intermediate layout actually
            // runs. It must happen before re-maximizing, because `setSize` on a
            // maximized frame unmaximizes it.
            val width = native.width
            val height = native.height
            native.setSize(width, height + 1)
            SwingUtilities.invokeLater {
                native.setSize(width, height)
                native.placement = previousPlacement
                windowState.placement = previousPlacement
            }
        }
        _isFullScreen.value = value
    }

    override fun setCursorHidden(hidden: Boolean) {
        // The Compose scene is a child component and owns the cursor the pointer
        // sees, so the frame alone is not enough on every desktop target.
        val native = window ?: return
        native.setCursorRecursively(if (hidden) blankCursor else Cursor.getDefaultCursor())
    }

    override fun setScreenAwake(awake: Boolean) = ScreenAwake.set(awake)
}
