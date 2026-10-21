package com.misgus.pengu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.misgus.pengu.data.BackendBundle
import com.misgus.pengu.data.BackendKind
import com.misgus.pengu.data.addons.StremioAddonRepositories
import com.misgus.pengu.data.createBackend
import com.misgus.pengu.data.platform.createAddonHttpClient
import com.misgus.pengu.data.platform.createHttpClient
import com.misgus.pengu.data.platform.initPlatform
import com.misgus.pengu.data.store.AppPaths
import com.misgus.pengu.data.store.FileSessionStore
import com.misgus.pengu.data.store.FileStreamCache
import com.misgus.pengu.data.store.FileWatchedSeriesCache
import com.misgus.pengu.data.store.JsonMapStore
import com.misgus.pengu.data.store.JsonSettingsRepository
import com.misgus.pengu.data.tmdb.TmdbMetadataRepository
import com.misgus.pengu.player.MediampPlayerService
import com.misgus.pengu.ui.PenguApp
import com.misgus.pengu.ui.services.AppServices
import com.misgus.pengu.ui.window.NoopWindowController
import io.ktor.client.HttpClient

/**
 * Android entry point.
 *
 * Desktop keeps its own composition root in `:app:desktopApp`; this one wires
 * the shared backend and the ExoPlayer player. Both composition roots pick the
 * concrete engine, so the shared UI only ever sees [com.misgus.pengu.core.player.PlayerService].
 */
class MainActivity : ComponentActivity() {
    private lateinit var backend: BackendBundle
    private lateinit var addonHttpClient: HttpClient
    private lateinit var player: MediampPlayerService

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app always paints a dark surface, so the system-bar icons are
        // forced light: the default `auto` follows the OS and turns them dark in
        // light mode, which is invisible over the artwork.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Gives the platform layer a real, persistent app directory.
        initPlatform(this)

        // Built on the UI thread, as ExoPlayer requires, and owned by the activity.
        player = MediampPlayerService(this)

        val httpClient = createHttpClient()
        // One client pool for the four Stremio repositories, so an addon's
        // manifest is fetched once per process instead of once per repository.
        // Addon traffic gets its own client: it is the only traffic cached on
        // disk, so a stale read cannot show data the user just changed. The
        // addon repository also validates new addons with it.
        addonHttpClient = createAddonHttpClient()
        backend =
            createBackend(
                kind = BackendKind.NUVIO,
                httpClient = httpClient,
                addonHttpClient = addonHttpClient,
                sessionStore = FileSessionStore(),
            )
        val addons = backend.addons
        val settings = JsonSettingsRepository(JsonMapStore(AppPaths.settingsFile, pretty = true))
        val stremio = StremioAddonRepositories(addons, addonHttpClient)
        val services =
            AppServices(
                account = backend.account,
                settings = settings,
                library = backend.library,
                progress = backend.progress,
                metadata = stremio.metadata,
                streams = stremio.streams,
                catalogs = stremio.catalogs,
                subtitles = stremio.subtitles,
                tmdb = TmdbMetadataRepository(httpClient, settings),
                player = player,
                window = NoopWindowController(),
                addons = addons,
                // Same persistence as desktop: the watched-series cache lives in
                // the app's config directory.
                watchedCache = FileWatchedSeriesCache(),
                streamCache = FileStreamCache(),
            )

        setContent {
            PenguApp(services = services)
        }
    }

    override fun onStop() {
        // Background playback is not wired yet (it needs a media session and a
        // foreground service), so leaving the app pauses. This is the seam where
        // that policy would live; a configuration change (rotation) must not
        // pause, because the same session continues once it finishes.
        if (!isChangingConfigurations) player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        // Releases the player engine and the HTTP clients (the backend's and the
        // addon one, which a configuration change would otherwise leak).
        player.dispose()
        addonHttpClient.close()
        backend.close()
        super.onDestroy()
    }
}
