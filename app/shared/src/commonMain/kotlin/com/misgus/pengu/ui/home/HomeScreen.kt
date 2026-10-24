package com.misgus.pengu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.detail_episode_code
import com.misgus.pengu.app.shared.generated.resources.detail_error_library
import com.misgus.pengu.app.shared.generated.resources.empty_nothing_to_show
import com.misgus.pengu.app.shared.generated.resources.home_continue_remove_error
import com.misgus.pengu.app.shared.generated.resources.home_continue_watching
import com.misgus.pengu.app.shared.generated.resources.home_empty_message
import com.misgus.pengu.app.shared.generated.resources.home_error_title
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.components.ContinueWatchingActions
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterCard
import com.misgus.pengu.ui.components.SectionHeader
import com.misgus.pengu.ui.components.SyncingIndicator
import com.misgus.pengu.ui.components.contentTypeLabel
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.ContentNavigator
import com.misgus.pengu.ui.shell.ContentRoute
import com.misgus.pengu.ui.shell.LocalSnackbarHost
import com.misgus.pengu.ui.streams.StreamsRequest
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LocalWindowHeight
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.WindowSize
import com.misgus.pengu.ui.theme.cellWidth
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.posterCardHeight
import com.misgus.pengu.ui.theme.safeTopInset
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Home section: a featured carousel, "Continue watching" and catalog rows. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navigator: ContentNavigator,
    onOpenStreams: (StreamsRequest) -> Unit,
    onPlayTarget: (PlaybackTarget) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val services = LocalAppServices.current
    val settingsRepository = services.settings
    val settings by settingsRepository.state.collectAsStateWithLifecycle()
    val windowSize = LocalWindowSize.current
    val snackbar = LocalSnackbarHost.current
    val savedError = state.savedError
    val libraryError = stringResource(Res.string.detail_error_library)

    // The ViewModel owns the write; the screen surfaces a failure once and clears it.
    LaunchedEffect(savedError) {
        if (savedError != null) {
            snackbar?.showSnackbar(libraryError)
            viewModel.consumeSavedError()
        }
    }

    // The feed is published in one update once the addons and the watch history
    // have answered, and the last feed from disk goes up before that first paint,
    // so a cold Home is not blank while the addons are read. The last thing that
    // can still pop in is the billboard artwork, a full-size image: on the
    // transition from loading to loaded, Home keeps its loading screen until the
    // first slide is decoded (or the wait times out), so the hero never shows the
    // bare background and then fills in. A Home that was already loaded when the
    // screen composed (coming back from another section) is not gated again.
    val cameLoaded = remember { state.rows.isNotEmpty() }
    var heroArtworkReady by remember { mutableStateOf(false) }
    val featuredKey = state.featured.map { it.type to it.id }
    LaunchedEffect(featuredKey) { heroArtworkReady = false }
    val waitsForHero =
        !cameLoaded && !heroArtworkReady && state.rows.isNotEmpty() && state.featured.isNotEmpty()
    var heroWaitExpired by remember { mutableStateOf(false) }
    LaunchedEffect(waitsForHero, featuredKey) {
        if (!waitsForHero) return@LaunchedEffect
        heroWaitExpired = false
        delay(HERO_ARTWORK_TIMEOUT)
        heroWaitExpired = true
    }

    // "Continue watching" resumes straight into the player when the URL the title
    // was last played with is still trusted; otherwise — expired, missing, or a
    // pick that was never written down — the picker decides again. A debrid URL
    // that fails to play is dropped by the player, so the next tap lands here too.
    val scope = rememberCoroutineScope()
    val removeContinueError = stringResource(Res.string.home_continue_remove_error)
    val playEntry: (ContinueEntry, Boolean) -> Unit = { entry, fromStart ->
        val item = entry.item
        val progress = entry.progress
        val playId = resumePlayId(progress)
        // Blank is how the backend stores "no episode", and it is also the cache
        // key the picker wrote, so the two never disagree.
        val videoId = progress.videoId?.takeIf { it.isNotBlank() }
        if (playId == null) {
            // A series row without its episode id cannot address a stream.
            navigator.push(ContentRoute.Detail(item))
        } else {
            val season = progress.season
            val episode = progress.episode
            scope.launch {
                val playTitle =
                    if (season != null && episode != null) {
                        "${item.name} · ${getString(Res.string.detail_episode_code, season.number, episode.number)}"
                    } else {
                        item.name
                    }
                // The cache is keyed by playback's own identity, the same one the
                // picker wrote when the stream was chosen.
                val cached = runCatchingCancellable { services.streamCache.find(progress.contentId, videoId) }.getOrNull()
                val fresh = cached?.takeIf { it.isFresh(settings.streamUrlCache, Clock.System.now()) }
                if (fresh == null) {
                    cached?.let { runCatchingCancellable { services.streamCache.remove(progress.contentId, videoId) } }
                    onOpenStreams(
                        StreamsRequest(
                            type = progress.contentType,
                            id = playId,
                            title = playTitle,
                            year = item.year?.toString(),
                            contentId = progress.contentId,
                            season = season,
                            episode = episode,
                            startFromBeginning = fromStart,
                        ),
                    )
                } else {
                    onPlayTarget(
                        PlaybackTarget.fromContent(
                            type = progress.contentType,
                            id = playId,
                            title = playTitle,
                            source = fresh.url,
                            httpHeaders = fresh.httpHeaders,
                            season = season,
                            episode = episode,
                            contentId = progress.contentId,
                            startFromBeginning = fromStart,
                        ),
                    )
                }
            }
        }
    }
    val onContinue: (ContinueEntry) -> Unit = { entry -> playEntry(entry, false) }
    val onContinueFromStart: (ContinueEntry) -> Unit = { entry -> playEntry(entry, true) }
    val onRemoveContinue: (ContinueEntry) -> Unit = { entry ->
        scope.launch {
            runCatchingCancellable { services.progress.remove(entry.progress.contentId) }
                .onSuccess { services.playbackUpdates.notifyClosed() }
                .onFailure { snackbar?.showSnackbar(removeContinueError) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeContent(
            state = state,
            posterSize = settings.posterSize,
            hideLabels = settings.hideLabels,
            showCatalogType = settings.showCatalogType,
            savedIds = state.savedIds,
            busyIds = state.savingIds,
            onOpenDetail = { navigator.push(ContentRoute.Detail(it)) },
            onOpenPreview = { navigator.push(ContentRoute.Detail(LibraryItem.fromPreview(it))) },
            onOpenCatalog = { navigator.push(ContentRoute.Catalog(it)) },
            onToggleSaved = viewModel::toggleSaved,
            onWatchedChanged = viewModel::applyWatched,
            onContinue = onContinue,
            onContinueFromStart = onContinueFromStart,
            onRemoveContinue = onRemoveContinue,
            onHeroArtworkSettled = { heroArtworkReady = true },
            onPlayPreview = { preview ->
                onOpenStreams(
                    StreamsRequest(
                        type = preview.type,
                        id = preview.id,
                        title = preview.name,
                        year = yearOf(preview.releaseInfo),
                    ),
                )
            },
        )
        // The feed is already composed behind this (so its images are loading);
        // the screen stays covered until the billboard can be drawn whole.
        if (waitsForHero && !heroWaitExpired) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { HomeLoading() }
        }
        if (state.isSyncingWatched) {
            // A wide window already clears the system bars for the section; a
            // phone keeps the billboard full-bleed, so the indicator clears the
            // status bar itself.
            val topInset = if (windowSize == WindowSize.COMPACT) safeTopInset else 0.dp
            Box(Modifier.align(Alignment.TopEnd).padding(top = topInset + AppSpacing.md, end = AppSpacing.xl)) {
                SyncingIndicator()
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    posterSize: PosterSize,
    hideLabels: Boolean,
    showCatalogType: Boolean,
    savedIds: Set<String>,
    busyIds: Set<String>,
    onOpenDetail: (LibraryItem) -> Unit,
    onOpenPreview: (MetaPreview) -> Unit,
    onOpenCatalog: (CatalogRef) -> Unit,
    onPlayPreview: (MetaPreview) -> Unit,
    onContinue: (ContinueEntry) -> Unit,
    onContinueFromStart: (ContinueEntry) -> Unit,
    onRemoveContinue: (ContinueEntry) -> Unit,
    onToggleSaved: (MetaPreview) -> Unit,
    onWatchedChanged: (String, Boolean) -> Unit,
    onHeroArtworkSettled: () -> Unit,
) {
    val windowSize = LocalWindowSize.current
    when {
        state.error != null && state.rows.isEmpty() && state.continueWatching.isEmpty() ->
            Message(
                icon = MaterialIcons.Error,
                title = stringResource(Res.string.home_error_title),
                message = stringResource(state.error.toUserMessage()),
            )
        // The whole first load is covered by the loading screen unless the previous
        // feed was painted from disk, in which case the rows are already there and
        // the refresh behind replaces them without coming back here.
        state.rows.isEmpty() && (state.isLoading || !state.hasLoaded) ->
            HomeLoading()
        state.rows.isEmpty() && state.continueWatching.isEmpty() ->
            Message(
                icon = MaterialIcons.Compass,
                title = stringResource(Res.string.empty_nothing_to_show),
                message = stringResource(Res.string.home_empty_message),
            )
        else -> {
            val compact = windowSize == WindowSize.COMPACT
            val heroHeight = homeHeroHeight()
            // Pull the first section up over the hero's faded bottom, so it does
            // not sit a full header gap below it.
            val liftPx = with(LocalDensity.current) { HOME_CONTENT_LIFT.roundToPx() }
            val hasContinue = state.continueWatching.isNotEmpty()
            // Virtualized: a phone with many addons composes only the sections on
            // screen instead of every rail at once. Lifting the first section
            // shifts every later one up by the same amount.
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "home-hero") {
                    when {
                        state.featured.isNotEmpty() && !compact ->
                            WideHero(
                                items = state.featured,
                                savedIds = savedIds,
                                busyIds = busyIds,
                                onOpen = onOpenPreview,
                                onPlay = onPlayPreview,
                                onToggleSaved = onToggleSaved,
                                height = heroHeight,
                                onArtworkSettled = onHeroArtworkSettled,
                            )
                        state.featured.isNotEmpty() ->
                            Hero(
                                items = state.featured,
                                savedIds = savedIds,
                                busyIds = busyIds,
                                onOpen = onOpenPreview,
                                onPlay = onPlayPreview,
                                onToggleSaved = onToggleSaved,
                                onArtworkSettled = onHeroArtworkSettled,
                            )
                        compact ->
                            // No billboard to tuck under the status bar: reserve its height.
                            Spacer(Modifier.height(safeTopInset))
                    }
                }
                if (hasContinue) {
                    item(key = "home-continue") {
                        Column(Modifier.pullUp(liftPx)) {
                            SectionHeader(title = stringResource(Res.string.home_continue_watching))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
                                horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
                            ) {
                                items(state.continueWatching) { entry ->
                                    val cardWidth = if (compact) CompactContinueWidth else ContinueWidth
                                    ContinueCard(
                                        entry = entry,
                                        width = cardWidth,
                                        onTap = { onContinue(entry) },
                                        actions =
                                            MediaActions(
                                                item = entry.item,
                                                saved = entry.item.contentId in state.savedIds,
                                                watched = entry.item.contentId in state.watchedIds,
                                                continueWatching =
                                                    ContinueWatchingActions(
                                                        onPlayFromBeginning = { onContinueFromStart(entry) },
                                                        onRemove = { onRemoveContinue(entry) },
                                                    ),
                                                onMoreInfo = { onOpenDetail(entry.item) },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                itemsIndexed(state.rows, key = { _, row -> "row:${row.ref.key}" }) { index, row ->
                    Column(Modifier.pullUp(if (index == 0 && !hasContinue) liftPx else 0)) {
                        val typeLabel = contentTypeLabel(row.ref.type)
                        SectionHeader(
                            title = if (showCatalogType && typeLabel != null) "${row.title} · $typeLabel" else row.title,
                            onSeeAll = { onOpenCatalog(row.ref) },
                        )
                        val cardWidth = posterSize.cellWidth(windowSize)
                        LazyRow(
                            modifier = Modifier.height(posterCardHeight(cardWidth, hideLabels)),
                            contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
                            horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
                        ) {
                            items(row.items) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    caption = item.releaseInfo,
                                    width = cardWidth,
                                    watched = item.id in state.watchedIds,
                                    hideLabels = hideLabels,
                                    onTap = { onOpenPreview(item) },
                                    actions =
                                        MediaActions(
                                            item = LibraryItem.fromPreview(item),
                                            saved = item.id in savedIds,
                                            watched = item.id in state.watchedIds,
                                            onMoreInfo = { onOpenPreview(item) },
                                            onWatchedChanged = { onWatchedChanged(item.id, it) },
                                        ),
                                )
                            }
                        }
                    }
                }
                item(key = "home-bottom") { Spacer(Modifier.height(AppSpacing.xl)) }
            }
        }
    }
}

/** Full-screen loading indicator shown until every catalog has been fetched. */
@Composable
private fun HomeLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Share of the screen work-area height the wide billboard occupies.
 *
 * The billboard is sized from the screen, not the window, so a horizontal or
 * vertical resize changes how much artwork is revealed, never how tall the hero
 * is: it keeps the height it has on a maximized window. A compact window keeps
 * the aspect-ratio [Hero] instead.
 */
private const val HOME_HERO_SCREEN_FRACTION = 0.55f

/** Floor for the billboard height, so a short screen cannot shrink it under its copy. */
private val HOME_HERO_MIN_HEIGHT = 480.dp

/**
 * Height of the wide billboard.
 *
 * Taken from the screen work area, not the window, so a resize changes how much
 * artwork is revealed, not how tall the billboard is. The compact billboard
 * keeps its own aspect ratio ([HERO_COMPACT_HEIGHT_RATIO]).
 */
@Composable
private fun homeHeroHeight(): Dp {
    val screenHeight = LocalAppServices.current.window.screenWorkAreaHeight
    val referenceHeight =
        if (screenHeight.value.isFinite() && screenHeight > 0.dp) {
            screenHeight
        } else {
            LocalWindowHeight.current
        }
    return if (referenceHeight.value.isFinite()) {
        (referenceHeight * HOME_HERO_SCREEN_FRACTION).coerceAtLeast(HOME_HERO_MIN_HEIGHT)
    } else {
        HOME_HERO_MIN_HEIGHT
    }
}

/**
 * How far the rows below the hero are raised, so the first section sits closer to
 * the hero's faded bottom instead of a full header gap below it. `0.dp` restores
 * the old spacing; larger values pull the rows further up and overlap the hero.
 * It must stay clear of the paginator and the copy, which sit at the billboard's
 * bottom (the phone's paginator is only ~32dp above the edge).
 */
private val HOME_CONTENT_LIFT = 32.dp

/**
 * How long Home waits for the billboard artwork before showing the feed anyway.
 *
 * The loading screen exists to avoid a black hero that fills in a moment later; a
 * slow or unreachable image is not worth an indefinite spinner, so after this the
 * feed wins over a perfect first frame.
 */
private val HERO_ARTWORK_TIMEOUT = 4.seconds

/**
 * Lifts the content by [px] pixels and shrinks the reported height by the same
 * amount, so it overlaps the previous sibling instead of leaving a gap.
 */
private fun Modifier.pullUp(px: Int): Modifier =
    if (px == 0) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) {
                placeable.place(0, -px)
            }
        }
    }

/** Extracts the four-digit year from a Stremio `releaseInfo` string. */
internal fun yearOf(releaseInfo: String?): String? = releaseYear(releaseInfo)?.toString()
