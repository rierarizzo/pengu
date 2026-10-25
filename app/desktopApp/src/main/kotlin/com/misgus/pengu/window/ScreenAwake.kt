package com.misgus.pengu.window

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Keeps Windows from blanking the display while the player is on screen.
 *
 * `SetThreadExecutionState` is per-thread and drops back to normal when the
 * calling thread ends, so the flag has to be cleared from the same thread that
 * set it: both calls come from the composition (the UI thread). Other desktops
 * idle out on their own terms and are left alone.
 */
internal object ScreenAwake {
    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_DISPLAY_REQUIRED = 0x00000002

    // The Win32 entry point keeps its exported name; JNA maps by method name.
    @Suppress("ktlint:standard:function-naming")
    private interface Kernel32 : Library {
        fun SetThreadExecutionState(flags: Int): Int
    }

    private val kernel32: Kernel32? =
        if (isWindowsOs()) {
            runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
        } else {
            null
        }

    fun set(awake: Boolean) {
        val library = kernel32 ?: return
        runCatching { library.SetThreadExecutionState(ES_CONTINUOUS or if (awake) ES_DISPLAY_REQUIRED else 0) }
    }
}
