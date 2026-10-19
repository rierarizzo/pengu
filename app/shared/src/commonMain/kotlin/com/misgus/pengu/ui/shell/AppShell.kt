package com.misgus.pengu.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.error_playback_unavailable
import com.misgus.pengu.app.shared.generated.resources.error_source_unplayable
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.ui.platform.PlatformBackHandler
import com.misgus.pengu.ui.profiles.ProfilePicker
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.streams.StreamsDialog
import com.misgus.pengu.ui.streams.StreamsRequest
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.LocalMotionEnabled
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.WindowSize
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Persistent app chrome: the navigation and the content area.
 *
 * The compact bottom bar floats over the content, which runs edge-to-edge behind
 * it, so the bar can fold away on scroll without leaving a gap and without the
 * content viewport ever changing (resizing it would clamp a section's scroll). A
 * section that does not scroll, such as Calendar, keeps the bar's height and stays
 * clear of it instead. Pushed routes use the full window and let the navigation
 * fade away.
 */
@Composable
fun AppShell() {
    val services = LocalAppServices.current
    val account by services.account.state.collectAsStateWithLifecycle()
    val isFullScreen by services.window.isFullScreen.collectAsStateWithLifecycle()
    val chrome = services.chrome
    val immersive = chrome.immersive
    val scrollCollapsed = chrome.scrollCollapsed
    val motionEnabled = LocalMotionEnabled.current

    // Section and route stack live in a ViewModel so a rotation (Android) keeps
    // the navigation instead of returning to Home. Keying it by the active
    // profile makes a profile switch start from a clean shell.
    val shell: ShellViewModel = viewModel(key = account.activeProfile?.id) { ShellViewModel() }
    val selected = shell.selected
    val navigator = shell.navigator
    val selectSection: (AppSection) -> Unit = { section ->
        // Expand the chrome before the next section composes, so a section the
        // user left scrolled down never flashes its bar folded on entry.
        chrome.updateScrollCollapsed(false)
        shell.select(section)
    }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var streamsRequest by remember { mutableStateOf<StreamsRequest?>(null) }
    val playbackUnavailable = stringResource(Res.string.error_playback_unavailable)
    val sourceUnplayable = stringResource(Res.string.error_source_unplayable)
    // One place decides whether a playback target can open, so a section and a
    // pushed settings page cannot disagree about the support check.
    val openPlayer: (PlaybackTarget) -> Unit = { target ->
        if (services.playbackSupported) {
            navigator.push(ContentRoute.Player(target))
        } else {
            scope.launch {
                snackbarHost.showSnackbar(playbackUnavailable)
            }
        }
    }

    // The system back leaves a pushed route before it leaves the app. A dialog
    // or the player is composed on top, so it gets the event first.
    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.pop() }

    // A pushed route (detail, catalog) is an immersive screen with its own back
    // affordance, so the navigation fades away; the content keeps its size.
    val showNav = !isFullScreen && !immersive && navigator.current == null
    // Scrolling down folds the compact bar away and scrolling up brings it back;
    // the wide rail stays put, since it is the window's chrome, not a strip the
    // content can spare. A new section or route always opens expanded.
    val showBottomNav = showNav && !scrollCollapsed
    val gate = account.isSignedIn && account.requiresProfile && account.activeProfile == null
    LaunchedEffect(selected, navigator.current) { chrome.updateScrollCollapsed(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The window class is published at the root (PenguApp), so every
        // surface — including onboarding and the player — reads the same one.
        val windowSize = LocalWindowSize.current
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHost) {
            if (gate) {
                // Profile gate: the library and every write belong to a profile, so
                // there is no safe default. Backends without profiles never enter here.
                Box(Modifier.fillMaxSize()) { ProfilePicker() }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    // The app is edge-to-edge: screens place the system insets
                    // themselves, so the Scaffold must not reserve them again.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        // A snackbar clears the compact bottom bar; a pushed route
                        // hides the bar, so it falls back to the window edge.
                        val barHeight =
                            if (showBottomNav && windowSize == WindowSize.COMPACT) {
                                AppSizes.bottomBarHeight
                            } else {
                                0.dp
                            }
                        SnackbarHost(
                            snackbarHost,
                            modifier = Modifier.padding(bottom = barHeight).navigationBarsPadding(),
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).collapseChromeOnScroll(chrome)) {
                        ContentArea(
                            selected = selected,
                            navigator = navigator,
                            onOpenPlayer = openPlayer,
                            onOpenStreams = { request ->
                                if (services.playbackSupported) {
                                    streamsRequest = request
                                } else {
                                    scope.launch {
                                        snackbarHost.showSnackbar(playbackUnavailable)
                                    }
                                }
                            },
                        )
                        // The navigation never changes the content viewport: it
                        // fades in over the edge-to-edge content. Compact windows
                        // (phones) get the icon-only bottom bar; wider windows get
                        // the left rail.
                        val navFade = AppMotion.spec<Float>(motionEnabled, AppMotion.SLOW, easing = AppMotion.emphasized)
                        val navSlide = AppMotion.spec<IntOffset>(motionEnabled, AppMotion.SLOW, easing = AppMotion.emphasized)
                        if (windowSize == WindowSize.COMPACT) {
                            AnimatedVisibility(
                                visible = showBottomNav,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                enter = fadeIn(navFade) + slideInVertically(navSlide) { it },
                                exit = fadeOut(navFade) + slideOutVertically(navSlide) { it },
                            ) {
                                BottomNav(selected = selected, onSelected = selectSection)
                            }
                        } else {
                            AnimatedVisibility(
                                visible = showNav,
                                modifier = Modifier.align(Alignment.CenterStart),
                                enter = fadeIn(navFade) + slideInHorizontally(navSlide) { -it },
                                exit = fadeOut(navFade) + slideOutHorizontally(navSlide) { -it },
                            ) {
                                LeftNav(
                                    selected = selected,
                                    onSelected = selectSection,
                                    activeProfile = account.activeProfile,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    streamsRequest?.let { request ->
        StreamsDialog(
            request = request,
            onDismiss = { streamsRequest = null },
            onSelect = { stream ->
                streamsRequest = null
                val url = stream.url
                if (!url.isNullOrEmpty()) {
                    navigator.push(
                        ContentRoute.Player(
                            PlaybackTarget.fromContent(
                                type = request.type,
                                id = request.id,
                                title = request.title,
                                source = url,
                                httpHeaders = stream.httpHeaders,
                            ),
                        ),
                    )
                } else {
                    scope.launch {
                        snackbarHost.showSnackbar(sourceUnplayable)
                    }
                }
            },
        )
    }
}
