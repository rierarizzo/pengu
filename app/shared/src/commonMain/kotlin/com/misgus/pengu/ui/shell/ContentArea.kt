package com.misgus.pengu.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.ui.player.PlayerScreen
import com.misgus.pengu.ui.screens.CatalogScreen
import com.misgus.pengu.ui.screens.DetailScreen
import com.misgus.pengu.ui.screens.SettingsCategoryScreen
import com.misgus.pengu.ui.streams.StreamsRequest
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.LocalMotionEnabled
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.WindowSize
import com.misgus.pengu.ui.theme.safeBottomInset
import com.misgus.pengu.ui.theme.safeTopInset

/** Host for transient snackbars, so any screen can show one. */
val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState?> { null }

/** A [ViewModelStoreOwner] scoped to a pushed route; cleared when the route pops. */
class RouteViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * Renders the content area: the current section with the pushed route on top.
 *
 * The section stays composed while a route is open, so its scroll position is
 * never saved and restored — that round-trip lost the within-item offset and
 * scrolled the section back up. Changing a tab swaps the section with no
 * transition; the route fades in over it and leaves at once when popped.
 *
 * The player renders outside the route transition on purpose: it drives a single
 * shared engine, and a transition would keep the outgoing player alive next to
 * the incoming one.
 */
@Composable
fun ContentArea(
    selected: AppSection,
    navigator: ContentNavigator,
    onOpenStreams: (StreamsRequest) -> Unit,
    onOpenPlayer: (PlaybackTarget) -> Unit,
) {
    val enabled = LocalMotionEnabled.current
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                // Switching a tab swaps the whole section at once: no crossfade,
                // so a section never paints over its neighbour mid-swap.
                EnterTransition.None togetherWith ExitTransition.None
            },
            label = "section",
        ) { section ->
            // The compact bottom bar floats over the content, so a scrolling
            // section runs edge-to-edge behind it and the bar folds away as the
            // user scrolls. A wide window reserves the rail's width.
            val compact = LocalWindowSize.current == WindowSize.COMPACT
            val contentPadding =
                when {
                    section == AppSection.HOME && compact -> PaddingValues(bottom = safeBottomInset)
                    compact -> PaddingValues(top = safeTopInset, bottom = safeBottomInset)
                    else ->
                        PaddingValues(
                            start = AppSizes.railWidth,
                            top = safeTopInset,
                            bottom = safeBottomInset,
                        )
                }
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                SectionHost(section, navigator, onOpenStreams, onOpenPlayer)
            }
        }

        val route = navigator.current
        if (route is ContentRoute.Player) {
            RouteContent(route) {
                PlayerScreen(
                    target = route.target,
                    onBack = navigator::pop,
                    onPlayNext = { navigator.replace(ContentRoute.Player(it)) },
                )
            }
            return@Box
        }

        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val enter = AppMotion.spec<Float>(enabled, AppMotion.MEDIUM, easing = AppMotion.emphasizedDecelerate)
                // Only the route animates; the section underneath is left alone.
                (fadeIn(enter) togetherWith ExitTransition.None).using(null)
            },
            label = "route",
        ) { target ->
            if (target != null) {
                RouteContent(target) {
                    when (target) {
                        is ContentRoute.Detail ->
                            DetailScreen(
                                item = target.item,
                                knownSaved = target.knownSaved,
                                onBack = navigator::pop,
                                onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                                onOpenStreams = onOpenStreams,
                            )
                        is ContentRoute.Catalog ->
                            CatalogScreen(
                                ref = target.ref,
                                onBack = navigator::pop,
                                onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
                            )
                        is ContentRoute.SettingsPage ->
                            SettingsCategoryScreen(
                                category = target.category,
                                onBack = navigator::pop,
                                onOpenPlayer = onOpenPlayer,
                            )
                        // Rendered above, outside the transition.
                        is ContentRoute.Player -> Unit
                    }
                }
            }
        }
    }
}

/** Scopes a route's ViewModels and clears them when the route leaves. */
@Composable
private fun RouteContent(
    key: Any,
    content: @Composable () -> Unit,
) {
    val owner = remember(key) { RouteViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
