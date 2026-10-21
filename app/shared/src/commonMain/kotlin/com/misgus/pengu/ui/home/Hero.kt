package com.misgus.pengu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.ui.components.HeroActionRowInset
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LocalMotionEnabled
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.POSTER_ART_ASPECT
import com.misgus.pengu.ui.theme.WindowSize
import com.misgus.pengu.ui.theme.motionSpring
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/*
 * The home billboard. This file owns the two entry points (`Hero` for compact
 * windows, `WideHero` for desktop), the pager that drives them, and the shared
 * geometry. The per-page artwork and copy live in `HeroLayers.kt`, and the
 * overlays (swipe, paginator, preloader) in `HeroOverlays.kt`.
 */

/** Share of the window width the desktop artwork occupies. */
internal const val HERO_IMAGE_WIDTH_FRACTION = 0.75f

/** Aspect ratio used until the artwork has decoded, so the frame has a height. */
internal const val HERO_FALLBACK_ASPECT_RATIO = 1920f / 823f

/**
 * Height of the compact billboard as a multiple of its width.
 *
 * A phone cannot show the wide desktop composition, so it shows a portrait,
 * language-neutral poster whole: the frame is exactly the poster's own height
 * (`1 / POSTER_ART_ASPECT`; a 2:3 poster is 1.5 times its width), so nothing is
 * cropped and no strip is left over. The top wash still darkens the artwork, but
 * never all the way to the page background, so the cover stays visible up to the
 * top edge; only the bottom fades out, to meet the section below.
 */
internal const val HERO_COMPACT_HEIGHT_RATIO = 1f / POSTER_ART_ASPECT

/** Widest the copy column ever grows, so the synopsis keeps a comfortable measure. */
internal val HERO_CONTENT_MAX_WIDTH = 840.dp

/**
 * Horizontal inset of the copy block (title, metadata and synopsis).
 *
 * The play call to action is a transparent text button, whose container still
 * pads its icon and label in from the row edge; the copy takes the same inset so
 * the title block and the play glyph share a left edge.
 */
internal val HERO_COPY_TEXT_INSET = AppSpacing.md

/** Gap between the bottom of the billboard and the copy actions. */
internal val HERO_CONTENT_BOTTOM_MARGIN = 100.dp

/**
 * Gap between the bottom of the compact billboard and the action row.
 *
 * The row sits low, just above the paginator dots and deep in the bottom fade:
 * far enough that the buttons do not crowd the dots, but lower than the copy
 * itself, so the actions read as a footer under the title block instead of as
 * another line of it (the wide billboard has far more room below its row).
 */
internal val HERO_COMPACT_CONTENT_BOTTOM_MARGIN = 48.dp

/**
 * Vertical rhythm of the copy column, in order: title, metadata, synopsis,
 * actions. The synopsis sits above the actions here, while the detail billboard
 * keeps actions first; the values are tighter, since the home copy sits over
 * cropped artwork.
 */
internal val HERO_LOGO_METADATA_SPACE = 12.dp
internal val HERO_METADATA_SYNOPSIS_SPACE = 18.dp

/**
 * Same rhythm as the metadata-to-synopsis gap, discounting the transparent
 * padding of the action row ([HeroActionRowInset]).
 *
 * The row leads with a transparent play control, so, like an icon-only row, its
 * optical gap is the value written minus that padding.
 */
internal val HERO_SYNOPSIS_ACTIONS_SPACE = HERO_METADATA_SYNOPSIS_SPACE - HeroActionRowInset

/**
 * Tighter vertical rhythm on a phone, where the copy shares a shorter band.
 *
 * The actions follow the metadata directly (no synopsis), so their spacer
 * discounts [HeroActionRowInset] too. It is wider than the wide billboard's
 * text gaps, since the row is a full-width footer and the extra space reads as
 * a separation from the copy above rather than a missing synopsis; the metadata
 * stays a comfortable step below the title.
 */
internal val HERO_COMPACT_LOGO_METADATA_SPACE = 8.dp
internal val HERO_COMPACT_METADATA_ACTIONS_SPACE = (28.dp - HeroActionRowInset).coerceAtLeast(0.dp)

/**
 * Widest the hero copy (not the artwork) slides during a page change. Small on
 * purpose: just enough to read the swipe direction, not a full-screen slide.
 */
internal val HERO_CONTENT_SLIDE_MAX = 64.dp

/**
 * Backdrop drift against a swipe, as a fraction of the page width, and the
 * overscan that keeps the drift from exposing an edge.
 *
 * The two slides do not dissolve perfectly on top of each other: a small drift
 * and zoom make the change read as layers instead of a flat double exposure.
 */
internal const val HERO_BACKDROP_PARALLAX = 0.055f
internal const val HERO_BACKDROP_SCALE = 1.14f

/**
 * How far a drag on the action row must travel, or how fast it must fling, to
 * commit to the next slide instead of snapping back.
 */
internal val HERO_SWIPE_THRESHOLD = 48.dp
internal const val HERO_SWIPE_VELOCITY_THRESHOLD = 300f

/**
 * How long a slide stays before the carousel advances to the next one.
 *
 * In the common 5-8 s range for auto-rotating carousels (Swiper defaults to 3 s,
 * Bootstrap to 5 s): long enough to read the title, short enough to keep moving.
 */
internal val HERO_AUTO_ADVANCE_INTERVAL = 7.seconds

/**
 * How long auto-advance stays paused after the user swipes the carousel.
 *
 * A manual change means the user picked that slide, so the rotation yields to
 * them instead of moving the billboard under their tap. A minute is long enough
 * to read the copy and use the actions without it shifting, and short enough
 * that the hero starts moving again on its own once they go idle; it is the
 * common "resume after interaction" window for auto-rotating carousels.
 */
internal val HERO_MANUAL_COOLDOWN = 60.seconds

/** Carousel paginator: dot size, gap, bottom inset and colour animation. */
internal val HERO_PAGINATOR_DOT_SIZE = 7.dp
internal val HERO_PAGINATOR_GAP = 6.dp
internal val HERO_PAGINATOR_BOTTOM_MARGIN = 24.dp

/** On a phone the paginator sits higher, clear of the gesture bar. */
internal val HERO_COMPACT_PAGINATOR_BOTTOM_MARGIN = 32.dp
internal const val HERO_PAGINATOR_ANIMATION_MS = 250

/**
 * First page of the infinite hero pager.
 *
 * A large multiple of [itemCount] near the middle of the range, so there is room
 * to swipe both ways and the auto-advance always moves forward: the last slide
 * no longer scrolls back through every page to reach the first. `page %
 * itemCount` maps a page back to its item.
 */
private fun heroStartingPage(itemCount: Int): Int = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % itemCount

/**
 * Compact / Android billboard: in the scroll flow, full-bleed and cropped.
 *
 * A phone cannot show the wide desktop composition, so it crops the same
 * backdrop into a tall band, left-aligns the copy like the detail billboard and
 * the rest of the content, washes it only with a bottom fade, and drops the
 * synopsis, which has no room to read on a small screen.
 */
@Composable
internal fun Hero(
    items: List<MetaPreview>,
    savedIds: Set<String>,
    busyIds: Set<String>,
    onOpen: (MetaPreview) -> Unit,
    onPlay: (MetaPreview) -> Unit,
    onToggleSaved: (MetaPreview) -> Unit,
    onArtworkSettled: () -> Unit = {},
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = maxWidth * HERO_COMPACT_HEIGHT_RATIO
        HeroCarousel(
            items = items,
            height = height,
            pageWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) },
            wide = false,
            savedIds = savedIds,
            busyIds = busyIds,
            backdropWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) },
            backdropHeightPx = with(density) { height.roundToPx().coerceAtLeast(1) },
            onBackdropAspect = { _, _ -> },
            onOpen = onOpen,
            onPlay = onPlay,
            onToggleSaved = onToggleSaved,
            onArtworkSettled = onArtworkSettled,
        )
    }
}

/**
 * Desktop billboard: in the scroll flow, with the artwork scaled to
 * [HERO_IMAGE_WIDTH_FRACTION] of the window and pinned to the top-right.
 *
 * [height] is a fixed billboard height, so a horizontal resize changes how much
 * artwork is shown, not how tall the billboard is. A single aspect keeps the
 * frame (and the sections below it) stable while the pages swipe; the first
 * decoded artwork sets it.
 */
@Composable
internal fun WideHero(
    items: List<MetaPreview>,
    savedIds: Set<String>,
    busyIds: Set<String>,
    onOpen: (MetaPreview) -> Unit,
    onPlay: (MetaPreview) -> Unit,
    onToggleSaved: (MetaPreview) -> Unit,
    height: Dp,
    onArtworkSettled: () -> Unit = {},
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    var aspect by remember(items) { mutableStateOf(HERO_FALLBACK_ASPECT_RATIO) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frameHeight = height
        // Decode the full artwork at its display width; if the frame ends up
        // taller than the artwork's aspect, decode to the frame height so `Crop`
        // does not upscale.
        val artworkHeight = maxOf((maxWidth * HERO_IMAGE_WIDTH_FRACTION) / aspect, frameHeight)
        HeroCarousel(
            items = items,
            height = frameHeight,
            pageWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) },
            wide = true,
            savedIds = savedIds,
            busyIds = busyIds,
            backdropWidthPx = with(density) { (maxWidth * HERO_IMAGE_WIDTH_FRACTION).roundToPx().coerceAtLeast(1) },
            backdropHeightPx = with(density) { artworkHeight.roundToPx().coerceAtLeast(1) },
            onBackdropAspect = { page, ratio -> if (page == 0) aspect = ratio },
            onOpen = onOpen,
            onPlay = onPlay,
            onToggleSaved = onToggleSaved,
            onArtworkSettled = onArtworkSettled,
        )
    }
}

/** Shared pager, paginator and preloader; [wide] selects the artwork placement. */
@Composable
private fun HeroCarousel(
    items: List<MetaPreview>,
    height: Dp,
    pageWidthPx: Int,
    wide: Boolean,
    savedIds: Set<String>,
    busyIds: Set<String>,
    backdropWidthPx: Int,
    backdropHeightPx: Int,
    onBackdropAspect: (Int, Float) -> Unit,
    onOpen: (MetaPreview) -> Unit,
    onPlay: (MetaPreview) -> Unit,
    onToggleSaved: (MetaPreview) -> Unit,
    onArtworkSettled: () -> Unit,
) {
    // Infinite pager so the auto-advance and a swipe wrap around instead of
    // scrolling back through every page from the last slide to the first. The
    // item shown is `page % items.size`; recreating on a size change keeps the
    // current page valid when the featured set rotates.
    val pagerState =
        key(items.size) {
            rememberPagerState(
                initialPage = if (items.size > 1) heroStartingPage(items.size) else 0,
                pageCount = { if (items.size > 1) Int.MAX_VALUE else 1 },
            )
        }
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val density = LocalDensity.current
    val motionEnabled = LocalMotionEnabled.current
    val snapSpec = motionSpring()
    val scope = rememberCoroutineScope()
    // Monotonic mark of the last manual interaction; auto-advance holds off until
    // a full cooldown has passed, then resumes once the user goes idle.
    var lastManualInteraction by remember(pagerState) { mutableStateOf<TimeMark?>(null) }
    // Set while our own animation drives the pager, so the watcher below can tell
    // an auto-advance from something the user did.
    val autoScrolling = remember { mutableStateOf(false) }
    // Any scroll we did not start counts as manual input. Watching
    // `isScrollInProgress` covers both a drag on a phone and a wheel / trackpad
    // swipe on desktop; `interactionSource` alone would miss the wheel, since it
    // only dispatches drag events.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { inProgress ->
            if (inProgress && !autoScrolling.value) {
                lastManualInteraction = TimeSource.Monotonic.markNow()
            }
        }
    }
    // Auto-advance. The effect is keyed on stable inputs on purpose: keying it on
    // `isScrollInProgress` would cancel it (and the animation) the moment the
    // animation sets that flag itself, so the page would never move. A tick that
    // lands while the user is dragging, or while the post-swipe cooldown is still
    // running, is skipped instead.
    LaunchedEffect(pagerState, items.size, motionEnabled) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(HERO_AUTO_ADVANCE_INTERVAL)
            val sinceManual = lastManualInteraction?.elapsedNow()
            val coolingDown = sinceManual != null && sinceManual < HERO_MANUAL_COOLDOWN
            if (!pagerState.isScrollInProgress && !coolingDown) {
                autoScrolling.value = true
                try {
                    // A spring snap, like the rest of the app's larger surfaces,
                    // and a snap when motion is off.
                    pagerState.animateScrollToPage(
                        pagerState.currentPage + 1,
                        animationSpec = snapSpec,
                    )
                } finally {
                    autoScrolling.value = false
                }
            }
        }
    }

    // The page beside the settled one, if any. Reading the offset through
    // `derivedStateOf` keeps the layers from recomposing on every swipe frame:
    // the neighbour only changes when a page enters or leaves the viewport.
    val neighborPage by remember(pagerState) {
        derivedStateOf {
            when {
                pagerState.currentPageOffsetFraction > 0f -> pagerState.currentPage + 1
                pagerState.currentPageOffsetFraction < 0f -> pagerState.currentPage - 1
                else -> null
            }
        }
    }
    // Neighbour first and the settled page last, so the settled copy owns the hits.
    val layers = listOfNotNull(neighborPage, pagerState.currentPage)

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
            // The home hero carries no info action: tapping it opens the title.
            .heroTap(onOpen = { onOpen(items[pagerState.currentPage % items.size]) }),
    ) {
        // The pager is only a gesture and offset driver: its pages are empty, so
        // the artwork and the copy are drawn as separate layers below. One alpha
        // over a whole page forces the billboard into an offscreen layer every
        // frame; per-primitive alpha does not.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize()) {
            // Artwork first, then the washes once, then the copy: the wash is the
            // same for every slide, so it no longer draws per page.
            layers.forEach { page ->
                key(page) {
                    HeroBackdropLayer(
                        item = items[page % items.size],
                        wide = wide,
                        page = page,
                        pageWidthPx = pageWidthPx,
                        pagerState = pagerState,
                        onAspect = { ratio -> onBackdropAspect(page % items.size, ratio) },
                        // Only the settled page decides when the billboard is
                        // ready: a neighbour finishing first is not the frame the
                        // user is waiting for.
                        onSettled = { if (page == pagerState.currentPage) onArtworkSettled() },
                    )
                }
            }
            HeroWashes(wide)
            layers.forEach { page ->
                key(page) {
                    HeroCopy(
                        item = items[page % items.size],
                        compact = compact,
                        saved = items[page % items.size].id in savedIds,
                        savedBusy = items[page % items.size].id in busyIds,
                        onPlay = { onPlay(items[page % items.size]) },
                        onToggleSaved = { onToggleSaved(items[page % items.size]) },
                        pagerState = pagerState,
                        scope = scope,
                        onManualScroll = { lastManualInteraction = TimeSource.Monotonic.markNow() },
                        modifier =
                            Modifier.graphicsLayer {
                                // Deferred to the layer phase: the offset changes
                                // on every swipe frame, and an alpha over the
                                // copy alone stays off the whole-page layer.
                                alpha = heroLayerVisibility(pagerState, page)
                                translationX = -heroLayerOffset(pagerState, page) * HERO_CONTENT_SLIDE_MAX.toPx()
                            },
                    )
                }
            }
        }
        if (items.size > 1) {
            HeroPaginator(
                pageCount = items.size,
                pagerState = pagerState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (compact) HERO_COMPACT_PAGINATOR_BOTTOM_MARGIN else HERO_PAGINATOR_BOTTOM_MARGIN),
            )
        }
    }
    HeroArtworkPreloader(
        items = items,
        pagerState = pagerState,
        backdropWidthPx = backdropWidthPx,
        backdropHeightPx = backdropHeightPx,
        wide = wide,
    )
}

/** Signed distance of [page] from the settled page, in pages. */
internal fun heroLayerOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

/** Cross-fade alpha of [page] against the settled page. */
internal fun heroLayerVisibility(
    pagerState: PagerState,
    page: Int,
): Float = (1f - heroLayerOffset(pagerState, page).absoluteValue).coerceIn(0f, 1f)
