package com.misgus.pengu.ui.shell

import androidx.compose.runtime.mutableStateListOf
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.ui.screens.SettingsCategory

/** A pushed content route (detail, full catalog, settings page or the player). */
sealed interface ContentRoute {
    data class Detail(
        val item: LibraryItem,
        /**
         * Set when the caller already knows the title is saved (it opened it
         * from the library), so the detail skips the `contains` check.
         */
        val knownSaved: Boolean = false,
    ) : ContentRoute

    data class Catalog(
        val ref: CatalogRef,
    ) : ContentRoute

    data class Player(
        val target: PlaybackTarget,
    ) : ContentRoute

    /** One settings page, opened from the compact index. */
    data class SettingsPage(
        val category: SettingsCategory,
    ) : ContentRoute
}

/**
 * Stack of routes shown inside the shell's content area.
 *
 * Pushed routes are immersive: the shell hides the top navigation so the screen
 * gets the whole window. Dialogs still cover everything because they are hosted
 * at the root.
 */
class ContentNavigator {
    val stack = mutableStateListOf<ContentRoute>()

    val current: ContentRoute? get() = stack.lastOrNull()
    val canGoBack: Boolean get() = stack.isNotEmpty()

    fun push(route: ContentRoute) {
        stack.add(route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /**
     * Replaces the top route without growing the stack.
     *
     * Used by the player's next-episode autoplay so "back" still leaves the
     * player instead of walking through every episode that auto-played.
     */
    fun replace(route: ContentRoute) {
        if (stack.isEmpty()) stack.add(route) else stack[stack.lastIndex] = route
    }

    fun clear() {
        stack.clear()
    }
}
