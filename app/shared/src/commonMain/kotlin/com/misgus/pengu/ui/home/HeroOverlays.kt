package com.misgus.pengu.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.ui.components.LogoMaxBitmapSize
import com.misgus.pengu.ui.components.LogoTrimTransformation
import com.misgus.pengu.ui.components.handCursor
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.motionTween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/*
 * The hero overlays that are not per-page artwork or copy: the swipe forwarder,
 * the paginator and the artwork preloader. They only read the pager state, so
 * they live apart from the carousel that owns it.
 */

/**
 * Forwards a horizontal drag on the action row to the pager.
 *
 * The copy layers sit above the pager, so a drag that starts on one of their
 * buttons is caught by the button and never reaches the pager: siblings do not
 * share the hit path. This runs in the initial pass, drives the pager with the
 * raw delta and snaps on release, while a tap still falls through to the button.
 */
internal fun Modifier.heroActionDrag(
    pagerState: PagerState,
    scope: CoroutineScope,
    onManualScroll: () -> Unit,
): Modifier =
    if (pagerState.pageCount <= 1) {
        this
    } else {
        pointerInput(pagerState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val thresholdPx = HERO_SWIPE_THRESHOLD.toPx()
                val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                val startPage = pagerState.currentPage
                var totalX = 0f
                var totalY = 0f
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocity.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (dragging) {
                            onManualScroll()
                            val flung = velocity.calculateVelocity().x.absoluteValue > HERO_SWIPE_VELOCITY_THRESHOLD
                            val committed = totalX.absoluteValue > thresholdPx || flung
                            val target =
                                when {
                                    !committed -> startPage
                                    totalX > 0f -> startPage - 1
                                    totalX < 0f -> startPage + 1
                                    else -> startPage
                                }
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        break
                    }
                    val delta = change.position - change.previousPosition
                    totalX += delta.x
                    totalY += delta.y
                    if (!dragging) {
                        val horizontal = totalX.absoluteValue > viewConfiguration.touchSlop && totalX.absoluteValue > totalY.absoluteValue
                        val vertical = totalY.absoluteValue > viewConfiguration.touchSlop && totalY.absoluteValue > totalX.absoluteValue
                        when {
                            vertical -> break
                            horizontal -> {
                                dragging = true
                                onManualScroll()
                            }
                            else -> continue
                        }
                    }
                    pagerState.dispatchRawDelta(-delta.x)
                    change.consume()
                }
            }
        }
    }

/**
 * Opens [onOpen] when the surface is tapped, so the whole billboard reads as
 * the "more info" target now that the home hero has no info action.
 *
 * Runs in the main pass and never consumes, so a swipe still reaches the pager
 * or the action row's drag forwarder. Main runs child-to-parent, so by the time
 * this sees the release every control under the pointer has handled it already:
 * a release a child consumed (the action buttons, the play call to action) is
 * ignored, so tapping a control never opens the title as well. A release that
 * followed more than the touch slop is not a tap either.
 */
@Composable
internal fun Modifier.heroTap(onOpen: () -> Unit): Modifier {
    // The copy and actions are rebuilt per page, so read the latest handler
    // instead of restarting the gesture detector on every recomposition.
    val open = rememberUpdatedState(onOpen)
    // Tapping the billboard opens the title, so the whole surface is one target.
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!moved && !change.isConsumed) open.value()
                    break
                }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
            }
        }
    }.handCursor()
}

/** Carousel paginator: an equal dot per page, grey when idle and white when active. */
@Composable
internal fun HeroPaginator(
    pageCount: Int,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HERO_PAGINATOR_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until pageCount) {
            val selected = index == pagerState.currentPage % pageCount
            val color by animateColorAsState(
                targetValue =
                    if (selected) {
                        AppColors.heroIndicatorActive
                    } else {
                        AppColors.heroIndicatorInactive
                    },
                animationSpec = motionTween(HERO_PAGINATOR_ANIMATION_MS),
                label = "heroIndicatorColor",
            )
            Box(
                modifier =
                    Modifier
                        .size(HERO_PAGINATOR_DOT_SIZE)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}

/**
 * Warms Coil's cache with the next hero artwork, so the auto-advance and a
 * forward swipe paint from memory instead of starting a network round-trip
 * mid-gesture.
 *
 * Only the immediate neighbour is warmed, re-warmed as the page advances:
 * enqueuing the whole set at once spiked the decoder (six full-size backdrops
 * plus six logos) and competed with the visible page on entry. The request is
 * cancelled with the composition if the items or the sizes change.
 */
@Composable
internal fun HeroArtworkPreloader(
    items: List<MetaPreview>,
    pagerState: PagerState,
    backdropWidthPx: Int,
    backdropHeightPx: Int,
    wide: Boolean,
) {
    if (items.size <= 1) return
    val context = LocalPlatformContext.current
    // Reading `currentPage` here re-arms the preload only when the page settles,
    // not on every scroll frame.
    val nextPage = (pagerState.currentPage + 1) % items.size
    LaunchedEffect(items, nextPage, backdropWidthPx, backdropHeightPx, wide) {
        val loader = SingletonImageLoader.get(context)
        val item = items[nextPage]
        val backdrop = heroArtwork(item, wide)
        backdrop?.let { art ->
            loader.enqueue(
                ImageRequest
                    .Builder(context)
                    .data(art.url)
                    .size(backdropWidthPx, backdropHeightPx)
                    .build(),
            )
        }
        item.logo?.let { url ->
            loader.enqueue(
                ImageRequest
                    .Builder(context)
                    .data(url)
                    // The same request the billboard makes, size included: a
                    // different size is a different cache entry, and the preload
                    // would be thrown away.
                    .size(LogoMaxBitmapSize)
                    .maxBitmapSize(LogoMaxBitmapSize)
                    .transformations(LogoTrimTransformation)
                    .build(),
            )
        }
    }
}
