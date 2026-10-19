package com.misgus.pengu.ui.services

import androidx.compose.runtime.staticCompositionLocalOf
import com.misgus.pengu.core.catalog.HomeSnapshotStore
import com.misgus.pengu.core.catalog.NoopHomeSnapshotStore
import com.misgus.pengu.core.player.NoopPlayerService
import com.misgus.pengu.core.player.PlayerService
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.LibraryRepository
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.NoSubtitleRepository
import com.misgus.pengu.core.repository.NoTmdbRepository
import com.misgus.pengu.core.repository.NoopAddonRepository
import com.misgus.pengu.core.repository.ProgressRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.StreamRepository
import com.misgus.pengu.core.repository.SubtitleRepository
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.watched.NoopWatchedSeriesCache
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.core.watched.WatchedSeriesCache
import com.misgus.pengu.ui.shell.AppChrome
import com.misgus.pengu.ui.window.WindowController

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
    val subtitles: SubtitleRepository = NoSubtitleRepository(),
    /**
     * Optional TMDB enrichment (artwork, text, cast) used by the detail screen
     * and the home billboard. A no-op unless an API key is set in Settings.
     */
    val tmdb: TmdbRepository = NoTmdbRepository(),
    /** Remembered per-series "fully watched" results. */
    val watchedCache: WatchedSeriesCache = NoopWatchedSeriesCache(),
    /**
     * Last Home paint, per profile, so a cold start shows the previous catalogs
     * while the network answer is on its way.
     */
    val homeSnapshot: HomeSnapshotStore = NoopHomeSnapshotStore(),
    /** Immersive-mode state, shared with the player. */
    val chrome: AppChrome = AppChrome(),
    /** Addons installed in the account; used by the addon repository layer. */
    val addons: AddonRepository = NoopAddonRepository(),
) {
    /** Watched-badge resolver, shared by the library and Home. */
    val watchedBadges: WatchedBadgeResolver =
        WatchedBadgeResolver(progress = progress, metadata = metadata, cache = watchedCache)

    /**
     * Whether [player] can actually play media on this target.
     *
     * The capability is declared by the engine itself, not inferred from its
     * type, so a target whose composition root installs a [NoopPlayerService]
     * reads `false` and the UI tells the user instead of opening a player that
     * only reports an error.
     */
    val playbackSupported: Boolean get() = player.canPlay
}

val LocalAppServices =
    staticCompositionLocalOf<AppServices> {
        error("No AppServices provided in the composition")
    }
