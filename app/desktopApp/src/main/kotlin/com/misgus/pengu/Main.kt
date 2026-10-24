package com.misgus.pengu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.SignInRequest
import com.misgus.pengu.data.BackendKind
import com.misgus.pengu.data.addons.StremioAddonRepositories
import com.misgus.pengu.data.createBackend
import com.misgus.pengu.data.platform.createAddonHttpClient
import com.misgus.pengu.data.platform.createHttpClient
import com.misgus.pengu.data.store.AppPaths
import com.misgus.pengu.data.store.FileHomeFeedStore
import com.misgus.pengu.data.store.FileSessionStore
import com.misgus.pengu.data.store.FileStreamCache
import com.misgus.pengu.data.store.FileWatchedSeriesCache
import com.misgus.pengu.data.store.JsonMapStore
import com.misgus.pengu.data.store.JsonSettingsRepository
import com.misgus.pengu.imaging.configureImageLoader
import com.misgus.pengu.player.MediampPlayerService
import com.misgus.pengu.ui.PenguApp
import com.misgus.pengu.ui.services.AppServices
import com.misgus.pengu.ui.theme.AppWindowChrome
import com.misgus.pengu.ui.theme.PenguTheme
import com.misgus.pengu.window.WindowsWindowFrameState
import com.misgus.pengu.window.WindowsWindowTitleBar
import com.misgus.pengu.window.WindowsWindowUtils
import com.misgus.pengu.window.isWindowsOs
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    // The desktop Coil decoder aliases when it downsamples; install the cubic
    // one before any image is rendered.
    configureImageLoader()

    val environment = System.getenv()
    val backendKind = BackendKind.fromEnvironment(environment)
    val httpClient = createHttpClient()
    // One client pool for the four repositories, so an addon's manifest is
    // fetched once per process instead of once per repository. Addon traffic gets
    // its own client: it is the only traffic that is cached on disk, so a stale
    // read can never show the user library or progress data they just changed.
    // The addon repository also validates new addons with it.
    val addonHttpClient = createAddonHttpClient()

    // The composition root: the only place that picks concrete implementations.
    val backend =
        createBackend(
            kind = backendKind,
            httpClient = httpClient,
            addonHttpClient = addonHttpClient,
            sessionStore = FileSessionStore(),
            localAddons = localAddonsFromEnvironment(environment),
        )

    // One addon list shared by every Stremio repository.
    val addons = backend.addons
    val stremio = StremioAddonRepositories(addons, addonHttpClient, FileHomeFeedStore())
    val settings = JsonSettingsRepository(JsonMapStore(AppPaths.settingsFile, pretty = true))
    // The window is created before the composition's own settings load runs, so
    // the remembered geometry is read here once; the app then reads the same
    // file again with the rest of the preferences.
    val startSettings =
        runBlocking {
            settings.load()
            settings.state.value
        }
    val watchedCache = FileWatchedSeriesCache()

    val initialSource = initialSource(args)

    application {
        // Created on the Compose (EDT) thread so mediamp captures that same thread as its
        // main dispatcher; commands are then valid from the UI thread and its coroutines.
        val player = remember { MediampPlayerService(settings) }
        // The saved geometry is ignored when the user turned the toggle off, so
        // the window opens at the app's own default size.
        val remembered = startSettings.takeIf { it.rememberWindowSize }
        val windowState =
            rememberWindowState(
                width = remembered?.windowWidth?.dp ?: WINDOW_WIDTH,
                height = remembered?.windowHeight?.dp ?: WINDOW_HEIGHT,
                placement = if (remembered?.windowMaximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            )
        // A maximized window reports its maximized bounds, so the size worth
        // remembering is the last one it had while floating.
        val floatingSize = remember { mutableStateOf(windowState.size) }
        // The custom frame is Windows-only; other targets keep the native decoration.
        val frameState = remember { if (isWindowsOs()) WindowsWindowFrameState() else null }
        val windowController = remember { ComposeWindowController(windowState) }
        val services =
            remember {
                AppServices(
                    account = backend.account,
                    settings = settings,
                    library = backend.library,
                    progress = backend.progress,
                    metadata = stremio.metadata,
                    streams = stremio.streams,
                    catalogs = stremio.catalogs,
                    subtitles = stremio.subtitles,
                    watchedCache = watchedCache,
                    streamCache = FileStreamCache(),
                    player = player,
                    window = windowController,
                    addons = addons,
                )
            }

        val isFullScreen by windowController.isFullScreen.collectAsState()
        val closeApp: () -> Unit = {
            // Saved before the exit, so the next start reopens the same window.
            if (settings.state.value.rememberWindowSize) {
                saveWindowGeometry(
                    settings = settings,
                    size = floatingSize.value,
                    maximized = windowState.placement == WindowPlacement.Maximized,
                )
            }
            addonHttpClient.close()
            backend.close()
            player.dispose()
            exitApplication()
        }

        PenguTheme {
            Window(
                onCloseRequest = closeApp,
                state = windowState,
                title = WINDOW_TITLE,
            ) {
                LaunchedEffect(windowState.size, windowState.placement) {
                    if (windowState.placement == WindowPlacement.Floating) floatingSize.value = windowState.size
                }
                // Hand the shell every icon size instead of one bitmap: Windows picks
                // the rendition that matches the current DPI, so the taskbar and
                // Alt+Tab draw a sharp icon instead of rescaling a 256 px one.
                LaunchedEffect(window) {
                    window.iconImages =
                        WINDOW_ICON_SIZES.mapNotNull { size ->
                            ClassLoader
                                .getSystemResourceAsStream("pengu-icon/pengu-$size.png")
                                ?.use(ImageIO::read)
                        }
                }
                // The OS enforces the minimum: the window can grow or maximise but
                // never shrink below the size it opens with. Reapplied when the
                // density changes (e.g. moving to a monitor with another scale).
                val density = LocalDensity.current
                LaunchedEffect(window, density) {
                    window.minimumSize =
                        Dimension(
                            with(density) { WINDOW_WIDTH.roundToPx() },
                            with(density) { WINDOW_HEIGHT.roundToPx() },
                        )
                }
                // Paint the native window with the app background so resizing does
                // not flash the default white surface before Compose's next frame.
                val windowColor = AppWindowChrome.windowBackground
                SideEffect {
                    applyBackground(window, windowColor.toAwtColor())
                }
                // Install the custom frame once the native window exists.
                SideEffect {
                    frameState?.let { windowController.attach(window, it) }
                }
                Column(Modifier.fillMaxSize()) {
                    if (frameState != null && !isFullScreen) {
                        val maximized = windowState.placement == WindowPlacement.Maximized
                        WindowsWindowTitleBar(
                            state = frameState,
                            title = WINDOW_TITLE,
                            isMaximized = maximized,
                            onMinimize = { WindowsWindowUtils.minimize(window) },
                            onToggleMaximize = { WindowsWindowUtils.toggleMaximize(window, !maximized) },
                            onClose = closeApp,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PenguApp(
                            services = services,
                            initialSource = initialSource,
                            autoSignIn = credentialsFromEnvironment(environment),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Writes the window geometry the next start reopens with.
 *
 * Blocking on purpose: the process exits right after it, so a save in the
 * background would race the exit. The write is a small file.
 */
private fun saveWindowGeometry(
    settings: SettingsRepository,
    size: DpSize,
    maximized: Boolean,
) = runBlocking {
    runCatching {
        settings.setWindowGeometry(
            width = size.width.value.toInt(),
            height = size.height.value.toInt(),
            maximized = maximized,
        )
    }
}

/** Sets [color] as the background of [component] and its Swing children. */
private fun applyBackground(
    component: java.awt.Component,
    color: java.awt.Color,
) {
    component.background = color
    if (component is java.awt.Container) {
        component.components.forEach { applyBackground(it, color) }
    }
}

/** Converts a Compose colour to the AWT colour the window helpers use. */
private fun Color.toAwtColor(): java.awt.Color = java.awt.Color(toArgb(), true)

/**
 * Development shortcut: signs in with `NUVIO_EMAIL` / `NUVIO_PASSWORD` when there
 * is no stored session.
 *
 * Credentials are read from the environment for this run only; they are never
 * written to the repository or to disk.
 */
private fun credentialsFromEnvironment(environment: Map<String, String>): SignInRequest? {
    val email = environment["NUVIO_EMAIL"]
    val password = environment["NUVIO_PASSWORD"]
    if (email.isNullOrEmpty() || password.isNullOrEmpty()) return null
    return SignInRequest.EmailPassword(email, password)
}

/** Addons for local mode from `PENGU_LOCAL_ADDONS`, comma-separated URLs. */
private fun localAddonsFromEnvironment(environment: Map<String, String>): List<Addon> {
    val raw = environment["PENGU_LOCAL_ADDONS"] ?: return emptyList()
    return raw.split(',').mapNotNull { entry ->
        val url = entry.trim()
        if (url.isEmpty()) {
            null
        } else {
            Addon(id = url, url = url, name = url.substringAfter("://", url).substringBefore('/'))
        }
    }
}

/** Reads `--play=<source>` from the command line, if present. */
private fun initialSource(args: Array<String>): String? {
    val flag = "--play="
    return args.firstOrNull { it.startsWith(flag) }?.substring(flag.length)
}

/** Size the desktop window opens with, and the smallest it can be resized to. */
private val WINDOW_WIDTH: Dp = 1280.dp
private val WINDOW_HEIGHT: Dp = 720.dp

/**
 * Window title. Kept local because the shared Compose resources are internal to
 * the `:app:shared` module.
 */
private const val WINDOW_TITLE = "Pengu"

/** Sizes the shell may ask for; the icon set ships one PNG per entry. */
private val WINDOW_ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 256)
