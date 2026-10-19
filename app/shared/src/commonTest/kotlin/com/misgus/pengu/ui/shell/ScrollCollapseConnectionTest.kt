package com.misgus.pengu.ui.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.misgus.pengu.ui.theme.AppSizes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollCollapseConnectionTest {
    // The connection works in pixels; the test drives it with the production
    // threshold value, since it never converts units itself.
    private val threshold = AppSizes.scrollCollapseThreshold.value
    private val changes = mutableListOf<Boolean>()
    private val connection = ScrollCollapseConnection(threshold) { changes += it }

    @Test
    fun `scrolling down past the threshold collapses`() {
        connection.onPreScroll(Offset(0f, -threshold - 1f), NestedScrollSource.Drag)

        assertEquals(listOf(true), changes)
    }

    @Test
    fun `scrolling up past the threshold expands`() {
        connection.onPreScroll(Offset(0f, -threshold - 1f), NestedScrollSource.Drag)
        connection.onPreScroll(Offset(0f, threshold + 1f), NestedScrollSource.Drag)

        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `movement under the threshold does not toggle`() {
        connection.onPreScroll(Offset(0f, -threshold / 4f), NestedScrollSource.Drag)
        connection.onPreScroll(Offset(0f, -threshold / 4f), NestedScrollSource.Drag)

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `a reversal resets the run-up`() {
        connection.onPreScroll(Offset(0f, -threshold - 1f), NestedScrollSource.Drag)
        connection.onPreScroll(Offset(0f, threshold / 2f), NestedScrollSource.Drag)
        connection.onPreScroll(Offset(0f, -threshold / 2f), NestedScrollSource.Drag)

        assertEquals(listOf(true), changes)
    }
}
