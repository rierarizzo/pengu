package com.misgus.pengu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.size.Size
import coil3.transform.Transformation
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.media_rating
import com.misgus.pengu.app.shared.generated.resources.media_runtime_hours
import com.misgus.pengu.app.shared.generated.resources.media_runtime_hours_minutes
import com.misgus.pengu.app.shared.generated.resources.media_runtime_minutes
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.ui.theme.AppAlpha
import com.misgus.pengu.ui.theme.AppLogoSize
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppSynopsis
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.WindowSize
import org.jetbrains.compose.resources.stringResource

/*
 * Shared copy pieces for a media billboard.
 *
 * Both the home hero and the detail hero render their title, metadata, synopsis
 * and genres through these composables, so the two screens cannot drift in type,
 * colour, separators or ordering. Anything a billboard shows about a title
 * belongs here, not in the screen.
 */

/**
 * Trim applied to title logos, so their transparent border does not consume the
 * box's optical height or max width.
 *
 * Shared by the billboards and the Home preloader: Coil keys its cache on the
 * transformation, so both requests must carry the same one to hit the same entry.
 */
internal val LogoTrimTransformation: Transformation = TrimTransparentTransformation()

/** The trim as a request list, reused so [RemoteImage]'s remembered request is stable. */
internal val LogoTransformations: List<Transformation> = listOf(LogoTrimTransformation)

/**
 * Cap on the decoded logo bitmap.
 *
 * Logos decode at their own resolution ([Size.ORIGINAL]) instead of the layout's, so
 * the transparent canvas does not shrink the artwork below the size it is drawn at.
 * The cap keeps a very large source from allocating a huge bitmap: it is reduced
 * once at decode (a source under the cap keeps every pixel). Logos are wide and
 * short, so the cap rarely binds.
 */
internal val LogoMaxBitmapSize: Size = Size(2_048, 2_048)

/** Title logo when the metadata provides one, otherwise the name as a headline. */
@Composable
fun MediaTitle(
    name: String,
    logo: String?,
    /** Optical height of the logo; `null` uses the detail billboard's height. */
    logoHeight: Dp? = null,
    /** While the metadata loads, reserve the logo's space without drawing anything. */
    loading: Boolean = false,
    /** Alignment of the name when it falls back to text; the logo follows the column. */
    textAlign: TextAlign = TextAlign.Start,
) {
    // The logo box hugs the artwork, capped at an optical height on a wide window
    // and a share of the window width on a phone (see [AppLogoSize.maxWidthForWindow])
    // so a square logo cannot tower over the copy. Hugging the content keeps the
    // gap to the metadata the same for every logo: a fixed box would centre a
    // short, wide wordmark and leave a gap that grew with its aspect.
    val height = AppLogoSize.opticalHeight(logoHeight ?: AppLogoSize.detailHeight)
    when {
        loading ->
            // Reserve the space so the title does not jump when it arrives, but
            // draw nothing: a skeleton here reads as a stray block.
            Spacer(Modifier.height(height))
        !logo.isNullOrEmpty() ->
            RemoteImage(
                url = logo,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                filterQuality = LogoFilterQuality,
                transformations = LogoTransformations,
                decodeSize = Size.ORIGINAL,
                maxBitmapSize = LogoMaxBitmapSize,
                fadeIn = true,
                modifier = Modifier.heightIn(max = height).widthIn(max = AppLogoSize.maxWidthForWindow()),
            )
        else ->
            Text(
                name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                style = AppType.heroTitle(),
            )
    }
}

/**
 * Dot-separated metadata line, e.g. `2023 • 1 h 37 min • ★ 7.8`.
 *
 * The caller owns the leading space, so each billboard keeps its own rhythm.
 */
@Composable
fun MetadataRow(parts: List<String>) {
    if (parts.isEmpty()) return
    // A phone tightens the separators, so the metadata does not compete with the
    // title above a shorter billboard. The line also carries a small negative
    // tracking to keep the ribbon on one line (see [AppType.heroMeta]).
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val style = AppType.heroMeta()
    val separatorPadding = if (compact) AppSpacing.xs else AppSpacing.sm
    Row(verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Text(
                    "•",
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = separatorPadding),
                )
            }
            Text(part, style = style, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Content type, release year, (first) genre, runtime and rating, in the order a
 * billboard shows them.
 *
 * [runtime] and [runtimeMinutes] are the two shapes a runtime arrives in: the
 * addons spell it themselves, while TMDB answers in minutes and the label is
 * built here so it stays localizable. [genres] is optional because a catalog
 * preview may not carry them; when absent the remaining parts keep their
 * relative order. Only the first genre is used — the line is a glance, not a
 * genre list. [releaseInfo] is reduced to its year through [releaseYear], so a
 * raw `2022-` never leaks into the line. The detail billboard shows genres under
 * the synopsis instead, so it does not pass them. [contentType] is the raw
 * Stremio type, named through [contentTypeName].
 */
@Composable
fun metadataParts(
    releaseInfo: String?,
    runtime: String? = null,
    runtimeMinutes: Int? = null,
    genres: List<String> = emptyList(),
    rating: Double? = null,
    contentType: ContentType? = null,
): List<String> =
    buildList {
        contentType?.let { contentTypeName(it) }?.let { add(it) }
        releaseYear(releaseInfo)?.let { add(it.toString()) }
        genres.firstOrNull()?.let { add(it) }
        val runtimeText = runtime?.takeIf { it.isNotEmpty() } ?: runtimeMinutes?.let { runtimeLabel(it) }
        runtimeText?.let { add(it) }
        rating?.let { add(stringResource(Res.string.media_rating, ratingText(it))) }
    }

/** A runtime in minutes as `1 h 37 min`, `2 h` or `45 min`. */
@Composable
private fun runtimeLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours <= 0 -> stringResource(Res.string.media_runtime_minutes, minutes)
        rest == 0 -> stringResource(Res.string.media_runtime_hours, hours)
        else -> stringResource(Res.string.media_runtime_hours_minutes, hours, rest)
    }
}

/**
 * Hero synopsis: a teaser of at most [AppSynopsis.MAX_LINES] lines.
 *
 * [loading] keeps [minLines] reserved without showing the preview copy, so the
 * layout does not jump when the real synopsis arrives. The measure is capped
 * here, so Home and Detail wrap the description at the same width. With
 * [expandable], a truncated synopsis expands to its full text on tap and
 * collapses again.
 */
@Composable
fun HeroSynopsis(
    text: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    minLines: Int = 1,
    expandable: Boolean = false,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var truncated by remember(text) { mutableStateOf(false) }
    val canToggle = expandable && !loading && truncated
    Text(
        text = if (loading) "" else text.orEmpty(),
        minLines = minLines,
        maxLines = if (expanded) Int.MAX_VALUE else AppSynopsis.MAX_LINES,
        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = AppType.heroBody(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.HERO_SYNOPSIS),
        onTextLayout = { layout -> if (!expanded) truncated = layout.hasVisualOverflow },
        modifier =
            modifier
                .widthIn(max = AppSynopsis.maxWidth)
                .then(if (canToggle) Modifier.clickable { expanded = !expanded } else Modifier),
    )
}

/** Genres under the synopsis, dot-separated and dimmed. */
@Composable
fun HeroGenres(genres: List<String>) {
    if (genres.isEmpty()) return
    Text(
        genres.joinToString("  ·  "),
        style = AppType.bodySecondary(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Formats a rating to one decimal place. */
fun ratingText(value: Double): String = (kotlin.math.round(value * 10) / 10.0).toString()
