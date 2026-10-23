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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_more_options
import com.misgus.pengu.app.shared.generated.resources.detail_episode_code
import com.misgus.pengu.app.shared.generated.resources.home_continue_left
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.components.CardProgressBar
import com.misgus.pengu.ui.components.MediaActions
import com.misgus.pengu.ui.components.MediaActionsHost
import com.misgus.pengu.ui.components.PosterImage
import com.misgus.pengu.ui.components.combinedClickableSurface
import com.misgus.pengu.ui.components.motionBorder
import com.misgus.pengu.ui.components.onSecondaryClick
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.rememberMediaActionsState
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.PosterCaption
import com.misgus.pengu.ui.theme.motionTween
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.time.Duration

/**
 * The id the stream addons are asked with when a continue card resumes.
 *
 * It comes from the progress row, not from the library item: progress is written
 * with the id playback was reported with (the resolved IMDb id, and the episode's
 * own id for a series), while the library keeps the id the title was saved with —
 * `tmdb:123` for a title opened from a TMDB recommendation, which the addons do
 * not answer. `null` means the row cannot address a stream at all (a series
 * without its episode id), so the card falls back to the detail page.
 */
internal fun resumePlayId(progress: WatchProgress): String? {
    // The backend records "no episode" as an empty string, not `null`.
    progress.videoId?.takeIf { it.isNotBlank() }?.let { return it }
    return progress.contentId.takeIf { it.isNotBlank() && !ContentTypes.isSeries(progress.contentType) }
}

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
    actions: MediaActions,
) {
    val item = entry.item
    val progress = entry.progress
    val actionsState = rememberMediaActionsState()
    val optionsLabel = stringResource(Res.string.action_more_options)
    val season = progress.season
    val episode = progress.episode
    // The episode's name is not part of the progress row (the backend has no column
    // for it), so it comes from the record written when the stream was resolved.
    // The store keeps its file in memory after the first read.
    val streamCache = LocalAppServices.current.streamCache
    val videoId = progress.videoId?.takeIf { it.isNotBlank() }
    var episodeTitle by remember(progress.contentId, videoId, progress.lastWatched) { mutableStateOf<String?>(null) }
    LaunchedEffect(progress.contentId, videoId, progress.lastWatched) {
        episodeTitle = runCatchingCancellable { streamCache.find(progress.contentId, videoId) }.getOrNull()?.episodeTitle
    }
    val meta =
        listOfNotNull(
            if (season != null && episode != null) stringResource(Res.string.detail_episode_code, season, episode) else null,
            episodeTitle,
            remainingLabel(progress),
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
                .onSecondaryClick(actionsState::open)
                .combinedClickableSurface(
                    state = interaction,
                    onClick = onTap,
                    onLongClick = { actionsState.open() },
                    indication = null,
                    onLongClickLabel = optionsLabel,
                ),
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
        MediaActionsHost(actions, actionsState)
    }
}

/** Minutes left to play, rounded up; `null` when the length or position is unknown. */
internal fun remainingMinutes(progress: WatchProgress): Int? {
    val position = progress.position ?: return null
    val duration = progress.duration ?: return null
    val left = duration - position
    if (left <= Duration.ZERO) return null
    return ceil(left.inWholeSeconds / 60.0).toInt()
}

/** [remainingMinutes] as `35 min left`, or `null` when there is nothing to say. */
@Composable
private fun remainingLabel(progress: WatchProgress): String? =
    remainingMinutes(progress)?.let { stringResource(Res.string.home_continue_left, it) }
