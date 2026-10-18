package com.misgus.pengu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScale
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.POSTER_ART_ASPECT
import com.misgus.pengu.ui.theme.PosterCaption
import com.misgus.pengu.ui.theme.motionLayer
import com.misgus.pengu.ui.theme.motionTween

/**
 * Poster card for a catalog item ([MetaPreview]), used by Home rows.
 *
 * [width] is the fixed width for horizontal rows; when null the card fills its
 * parent (used inside a grid). [hideLabels] drops the title and release-info
 * captions, leaving the artwork alone.
 */
@Composable
fun MetaPosterCard(
    preview: MetaPreview,
    watched: Boolean = false,
    hideLabels: Boolean = false,
    width: Dp? = null,
    onTap: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val interaction = rememberInteraction()
    val borderColor =
        animateColorAsState(
            targetValue = if (interaction.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = motionTween(),
            label = "borderColor",
        )
    val scale =
        animateFloatAsState(
            targetValue = if (interaction.active) AppScale.HOVER else 1f,
            animationSpec = motionTween(durationMillis = AppMotion.FAST),
            label = "posterScale",
        )
    Column(
        modifier =
            Modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .clickableSurface(interaction, onTap, indication = null),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(POSTER_ART_ASPECT)
                    .motionLayer(scale = { scale.value })
                    .motionBorder(color = { borderColor.value }, width = AppSizes.borderWidth, cornerRadius = AppCorner.small)
                    .clip(shape),
        ) {
            PosterImage(preview.poster, Modifier.fillMaxSize())
            if (watched) {
                Box(Modifier.align(Alignment.TopEnd).padding(AppSpacing.xs)) { WatchedBadge() }
            }
        }
        if (!hideLabels) {
            Spacer(Modifier.height(AppSpacing.sm))
            MarqueeText(
                text = preview.name,
                style = PosterCaption.title(),
                active = interaction.active,
            )
            val releaseInfo = preview.releaseInfo
            if (!releaseInfo.isNullOrEmpty()) {
                Text(
                    text = releaseInfo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = PosterCaption.meta(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
