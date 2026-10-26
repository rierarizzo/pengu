package com.misgus.pengu.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_cancel
import com.misgus.pengu.app.shared.generated.resources.player_play_now
import com.misgus.pengu.app.shared.generated.resources.player_up_next
import com.misgus.pengu.app.shared.generated.resources.player_up_next_seconds
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import org.jetbrains.compose.resources.stringResource

/**
 * Prompt at the end of an episode: what plays next and how long is left to
 * change your mind.
 *
 * Autoplay used to swap the episode without a word, which read as the app
 * deciding on its own. The card keeps the countdown visible and lets the user
 * start it now or cancel; the caller owns the timer.
 */
@Composable
internal fun NextEpisodePrompt(
    label: String,
    secondsLeft: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(min = AppSizes.messageMaxWidth, max = AppSizes.messageMaxWidth)
                .clip(RoundedCornerShape(AppSizes.borderWidth * 2))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(AppSpacing.md),
    ) {
        Text(
            stringResource(Res.string.player_up_next),
            style = AppType.label(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            label,
            style = AppType.cardTitle(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(Res.string.player_up_next_seconds, secondsLeft),
            style = AppType.bodySecondary(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                text = stringResource(Res.string.player_play_now),
                onClick = onPlayNow,
                style = ActionButtonStyle.Accent,
            )
            ActionButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onCancel,
                style = ActionButtonStyle.Text,
            )
        }
    }
}
