package com.misgus.pengu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_mark_unwatched
import com.misgus.pengu.app.shared.generated.resources.action_mark_watched
import com.misgus.pengu.app.shared.generated.resources.action_more_info
import com.misgus.pengu.app.shared.generated.resources.detail_add_to_library
import com.misgus.pengu.app.shared.generated.resources.detail_error_library
import com.misgus.pengu.app.shared.generated.resources.detail_error_watched
import com.misgus.pengu.app.shared.generated.resources.detail_remove_from_library
import com.misgus.pengu.app.shared.generated.resources.home_continue_play_from_start
import com.misgus.pengu.app.shared.generated.resources.home_continue_remove
import com.misgus.pengu.app.shared.generated.resources.media_rating
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.platform.isDesktop
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.LocalSnackbarHost
import com.misgus.pengu.ui.theme.AppBottomSheetShape
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.safeBottomInset
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Actions that only make sense on a "Continue watching" card. */
data class ContinueWatchingActions(
    val onPlayFromBeginning: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * What the poster actions menu offers for one title.
 *
 * [saved] and [watched] are the states the caller already knows. A catalog
 * poster does not know them, so it leaves them `null` and the host resolves
 * them when the menu opens; a Home or Library poster passes them and the menu
 * paints correctly at once.
 */
data class MediaActions(
    val item: LibraryItem,
    val onMoreInfo: () -> Unit,
    val saved: Boolean? = null,
    val watched: Boolean? = null,
    val continueWatching: ContinueWatchingActions? = null,
    /** Lets a screen that shows the badge repaint without reloading the feed. */
    val onWatchedChanged: (Boolean) -> Unit = {},
)

/** Open/closed state of the actions menu, owned by the poster card. */
@Stable
class MediaActionsState internal constructor() {
    var expanded by mutableStateOf(false)
        private set

    /**
     * Where the secondary click happened, in the card's own coordinates.
     *
     * The menu is anchored there, so it opens under the cursor instead of at a
     * corner of the poster.
     */
    var position by mutableStateOf(Offset.Zero)
        private set

    fun open(at: Offset = Offset.Zero) {
        position = at
        expanded = true
    }

    fun close() {
        expanded = false
    }
}

@Composable
fun rememberMediaActionsState(): MediaActionsState = remember { MediaActionsState() }

private data class MediaActionItem(
    val label: String,
    val icon: ImageVector,
    val emphasized: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Desktop menu and touch sheet for a poster.
 *
 * Desktop only shows the options, anchored at the pointer that opened them. A
 * touch device opens a bottom sheet with the title, its metadata, the synopsis
 * and the same actions as full-width buttons, since a floating menu is a poor
 * target there.
 */
@Composable
fun MediaActionsHost(
    actions: MediaActions,
    state: MediaActionsState,
) {
    val services = LocalAppServices.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val libraryError = stringResource(Res.string.detail_error_library)
    val watchedError = stringResource(Res.string.detail_error_watched)

    var saved by remember(actions.item.contentId, actions.saved) { mutableStateOf(actions.saved) }
    var watched by remember(actions.item.contentId, actions.watched) { mutableStateOf(actions.watched) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(state.expanded, actions.item.contentId) {
        if (!state.expanded || actions.continueWatching != null) return@LaunchedEffect
        if (saved == null) {
            saved = runCatchingCancellable { services.library.contains(actions.item.contentId) }.getOrNull()
        }
        if (watched == null) {
            watched =
                runCatchingCancellable {
                    services.progress.watchedEntries(listOf(actions.item.contentId)).any { it.isMarker }
                }.getOrNull()
        }
    }

    val toggleSaved = {
        if (!busy) {
            busy = true
            val wanted = saved != true
            scope.launch {
                runCatchingCancellable {
                    if (wanted) {
                        services.library.add(actions.item)
                    } else {
                        services.library.remove(actions.item.contentId)
                    }
                }.onSuccess { saved = wanted }
                    .onFailure { snackbar?.showSnackbar(libraryError) }
                busy = false
            }
        }
    }

    val toggleWatched = {
        if (!busy) {
            busy = true
            val wanted = watched != true
            scope.launch {
                runCatchingCancellable {
                    if (wanted) {
                        services.progress.markWatched(actions.item.contentId, actions.item.contentType)
                    } else {
                        services.progress.unmarkWatched(actions.item.contentId)
                    }
                }.onSuccess {
                    watched = wanted
                    actions.onWatchedChanged(wanted)
                }.onFailure { snackbar?.showSnackbar(watchedError) }
                busy = false
            }
        }
    }

    val items =
        if (!state.expanded) {
            emptyList()
        } else {
            buildList {
                val continueWatching = actions.continueWatching
                if (continueWatching != null) {
                    add(
                        MediaActionItem(
                            label = stringResource(Res.string.home_continue_play_from_start),
                            icon = MaterialIcons.Play,
                            emphasized = true,
                            onClick = continueWatching.onPlayFromBeginning,
                        ),
                    )
                    add(
                        MediaActionItem(
                            label = stringResource(Res.string.home_continue_remove),
                            icon = MaterialIcons.Delete,
                            onClick = continueWatching.onRemove,
                        ),
                    )
                } else {
                    add(
                        MediaActionItem(
                            label =
                                stringResource(
                                    if (saved == true) Res.string.detail_remove_from_library else Res.string.detail_add_to_library,
                                ),
                            icon = if (saved == true) MaterialIcons.Check else MaterialIcons.Plus,
                            onClick = toggleSaved,
                        ),
                    )
                    add(
                        MediaActionItem(
                            label =
                                stringResource(
                                    if (watched == true) Res.string.action_mark_unwatched else Res.string.action_mark_watched,
                                ),
                            icon = if (watched == true) MaterialIcons.CheckCircle else MaterialIcons.CheckCircleOutline,
                            onClick = toggleWatched,
                        ),
                    )
                }
                add(
                    MediaActionItem(
                        label = stringResource(Res.string.action_more_info),
                        icon = MaterialIcons.ChevronRight,
                        onClick = actions.onMoreInfo,
                    ),
                )
            }
        }

    if (isDesktop && state.expanded) {
        Box(
            Modifier.offset {
                IntOffset(state.position.x.roundToInt(), state.position.y.roundToInt())
            },
        ) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = state::close,
                containerColor = AppColors.surfaceSelected,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(AppSizes.borderWidth, MaterialTheme.colorScheme.outlineVariant),
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        modifier = Modifier.handCursor().height(40.dp),
                        enabled = !busy,
                        text = {
                            Text(
                                item.label,
                                style =
                                    AppType.bodySecondary().copy(
                                        fontWeight = if (item.emphasized) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                color = if (item.emphasized && !busy) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(AppIconSize.sm),
                            )
                        },
                        onClick = {
                            state.close()
                            item.onClick()
                        },
                    )
                }
            }
        }
    } else if (!isDesktop && state.expanded) {
        MediaActionsSheet(
            item = actions.item,
            items = items,
            busy = busy,
            onDismiss = state::close,
        )
    }
}

/** Touch layout: the title, the synopsis and the actions in thumb reach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaActionsSheet(
    item: LibraryItem,
    items: List<MediaActionItem>,
    busy: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()
    val run: (MediaActionItem) -> Unit = { action ->
        // Hide first so the sheet does not vanish mid-frame, then let the action
        // open the player or another screen.
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action.onClick()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppBottomSheetShape,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg)
                    .padding(top = AppSpacing.sm, bottom = AppSpacing.lg + safeBottomInset),
        ) {
            Text(item.name, style = AppType.sectionTitle())
            mediaMeta(item)?.let { meta ->
                Spacer(Modifier.height(AppSpacing.xs))
                Text(meta, style = AppType.caption(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                Spacer(Modifier.height(AppSpacing.md))
                HeroSynopsis(text = synopsis, expandable = true)
            }
            Spacer(Modifier.height(AppSpacing.lg))
            items.forEach { action ->
                SheetActionRow(
                    action = action,
                    enabled = !busy,
                    onClick = { run(action) },
                )
            }
        }
    }
}

/**
 * One option in the touch sheet: glyph and label, left aligned, with no button
 * container. A row is easier to scan than a stack of outlined pills and keeps
 * the whole width as the tap target.
 */
@Composable
private fun SheetActionRow(
    action: MediaActionItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val emphasized = action.emphasized && enabled
    val contentColor = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(interactionHighlight(interaction))
                .focusRing(interaction)
                .clickableSurface(interaction, onClick, enabled = enabled)
                .heightIn(min = AppSizes.controlHeight)
                .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppIconSize.sm),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Text(
            action.label,
            style =
                AppType.bodySecondary().copy(
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = contentColor,
        )
    }
}

/** `Movie • 2019 • ★ 8.1`, for the sheet's subtitle. */
@Composable
private fun mediaMeta(item: LibraryItem): String? =
    listOfNotNull(
        contentTypeLabel(item.contentType),
        item.releaseInfo?.takeIf { it.isNotBlank() },
        item.imdbRating?.let { stringResource(Res.string.media_rating, ratingText(it)) },
    ).joinToString(" • ")
        .takeIf { it.isNotEmpty() }
