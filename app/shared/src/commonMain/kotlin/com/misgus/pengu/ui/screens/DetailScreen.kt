package com.misgus.pengu.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_back
import com.misgus.pengu.app.shared.generated.resources.action_stream_now
import com.misgus.pengu.app.shared.generated.resources.detail_episode_generic
import com.misgus.pengu.app.shared.generated.resources.detail_error_library
import com.misgus.pengu.app.shared.generated.resources.detail_error_watched
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.HeroActionRow
import com.misgus.pengu.ui.components.HeroActionRowInset
import com.misgus.pengu.ui.components.HeroActionState
import com.misgus.pengu.ui.components.HeroGenres
import com.misgus.pengu.ui.components.HeroSynopsis
import com.misgus.pengu.ui.components.MediaTitle
import com.misgus.pengu.ui.components.MetadataRow
import com.misgus.pengu.ui.components.RemoteImage
import com.misgus.pengu.ui.components.metadataParts
import com.misgus.pengu.ui.detail.DetailUiState
import com.misgus.pengu.ui.detail.DetailViewModel
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.LocalSnackbarHost
import com.misgus.pengu.ui.streams.StreamsRequest
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppSynopsis
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.WindowSize
import com.misgus.pengu.ui.theme.motionTween
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Title detail: an immersive hero, episodes and the extra sections. */
@Composable
fun DetailScreen(
    item: LibraryItem,
    onBack: () -> Unit,
    onOpenDetail: (LibraryItem) -> Unit,
    onOpenStreams: (StreamsRequest) -> Unit,
    knownSaved: Boolean = false,
) {
    val services = LocalAppServices.current
    val viewModel: DetailViewModel =
        viewModel(key = "detail-${item.contentId}") {
            DetailViewModel(services.metadata, services.tmdb, item)
        }
    LaunchedEffect(item.contentId) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current

    var saved by remember { mutableStateOf(knownSaved) }
    var savedKnown by remember { mutableStateOf(knownSaved) }
    var saving by remember { mutableStateOf(false) }
    var watched by remember { mutableStateOf(false) }
    var watchedKnown by remember { mutableStateOf(false) }

    LaunchedEffect(item.contentId) {
        // The two reads are independent: run them together so the wait is the
        // slowest one, not their sum. Each publishes as soon as it is ready.
        coroutineScope {
            launch {
                // Opened from the library: it is saved, no need to ask.
                if (!knownSaved) {
                    saved = runCatchingCancellable { services.library.contains(item.contentId) }.getOrDefault(false)
                }
                savedKnown = true
            }
            launch {
                watched =
                    runCatchingCancellable {
                        services.progress.watchedEntries(listOf(item.contentId)).any { it.isMarker }
                    }.getOrDefault(false)
                watchedKnown = true
            }
        }
    }

    // Resolved once, so the snackbars inside the click handlers stay composable-free.
    val libraryError = stringResource(Res.string.detail_error_library)
    val watchedError = stringResource(Res.string.detail_error_watched)

    val openStreams: (ContentType, String, String) -> Unit = { type, id, title ->
        onOpenStreams(
            StreamsRequest(
                type = type,
                id = id,
                title = title,
                year = state.year?.toString(),
                background = state.background,
            ),
        )
    }
    val episodeFallback = stringResource(Res.string.detail_episode_generic)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        // A full-height billboard pushes everything else out of view on a tall,
        // wide window, so it is capped there; narrower windows keep it immersive.
        val heroHeight =
            if (LocalWindowSize.current == WindowSize.EXPANDED) {
                minOf(viewportHeight, DETAIL_HERO_MAX_HEIGHT)
            } else {
                viewportHeight
            }
        val scrollState = rememberScrollState()
        // The artwork is a fixed layer, so its darkening spans the whole page:
        // bright at the top, near-black at the end, never fully black.
        val fade by remember {
            derivedStateOf {
                val max = scrollState.maxValue
                if (max <= 0) 0f else (scrollState.value.toFloat() / max).coerceIn(0f, 1f)
            }
        }

        Box(Modifier.fillMaxSize()) {
            // Fixed below the scrolling content: the artwork follows the page
            // instead of leaving with the hero.
            DetailBackdrop(state = state, fade = fade)

            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                HeroContent(
                    state = state,
                    height = heroHeight,
                    onPlay = {
                        // Prefer a regular episode: season 0 is specials.
                        val first =
                            state.episodes
                                .filter { (it.season?.number ?: 0) > 0 }
                                .minByOrNull { (it.season?.number ?: 0) * 1000 + (it.episode?.number ?: it.number ?: 0) }
                                ?: state.episodes.firstOrNull()
                        if (state.isSeries && first != null) {
                            openStreams(ContentTypes.SERIES, first.id, "${state.name} · ${first.title ?: episodeFallback}")
                        } else {
                            openStreams(item.contentType, state.contentId, state.name)
                        }
                    },
                    actions =
                        HeroActionState(
                            saved = saved,
                            savedBusy = !savedKnown || saving,
                            savedLoading = !savedKnown,
                            watched = watched,
                            watchedBusy = !watchedKnown,
                            watchedLoading = !watchedKnown,
                        ),
                    onToggleSaved = {
                        if (saving) return@HeroContent
                        saving = true
                        scope.launch {
                            runCatchingCancellable {
                                if (saved) {
                                    services.library.remove(item.contentId)
                                } else {
                                    services.library.add(item)
                                }
                            }.onSuccess { saved = !saved }
                                .onFailure { snackbar?.showSnackbar(libraryError) }
                            saving = false
                        }
                    },
                    onToggleWatched = {
                        if (!watchedKnown) return@HeroContent
                        watchedKnown = false
                        scope.launch {
                            runCatchingCancellable {
                                if (watched) {
                                    services.progress.unmarkWatched(item.contentId)
                                } else {
                                    services.progress.markWatched(item.contentId, item.contentType)
                                }
                            }.onSuccess { watched = !watched }
                                .onFailure { snackbar?.showSnackbar(watchedError) }
                            watchedKnown = true
                        }
                    },
                )

                DetailScrollContent(
                    state = state,
                    item = item,
                    openStreams = openStreams,
                    onOpenDetail = onOpenDetail,
                )
            }

            // Fixed top scrim: guarantees a dark strip behind the back button,
            // even when section titles scroll underneath it.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(TOP_SCRIM_HEIGHT)
                    .background(AppScrim.detailTop),
            )

            // Fixed, so it stays reachable while the page scrolls. It uses the
            // shared icon button as the player and the catalog; the fixed top
            // scrim is what keeps it legible.
            AppIconButton(
                icon = FluentIcons.ArrowBack,
                contentDescription = stringResource(Res.string.action_back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(AppSpacing.md),
            )
        }
    }
}

@Composable
private fun DetailBackdrop(
    state: DetailUiState,
    fade: Float,
) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    // A phone crops a wide backdrop too aggressively, so it prefers the
    // language-neutral poster; the wide billboard keeps the backdrop. Nothing is
    // painted while the enrichment is in flight, so the page never shows the
    // addon's image and then swaps it for TMDB's.
    val artwork =
        when {
            state.isResolvingArtwork -> null
            compact -> state.textlessPoster ?: state.background ?: state.poster
            else -> state.background ?: state.poster
        }
    // The fallback is not the final artwork, so it is not painted as if it were:
    // the image fades in when it is the one that stays.
    val imageAlpha by animateFloatAsState(
        targetValue = if (artwork.isNullOrEmpty()) 0f else 1f,
        animationSpec = motionTween(durationMillis = AppMotion.SLOW),
        label = "backdropAlpha",
    )
    Box(Modifier.fillMaxSize()) {
        // Dark base so a slow or failed image never shows a bright gap; the
        // artwork fills the box on top when it can.
        Box(Modifier.fillMaxSize().background(AppScrim.detailBackdrop))
        if (!artwork.isNullOrEmpty()) {
            RemoteImage(
                url = artwork,
                contentDescription = null,
                fadeIn = true,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha },
            )
        }
        // A phone only darkens the bottom, where the copy sits: a left-to-right
        // scrim there washed out the whole right side of the artwork.
        if (!compact) {
            Box(Modifier.fillMaxSize().background(AppScrim.mediaHorizontal))
        }
        Box(Modifier.fillMaxSize().background(AppScrim.mediaBottom))
        // Scroll-linked darkening over the whole viewport, so the artwork stays
        // behind the content all the way down, never fully black. No blur: a
        // full-viewport blur re-renders the whole screen on every scroll frame
        // (and is ignored below Android 12), so the artwork only darkens.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = HERO_FADE_MAX * fade)))
    }
}

@Composable
private fun HeroContent(
    state: DetailUiState,
    height: Dp,
    actions: HeroActionState,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        // Nothing in the hero is painted until its source is final, so no title,
        // logo, line or synopsis is swapped a moment after it appears. The action
        // buttons stay live the whole time: library and watched are unrelated to
        // which metadata answered.
        val settled = state.hasLoaded && !state.isResolvingArtwork
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(LocalWindowSize.current.screenPadding)
                    .widthIn(max = AppSizes.contentMaxWidth),
        ) {
            MediaTitle(state.name, state.logo, loading = !settled)
            Spacer(Modifier.height(AppSpacing.md))
            MetadataRow(
                if (settled) {
                    metadataParts(
                        state.releaseInfo,
                        runtime = state.runtime,
                        runtimeMinutes = state.runtimeMinutes,
                        rating = state.imdbRating,
                    )
                } else {
                    emptyList()
                },
            )
            // Discount the icon controls' transparent padding, so the row reads as
            // close to the metadata and the synopsis as the text gaps do.
            Spacer(Modifier.height(AppSpacing.lg - HeroActionRowInset))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            ) {
                ActionButton(
                    text = stringResource(Res.string.action_stream_now),
                    icon = FluentIcons.Play,
                    onClick = onPlay,
                    style = ActionButtonStyle.Accent,
                )
                HeroActionRow(
                    actions = actions,
                    onToggleSaved = onToggleSaved,
                    onToggleWatched = onToggleWatched,
                )
            }
            Spacer(Modifier.height(AppSpacing.lg - HeroActionRowInset))
            // Show only the settled description: the library preview can be a
            // different synopsis, and swapping it mid-view reads worse than a
            // short reserve. `minLines` keeps the layout stable.
            HeroSynopsis(
                text = state.description,
                loading = !settled,
                minLines = AppSynopsis.MAX_LINES,
                expandable = true,
            )
            if (settled && state.genres.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.sm))
                HeroGenres(state.genres)
            }
        }
    }
}

/** Height of the fixed top scrim, in dp. */
private val TOP_SCRIM_HEIGHT = 96.dp

/** Taller hero is capped here on an expanded window, so the content stays close. */
private val DETAIL_HERO_MAX_HEIGHT = 560.dp

/** How dark the artwork gets at the end of the page. */
private const val HERO_FADE_MAX = 0.9f
