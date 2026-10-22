package com.misgus.pengu.ui.services

import androidx.compose.runtime.staticCompositionLocalOf
import com.misgus.pengu.core.player.PlayerService
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.NoopStreamCacheRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.StreamCacheRepository
import com.misgus.pengu.core.repository.StreamRepository
import com.misgus.pengu.core.repository.SubtitleRepository
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.core.watched.WatchedSeriesCache
import com.misgus.pengu.ui.shell.AppChrome
import com.misgus.pengu.ui.window.WindowController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-wide repositories and platform services, handed down the composition.
 *
 * Screens read data from here instead of reaching into `data/`, which keeps the
 * `ui -> domain` direction and lets tests inject in-memory implementations.
 *
 * It also owns the shared [watchedBadges] resolver, so the library and Home
 * reuse the same in-memory and on-disk cache.
 */
class AppServices(
    /** Session and profiles. */
    val account: AccountRepository,
    /** Local preferences (`settings.json`). */
    val settings: SettingsRepository,
    /** Saved titles. */
    val library: LibraryRepository,
    /** Watch progress. */
    val progress: ProgressRepository,
    /** Title metadata from Stremio addons. */
    val metadata: MetadataRepository,
    /** Playable sources from Stremio addons. */
    val streams: StreamRepository,
    /** Catalogs exposed by Stremio addons. */
    val catalogs: CatalogRepository,
    /** The playback engine. */
    val player: PlayerService,
    /** Native window control. */
    val window: WindowController,
    /** Subtitle tracks exposed by Stremio addons. */
    val subtitles: SubtitleRepository,
    /**
     * Optional TMDB enrichment (artwork, text, cast) used by the detail screen
     * and the home billboard. A no-op unless an API key is set in Settings.
     */
    val tmdb: TmdbRepository,
    /** Remembered per-series "fully watched" results. */
    val watchedCache: WatchedSeriesCache,
    /**
     * The stream each title was last played with, so "Continue watching" can
     * resume without the picker while its URL is still fresh.
     */
    val streamCache: StreamCacheRepository = NoopStreamCacheRepository(),
    /** Immersive-mode state, shared with the player. */
    val chrome: AppChrome = AppChrome(),
    /** Addons installed in the account; used by the addon repository layer. */
    val addons: AddonRepository,
    /** Playback session signals, read by the screens that show progress. */
    val playbackUpdates: PlaybackUpdates = PlaybackUpdates(),
    /**
     * Work that has to outlive the screen that started it.
     *
     * The one use is the final progress write when the player closes: a
     * composable's own scope is cancelled as it leaves, which used to drop the
     * last position (see `PlayerScreen`).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** Watched-badge resolver, shared by the library and Home. */
    val watchedBadges: WatchedBadgeResolver =
        WatchedBadgeResolver(progress = progress, metadata = metadata, cache = watchedCache)

    /**
     * Whether [player] can actually play media on this target.
     *
     * The capability is declared by the engine itself, not inferred from its
     * type, so a target that installs a player which cannot play reads `false`
     * and the UI tells the user instead of opening a player that only reports
     * an error.
     */
    val playbackSupported: Boolean get() = player.canPlay

    /**
     * Drops what the app remembers about the user's watch history, so
     * "Continue watching" is rebuilt from the backend.
     *
     * The remembered per-series results are the only watch data kept on the
     * device — progress itself is always read from the account — so clearing
     * them and ticking [PlaybackUpdates.cachesCleared] is the whole reset.
     */
    suspend fun clearWatchCache() {
        val profileKey =
            account.state.value.activeProfile
                ?.profileId
                ?.toString()
                .orEmpty()
        watchedCache.clear(profileKey)
        playbackUpdates.notifyCachesCleared()
    }
}

val LocalAppServices =
    staticCompositionLocalOf<AppServices> {
        error("No AppServices provided in the composition")
    }
