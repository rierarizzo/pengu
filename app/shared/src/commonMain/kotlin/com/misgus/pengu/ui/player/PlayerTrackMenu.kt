package com.misgus.pengu.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_close
import com.misgus.pengu.app.shared.generated.resources.player_no_tracks
import com.misgus.pengu.app.shared.generated.resources.player_track_ai
import com.misgus.pengu.app.shared.generated.resources.player_track_subtitle
import com.misgus.pengu.app.shared.generated.resources.player_track_trusted
import com.misgus.pengu.core.model.Subtitle
import com.misgus.pengu.core.player.MediaTrack
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import org.jetbrains.compose.resources.stringResource

/** One selectable row of a track menu. */
internal data class TrackRow(
    val label: String,
    val detail: String? = null,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

/** Audio/subtitle track picker shown by the player. */
@Composable
internal fun TrackMenu(
    title: String,
    rows: List<TrackRow>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        confirmButton = {
            ActionButton(
                text = stringResource(Res.string.action_close),
                onClick = onDismiss,
                style = ActionButtonStyle.Text,
            )
        },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
                if (rows.isEmpty()) {
                    Text(stringResource(Res.string.player_no_tracks), style = AppType.bodySecondary())
                }
                rows.forEach { row ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = row.selected,
                                    onClick = {
                                        row.onSelect()
                                        onDismiss()
                                    },
                                ).padding(vertical = AppSpacing.sm),
                    ) {
                        Text(
                            text = if (row.selected) "● ${row.label}" else "○ ${row.label}",
                            style = AppType.itemTitle(),
                        )
                        row.detail?.let {
                            Text(it, style = AppType.caption(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

internal fun trackLabel(track: MediaTrack): String =
    track.title?.takeIf { it.isNotEmpty() } ?: track.language?.takeIf { it.isNotEmpty() } ?: track.id

@Composable
internal fun subtitleSubtitleTitle(subtitle: Subtitle): String {
    val name = subtitle.languageName
    val code = subtitle.langCode ?: subtitle.lang
    return if (name != null) {
        if (code.isNullOrEmpty()) name else "$name ($code)"
    } else {
        subtitle.lang ?: subtitle.title ?: stringResource(Res.string.player_track_subtitle)
    }
}

@Composable
internal fun subtitleTrackLabel(subtitle: Subtitle): String = subtitleSubtitleTitle(subtitle)

@Composable
internal fun subtitleDetail(subtitle: Subtitle): String? {
    val parts =
        buildList {
            subtitle.title?.takeIf { it.isNotEmpty() }?.let { add(it) }
            subtitle.addonName?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (subtitle.aiTranslated) add(stringResource(Res.string.player_track_ai))
            if (subtitle.fromTrusted) add(stringResource(Res.string.player_track_trusted))
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
