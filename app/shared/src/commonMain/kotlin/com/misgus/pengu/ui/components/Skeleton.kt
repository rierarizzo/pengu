package com.misgus.pengu.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.misgus.pengu.ui.theme.LocalMotionEnabled
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.gridColumnCount
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.posterCardHeight
import com.misgus.pengu.ui.theme.screenPadding
import kotlin.math.ceil

/**
 * Shimmer colours from the theme, so the sweep matches the surface it sits on.
 *
 * Built once per theme change instead of on every frame.
 */
@Composable
private fun shimmerColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.surface, scheme.surfaceContainerHigh) {
        listOf(scheme.surface, scheme.surfaceContainerHigh, scheme.surface)
    }
}

/**
 * Full-screen grid of poster skeletons for a loading library or catalog.
 *
 * The cell count is derived from the available space, so the placeholders always
 * cover the whole viewport instead of stopping after a fixed number of rows. One
 * shimmer transition drives every cell: the progress is a [State] read in the
 * draw phase, so the grid repaints without recomposing per frame.
 */
@Composable
fun SkeletonPosterGrid(
    hideLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = rememberShimmerProgress()
    val windowSize = LocalWindowSize.current
    val screenPadding = windowSize.screenPadding
    val spacing = windowSize.gridSpacing
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = gridColumnCount(windowSize, maxWidth, screenPadding, spacing)
        // The cells stretch to fill the row, so measure with the real cell width
        // to know how tall a row is.
        val cell = (maxWidth - screenPadding * 2 - spacing * (columns - 1)) / columns
        val rowHeight = posterCardHeight(cell, hideLabels)
        val rows = rowsThatFit(maxHeight, rowHeight, screenPadding, spacing)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(windowSize.screenPadding),
            verticalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
            userScrollEnabled = false,
        ) {
            items(columns * rows) {
                Box(Modifier.fillMaxWidth().height(rowHeight)) {
                    SkeletonBox(progress = progress, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** How many [rowHeight] rows are needed to cover [height], including the spacing. */
private fun rowsThatFit(
    height: Dp,
    rowHeight: Dp,
    screenPadding: Dp,
    spacing: Dp,
): Int {
    val usable = height - screenPadding * 2 + spacing
    return maxOf(1, ceil(usable / (rowHeight + spacing)).toInt())
}

/** A placeholder block that shimmers while content loads. */
@Composable
fun SkeletonBox(
    progress: State<Float>,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val shimmer = shimmerColors()
    if (LocalMotionEnabled.current) {
        Box(
            modifier.clip(shape).drawBehind {
                val p = progress.value
                val width = size.width
                val brush =
                    Brush.linearGradient(
                        colors = shimmer,
                        start = Offset(p * width, 0f),
                        end = Offset((p + 0.6f) * width, 0f),
                    )
                drawRect(brush)
            },
        )
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surface))
    }
}

/** A single infinite 0..1 sweep shared by every skeleton on screen. */
@Composable
fun rememberShimmerProgress(): State<Float> {
    // Do not spin an infinite animation when motion is off.
    if (!LocalMotionEnabled.current) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "skeletonProgress",
    )
}
