package com.misgus.pengu.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.detail_episode_code
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.ui.components.CardProgressBar
import com.misgus.pengu.ui.components.PosterImage
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.motionBorder
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.player.formatPlayerTime
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.PosterCaption
import com.misgus.pengu.ui.theme.motionTween
import org.jetbrains.compose.resources.stringResource

/** Width of a 16:9 "Continue watching" card, smaller on a phone. */
internal val ContinueWidth = 400.dp
internal val CompactContinueWidth = 300.dp

/**
 * One "Continue watching" card: the wide artwork, with the title, the timecode
 * and the progress bar over it.
 *
 * The copy sits inside the card so the artwork can be as large as the card; the
 * timecode answers the question the row exists for — how far in the user is.
 */
@Composable
internal fun ContinueCard(
    entry: ContinueEntry,
    width: Dp,
    onTap: () -> Unit,
) {
    val item = entry.item
    val progress = entry.progress
    val season = progress.season
    val episode = progress.episode
    val meta =
        listOfNotNull(
            if (season != null && episode != null) stringResource(Res.string.detail_episode_code, season, episode) else null,
            timecode(progress),
        ).joinToString(" · ")
    val artwork = item.background ?: item.poster

    val interaction = rememberInteraction()
    val borderColor =
        animateColorAsState(
            targetValue = if (interaction.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = motionTween(),
            label = "borderColor",
        )

    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(16f / 9f)
                .motionBorder(color = { borderColor.value }, width = AppSizes.borderWidth, cornerRadius = AppCorner.small)
                .clip(MaterialTheme.shapes.small)
                .clickableSurface(interaction, onTap, indication = null),
    ) {
        PosterImage(artwork, Modifier.fillMaxSize())
        // Keeps the copy readable on any artwork without giving the card away.
        Box(Modifier.fillMaxSize().background(AppScrim.cardBottom))
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    // The bar is drawn along the very edge, so the copy clears it
                    // instead of sitting on top of it.
                    .padding(
                        start = AppSpacing.md,
                        end = AppSpacing.md,
                        top = AppSpacing.sm,
                        bottom = AppSpacing.sm + AppSizes.progressBarHeight,
                    ),
        ) {
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = PosterCaption.title(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = PosterCaption.meta(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        progress.fraction?.let { fraction ->
            CardProgressBar(fraction.toFloat(), Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** How far in the user is, as `12:34 / 45:00`; `null` when the length is unknown. */
internal fun timecode(progress: WatchProgress): String? {
    val position = progress.position ?: return null
    val duration = progress.duration ?: return null
    return "${formatPlayerTime(position)} / ${formatPlayerTime(duration)}"
}
