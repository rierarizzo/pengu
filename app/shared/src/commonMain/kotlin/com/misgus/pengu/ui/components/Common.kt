package com.misgus.pengu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_see_all
import com.misgus.pengu.app.shared.generated.resources.status_syncing
import com.misgus.pengu.app.shared.generated.resources.status_watched
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.motionTween
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/**
 * Marks a text node as a heading, so accessibility services can jump between
 * sections instead of reading the screen linearly.
 */
fun Modifier.sectionHeading(): Modifier = semantics { heading() }

/**
 * Mirrors the content in a right-to-left layout.
 *
 * Material Symbols' directional icons, the arrow and the sign-in/out glyphs, flip
 * through `ImageVector`'s `autoMirror`; a directional chevron, which the Icon
 * API does not mirror, is flipped here instead of swapping the icon at every
 * use.
 */
@Composable
fun Modifier.mirrorInRtl(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        graphicsLayer { scaleX = -1f }
    } else {
        this
    }

/** Check badge shown on a title the account marked as watched. */
@Composable
fun WatchedBadge(iconSize: Int = 14) {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(AppColors.mediaScrim)
                .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialIcons.Check,
            contentDescription = stringResource(Res.string.status_watched),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/**
 * Playback progress drawn along an edge of a card's artwork.
 *
 * Material 3's indicator ends the track with a stop indicator and leaves a gap
 * before it; over artwork both read as a stray dot and a notch instead of a
 * continuous bar, so they are switched off.
 */
@Composable
fun CardProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { fraction },
        color = MaterialTheme.colorScheme.primary,
        trackColor = AppColors.mediaScrim,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(AppSizes.progressBarHeight),
    )
}

/**
 * Small "Syncing…" pill shown while the watched badges are resolved in the
 * background, so the content can paint immediately.
 */
@Composable
fun SyncingIndicator() {
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = AppSpacing.sm + AppSpacing.xxs, vertical = AppSpacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(AppSpacing.sm))
        Text(stringResource(Res.string.status_syncing), style = AppType.bodySecondary())
    }
}

/** Centered icon + title + message used for empty, loading and error states. */
@Composable
fun Message(
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = AppSizes.messageMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Spacer(Modifier.size(AppSpacing.md))
            Text(title, style = AppType.cardTitle(), modifier = Modifier.sectionHeading())
            if (!message.isNullOrEmpty()) {
                Spacer(Modifier.size(AppSpacing.xs))
                Text(
                    message,
                    textAlign = TextAlign.Center,
                    style = AppType.bodySecondary(),
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.size(AppSpacing.md))
                ActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * Title row above a horizontal rail of posters, with an optional "see all"
 * icon button.
 *
 * Shared by Home and Search so the rails keep the same rhythm. The header is as
 * tall as its title: the optional trailing icon is centred on it and allowed to
 * overflow into the padding, so the gap to the content below does not depend on
 * whether the button is there.
 */
@Composable
fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
) {
    val screenPadding = LocalWindowSize.current.screenPadding
    Layout(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = screenPadding, end = screenPadding, top = AppSpacing.lg, bottom = AppSpacing.md),
        content = {
            Row {
                Text(
                    title,
                    style = AppType.sectionTitle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).sectionHeading(),
                )
            }
            if (onSeeAll != null) {
                AppIconButton(
                    icon = MaterialIcons.ChevronRight,
                    contentDescription = stringResource(Res.string.action_see_all),
                    onClick = onSeeAll,
                    iconModifier = Modifier.mirrorInRtl(),
                )
            }
        },
    ) { measurables, constraints ->
        // The icon is measured unconstrained so its 48dp touch target never widens
        // the header; it is centred on the title and overflows into the padding.
        val button = measurables.getOrNull(1)?.measure(Constraints())
        val buttonWidth = button?.width ?: 0
        val titleMaxWidth = (constraints.maxWidth - buttonWidth).coerceAtLeast(0)
        val titleLayout = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth))
        layout(constraints.maxWidth, titleLayout.height) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            titleLayout.place(x = if (rtl) constraints.maxWidth - titleLayout.width else 0, y = 0)
            button?.place(
                x = if (rtl) 0 else constraints.maxWidth - button.width,
                y = (titleLayout.height - button.height) / 2,
            )
        }
    }
}

/** Sampling for photographic artwork: mipmapped bilinear avoids downscale aliasing. */
val ArtworkFilterQuality = FilterQuality.Medium

/**
 * Sampling for a full-screen backdrop. It is drawn near its own scale, where
 * mipmaps ([ArtworkFilterQuality]) cost more than they save, so plain bilinear
 * is enough.
 */
val BackdropFilterQuality = FilterQuality.Low

/** Sampling for text-like artwork (title logos): bicubic keeps the edges crisp. */
val LogoFilterQuality = FilterQuality.High

/**
 * Remote artwork with the sampling the app expects.
 *
 * Coil draws with [androidx.compose.ui.graphics.drawscope.DrawScope.DefaultFilterQuality]
 * (bilinear), which aliases a poster or backdrop when it is downscaled. Mipmapped
 * bilinear ([FilterQuality.Medium]) removes that, and bicubic ([FilterQuality.High])
 * is reserved for text-like artwork such as title logos.
 *
 * [transformations] are applied before the image is measured, so a transformation
 * that changes the image's size also changes the box it asks for (title logos trim
 * their transparent border this way). [decodeSize] pins the decode resolution
 * instead of deriving it from the layout, and [maxBitmapSize] caps it so a huge
 * source is reduced once at decode rather than allocated whole.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    filterQuality: FilterQuality = ArtworkFilterQuality,
    transformations: List<Transformation> = emptyList(),
    decodeSize: Size? = null,
    maxBitmapSize: Size? = null,
    /** Fade the image in once it has loaded, instead of popping. */
    fadeIn: Boolean = false,
) {
    if (url.isNullOrEmpty()) return
    val needsRequest = transformations.isNotEmpty() || decodeSize != null || maxBitmapSize != null
    val model =
        if (!needsRequest) {
            url
        } else {
            val context = LocalPlatformContext.current
            // Remembered so a recomposition reuses the request instead of
            // rebuilding it (and its cache key) every frame.
            remember(url, transformations, decodeSize, maxBitmapSize, context) {
                val builder = ImageRequest.Builder(context).data(url)
                if (transformations.isNotEmpty()) builder.transformations(transformations)
                if (decodeSize != null) builder.size(decodeSize)
                if (maxBitmapSize != null) builder.maxBitmapSize(maxBitmapSize)
                builder.build()
            }
        }
    if (!fadeIn) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            filterQuality = filterQuality,
            modifier = modifier,
        )
        return
    }
    // Fade when the image itself is ready, not when its URL is known, so the
    // reveal is smooth even if the load lags behind the metadata.
    var loaded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = motionTween(),
        label = "imageFade",
    )
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        filterQuality = filterQuality,
        onSuccess = { loaded = true },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

/** Artwork with a graceful placeholder while it loads or fails. */
@Composable
fun PosterImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surface)) {
        if (url.isNullOrEmpty()) {
            PosterFallback()
        } else {
            RemoteImage(
                url = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PosterFallback() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = MaterialIcons.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
