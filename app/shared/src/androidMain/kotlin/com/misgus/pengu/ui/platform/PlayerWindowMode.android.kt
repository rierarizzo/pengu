package com.misgus.pengu.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Locks the host activity to sensor landscape, hides the system bars and holds
 * the display on for as long as the player is composed, then restores all of it.
 *
 * `SENSOR_LANDSCAPE` rather than a fixed landscape so the video follows the
 * phone when the user flips it to the other side. Android 16+ ignores
 * `requestedOrientation` on large screens, where the user can still rotate
 * freely; phones enforce it even with auto-rotate off. The bars come back on a
 * swipe (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) and are shown again when the
 * player leaves.
 */
@Composable
actual fun PlayerWindowMode() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // A feature-length video outlasts the screen timeout, so the player holds
        // the display on until it is left.
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
