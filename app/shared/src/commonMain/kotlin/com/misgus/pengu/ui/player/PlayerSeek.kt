package com.misgus.pengu.ui.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Seek step used by the keyboard shortcuts and the control buttons. */
internal val SEEK_BACK: Duration = (-10).seconds
internal val SEEK_FORWARD: Duration = 10.seconds

/** Long skip, reached by holding Shift with the arrow shortcuts. */
internal val SEEK_BACK_LONG: Duration = (-60).seconds
internal val SEEK_FORWARD_LONG: Duration = 60.seconds
