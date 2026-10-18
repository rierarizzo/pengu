package com.misgus.pengu.ui.theme

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.misgus.pengu.core.repository.PosterSize

/**
 * Ratio of a poster's artwork on its own.
 *
 * A card draws its artwork at this ratio whatever its width, and a card with its
 * caption hidden collapses to just the artwork, so the freed caption space is
 * dropped instead of stretching the art and cropping its sides.
 */
const val POSTER_ART_ASPECT = 2f / 3f

/** How much a poster shrinks on a compact window (phone). */
private const val COMPACT_SCALE = 0.65f

/** The width the user chose, before the window is taken into account. */
private val PosterSize.nominalCellWidth: Dp
    get() =
        when (this) {
            PosterSize.DEFAULT -> 210.dp
            PosterSize.LARGE -> 230.dp
        }

/**
 * Card width of a horizontal poster row for [windowSize].
 *
 * The size setting only drives these rows (the Home catalogs, the search
 * results and "More like this"); grids keep a fixed layout through [gridCells].
 * Compact windows scale it down, so a phone shows bigger cards than the smallest
 * desktop choice while the user's default / large pick still makes a difference.
 */
fun PosterSize.cellWidth(windowSize: WindowSize): Dp =
    if (windowSize == WindowSize.COMPACT) nominalCellWidth * COMPACT_SCALE else nominalCellWidth

/** Posters per row a grid keeps on a phone, whatever the size setting says. */
private const val COMPACT_GRID_COLUMNS = 3

/** Base cell width a grid uses on a wider window, so the grid stays responsive. */
private val GRID_CELL_WIDTH = 210.dp

/**
 * Columns a poster grid lays out for [windowSize].
 *
 * Grids ignore the poster size setting: a phone keeps [COMPACT_GRID_COLUMNS]
 * per row and a wider window fits as many [GRID_CELL_WIDTH] cells as it can.
 * [availableWidth] is the grid's width; [horizontalPadding] and [spacing] are
 * its content padding and the gap between cells.
 */
fun gridColumnCount(
    windowSize: WindowSize,
    availableWidth: Dp,
    horizontalPadding: Dp,
    spacing: Dp,
): Int =
    if (windowSize == WindowSize.COMPACT) {
        COMPACT_GRID_COLUMNS
    } else {
        val usable = availableWidth - horizontalPadding * 2
        maxOf(((usable + spacing) / (GRID_CELL_WIDTH + spacing)).toInt(), 1)
    }

/** `GridCells` for a loaded poster grid, matching [gridColumnCount]. */
fun gridCells(windowSize: WindowSize): GridCells =
    if (windowSize == WindowSize.COMPACT) GridCells.Fixed(COMPACT_GRID_COLUMNS) else GridCells.Adaptive(GRID_CELL_WIDTH)

/**
 * Height of a poster card [cardWidth] wide.
 *
 * The artwork always keeps its 2:3 ([POSTER_ART_ASPECT]) and a card that shows
 * its caption adds the fixed caption block ([PosterCaption.blockHeight]). A
 * proportional caption reserve would shrink with a phone's narrow cell and let
 * the caption eat into the artwork, cropping it.
 */
@Composable
fun posterCardHeight(
    cardWidth: Dp,
    hideLabels: Boolean,
): Dp = cardWidth / POSTER_ART_ASPECT + if (hideLabels) 0.dp else PosterCaption.blockHeight()
