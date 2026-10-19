package com.misgus.pengu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.detail_add_to_library
import com.misgus.pengu.app.shared.generated.resources.detail_remove_from_library
import com.misgus.pengu.app.shared.generated.resources.status_watched
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Transparent band above and below the glyph inside a billboard action control.
 *
 * [HeroActionRow] uses the shared 48dp icon control, whose glyph is
 * [AppIconSize.md], so half of the difference is empty padding. A screen that
 * spaces the row against text discounts it, so the optical gap matches the
 * value it wrote instead of reading 12dp wider.
 */
val HeroActionRowInset: Dp = (AppSizes.minTouchTarget - AppIconSize.md) / 2

/**
 * State of the icon actions next to a billboard's call to action.
 *
 * The home billboard only uses the save flags; the detail screen also uses the
 * watched ones. The defaults keep a screen from wiring the fields it does not
 * show.
 */
data class HeroActionState(
    val saved: Boolean = false,
    val savedBusy: Boolean = false,
    val savedLoading: Boolean = false,
    val watched: Boolean = false,
    val watchedBusy: Boolean = false,
    val watchedLoading: Boolean = false,
)

/**
 * The icon-only actions next to a billboard's call to action.
 *
 * Save is turned into the library toggle: a plus that becomes a check once the
 * title is in the library. Watched is added when its handler is passed (the
 * detail screen). One definition keeps these buttons identical wherever a
 * billboard appears; the home billboard opens the title by tapping anywhere on
 * it, so it carries no "more info" action.
 */
@Composable
fun HeroActionRow(
    actions: HeroActionState,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatched: (() -> Unit)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        AppIconButton(
            icon = if (actions.saved) FluentIcons.Check else FluentIcons.Plus,
            contentDescription =
                stringResource(
                    if (actions.saved) Res.string.detail_remove_from_library else Res.string.detail_add_to_library,
                ),
            onClick = onToggleSaved,
            enabled = !actions.savedBusy,
            selected = actions.saved,
            loading = actions.savedLoading,
        )
        onToggleWatched?.let { onToggleWatched ->
            AppIconButton(
                icon = if (actions.watched) FluentIcons.CheckCircle else FluentIcons.CheckCircleOutline,
                contentDescription = stringResource(Res.string.status_watched),
                onClick = onToggleWatched,
                enabled = !actions.watchedBusy,
                selected = actions.watched,
                loading = actions.watchedLoading,
            )
        }
    }
}
