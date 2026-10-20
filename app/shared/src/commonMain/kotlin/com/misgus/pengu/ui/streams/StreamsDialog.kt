package com.misgus.pengu.ui.streams

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_close
import com.misgus.pengu.app.shared.generated.resources.action_play
import com.misgus.pengu.app.shared.generated.resources.action_refresh
import com.misgus.pengu.app.shared.generated.resources.streams_copy_link
import com.misgus.pengu.app.shared.generated.resources.streams_count_many
import com.misgus.pengu.app.shared.generated.resources.streams_count_one
import com.misgus.pengu.app.shared.generated.resources.streams_empty
import com.misgus.pengu.app.shared.generated.resources.streams_error
import com.misgus.pengu.app.shared.generated.resources.streams_error_hint
import com.misgus.pengu.app.shared.generated.resources.streams_error_short
import com.misgus.pengu.app.shared.generated.resources.streams_loading
import com.misgus.pengu.core.model.Stream
import com.misgus.pengu.core.repository.StreamGroup
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.navContentColor
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.platform.clipEntryOfText
import com.misgus.pengu.ui.platform.isDesktop
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.RouteViewModelStoreOwner
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScale
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.LocalWindowWidth
import com.misgus.pengu.ui.theme.WindowSize
import com.misgus.pengu.ui.theme.motionLayer
import com.misgus.pengu.ui.theme.motionTween
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Widest the dialog gets, and a floor so it stays usable on a small window. */
private val StreamsDialogMaxWidth = 900.dp
private val StreamsDialogMinWidth = 280.dp

/**
 * Modal that lists the available streams.
 *
 * A phone has no room for a floating window, so the picker rises from the bottom
 * edge as a sheet; a wider window keeps the dialog, where a sheet gesture has no
 * meaning. Both host the same panel, so the content never drifts between the two.
 *
 * The stream text is shown as-is: addons put their configured, multi-line format
 * in `name` and `description`, so the UI just renders it. What the UI adds is the
 * structure around it — the addon each source belongs to and the size, when the
 * addon declares it — without parsing the formatted text. There is no filter: the
 * addons already rank their own sources, and a field over a dozen rows only adds
 * a step between opening the picker and playing.
 */
@Composable
fun StreamsDialog(
    request: StreamsRequest,
    onDismiss: () -> Unit,
    onSelect: (Stream) -> Unit,
) {
    val services = LocalAppServices.current
    val owner = remember(request) { RouteViewModelStoreOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val viewModel: StreamsViewModel =
            viewModel(key = request.id) {
                StreamsViewModel(services.streams, request.type, request.id)
            }
        LaunchedEffect(request) { viewModel.load() }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val onRefresh = { viewModel.load(force = true) }

        if (LocalWindowSize.current == WindowSize.COMPACT && !isDesktop) {
            StreamsSheet(
                request = request,
                state = state,
                onRefresh = onRefresh,
                onDismiss = onDismiss,
                onSelect = onSelect,
            )
        } else {
            StreamsWindow(
                request = request,
                state = state,
                onRefresh = onRefresh,
                onDismiss = onDismiss,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * The picker as a floating window, for a wide window.
 *
 * A dialog opens its own window, so the width is capped to the app window instead
 * of trusting the fixed size.
 */
@Composable
private fun StreamsWindow(
    request: StreamsRequest,
    state: StreamsUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Stream) -> Unit,
) {
    // One progress drives both the entrance and the exit, so the picker leaves with
    // the motion it arrived with and the route opens only once it is gone.
    val scope = rememberCoroutineScope()
    val enterSpec = motionTween<Float>(durationMillis = AppMotion.MEDIUM)
    val exitSpec = motionTween<Float>(durationMillis = AppMotion.FAST)
    val progress = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { progress.animateTo(1f, enterSpec) }
    val select: (Stream) -> Unit = { stream ->
        if (!leaving) {
            leaving = true
            scope.launch {
                progress.animateTo(0f, exitSpec)
                onSelect(stream)
            }
        }
    }
    val dialogWidth =
        minOf(StreamsDialogMaxWidth, LocalWindowWidth.current - AppSpacing.xl * 2)
            .coerceAtLeast(StreamsDialogMinWidth)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .width(dialogWidth)
                    .fillMaxHeight(0.82f)
                    .motionLayer(
                        scale = { AppScale.DIALOG_ENTER + (1f - AppScale.DIALOG_ENTER) * progress.value },
                        alpha = { progress.value },
                    ).clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            StreamsPanel(
                request = request,
                state = state,
                onRefresh = onRefresh,
                onDismiss = onDismiss,
                onSelect = select,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The picker as a bottom sheet, for a phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamsSheet(
    request: StreamsRequest,
    state: StreamsUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Stream) -> Unit,
) {
    // The sheet is only composed while open, so it starts hidden and lets
    // ModalBottomSheet animate it in; skipping the partial state keeps it on a
    // single, fully expanded stop, since the panel already caps its own height.
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    // Hides the sheet before the route opens, so it does not vanish mid-frame. A
    // programmatic hide does not call onDismissRequest, so the selection survives.
    val select: (Stream) -> Unit = { stream ->
        if (!leaving) {
            leaving = true
            scope.launch {
                sheetState.hide()
                onSelect(stream)
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        StreamsPanel(
            request = request,
            state = state,
            onRefresh = onRefresh,
            onDismiss = onDismiss,
            onSelect = select,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
        )
    }
}

/**
 * Content of the picker, shared by the window and the sheet.
 *
 * The list owns the remaining height so a short list, an empty state and an error
 * all sit inside the panel instead of pushing past its header.
 */
@Composable
private fun StreamsPanel(
    request: StreamsRequest,
    state: StreamsUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Header(
            request = request,
            state = state,
            onRefresh = onRefresh,
            onClose = onDismiss,
        )
        if (state.error != null && state.hasLoaded) {
            RefreshFailedNotice(onRetry = onRefresh)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Content(
                state = state,
                onRefresh = onRefresh,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * Title, count and the two header actions.
 *
 * The artwork the dialog used to open with is gone: the user already chose the
 * title one screen back, and a 200dp billboard pushed the sources — the only
 * reason this panel exists — below the fold.
 */
@Composable
private fun Header(
    request: StreamsRequest,
    state: StreamsUiState,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = LocalWindowSize.current.screenPadding,
                    end = AppSpacing.sm,
                    top = AppSpacing.sm,
                    // The list starts right under the count, so the header keeps its
                    // own gap: without it the first row reads as part of the title
                    // while the content scrolls past.
                    bottom = AppSpacing.md,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                request.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppType.dialogTitle(),
                modifier = Modifier.sectionHeading(),
            )
            Text(
                headerSubtitle(request, state),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppType.caption(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIconButton(
            icon = MaterialIcons.Refresh,
            contentDescription = stringResource(Res.string.action_refresh),
            onClick = onRefresh,
            enabled = !state.isLoading,
            iconSize = AppIconSize.sm,
        )
        AppIconButton(
            icon = MaterialIcons.Close,
            contentDescription = stringResource(Res.string.action_close),
            onClick = onClose,
        )
    }
}

/** The title's year and the count of sources, in one line. */
@Composable
private fun headerSubtitle(
    request: StreamsRequest,
    state: StreamsUiState,
): String {
    val count =
        when {
            state.isLoading && !state.hasLoaded -> stringResource(Res.string.streams_loading)
            state.error != null && !state.hasLoaded -> stringResource(Res.string.streams_error_short)
            state.totalCount == 1 -> stringResource(Res.string.streams_count_one)
            else -> stringResource(Res.string.streams_count_many, state.totalCount)
        }
    val year = request.year?.takeIf { it.isNotEmpty() }
    return listOfNotNull(year, count).joinToString(" • ")
}

@Composable
private fun Content(
    state: StreamsUiState,
    onRefresh: () -> Unit,
    onSelect: (Stream) -> Unit,
) {
    when {
        state.isLoading && !state.hasLoaded ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null && !state.hasLoaded ->
            Message(
                icon = MaterialIcons.Error,
                title = stringResource(Res.string.streams_error),
                message = stringResource(Res.string.streams_error_hint),
                actionLabel = stringResource(Res.string.action_refresh),
                onAction = onRefresh,
            )
        state.groups.isEmpty() ->
            Message(
                icon = MaterialIcons.Inbox,
                title = stringResource(Res.string.streams_empty),
            )
        else ->
            StreamList(
                groups = state.groups,
                onSelect = onSelect,
            )
    }
}

/**
 * The sources, one row each, grouped by addon.
 *
 * The group header only appears when more than one addon answered; with a single
 * addon it would repeat the same label over the whole list.
 */
@Composable
private fun StreamList(
    groups: List<StreamGroup>,
    onSelect: (Stream) -> Unit,
) {
    val grouped = groups.size > 1
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEachIndexed { groupIndex, group ->
            if (grouped) {
                item(key = "header-$groupIndex") { SectionHeader(group.addonName) }
            }
            itemsIndexed(group.streams) { streamIndex, stream ->
                StreamRow(
                    stream = stream,
                    addonName = group.addonName,
                    showAddon = !grouped,
                    onSelect = { onSelect(stream) },
                )
                val last = groupIndex == groups.lastIndex && streamIndex == group.streams.lastIndex
                if (!last) {
                    HorizontalDivider(
                        thickness = AppSizes.borderWidth,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(addonName: String) {
    Text(
        addonName,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = AppType.label(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = LocalWindowSize.current.screenPadding,
                    end = LocalWindowSize.current.screenPadding,
                    top = AppSpacing.md,
                    bottom = AppSpacing.xs,
                ).sectionHeading(),
    )
}

/**
 * One source.
 *
 * The whole row plays it: the play glyph is part of the row instead of its own
 * 48dp target, so a phone gets one tap target per source and the list keeps its
 * width. The copy action stays separate, and only exists when the stream has a
 * link to copy. Nothing paints a surface, so the rows read as one list over the
 * page background. The addon's text is never clamped: its `name` and
 * `description` carry the format the user configured (languages, subtitles,
 * quality, size), so truncating them would hide the very information the picker
 * is for. Only the meta line, which the app composes itself, stays on one line.
 */
@Composable
private fun StreamRow(
    stream: Stream,
    addonName: String,
    showAddon: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = rememberInteraction()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val primary = listOf(stream.name, stream.title).firstOrNull { !it.isNullOrEmpty() } ?: addonName
    val description = stream.description?.takeIf { it.isNotBlank() }
    val secondary =
        description
            ?: stream.behaviorHints?.filename?.takeIf { it.isNotBlank() && it != primary }
            ?: stream.title?.takeIf { it.isNotBlank() && it != primary }
    val meta =
        listOfNotNull(
            addonName.takeIf { showAddon },
            formatSize(stream.behaviorHints?.videoSize),
        ).joinToString(" • ")
            .takeIf { it.isNotEmpty() }
    val link = stream.url ?: stream.externalUrl

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRing(interaction)
                .clickableSurface(
                    interaction,
                    onSelect,
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.action_play),
                ).padding(
                    horizontal = LocalWindowSize.current.screenPadding,
                    vertical = AppSpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MaterialIcons.Play,
            contentDescription = null,
            tint = navContentColor(selected = false, state = interaction),
            modifier = Modifier.size(AppIconSize.md),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                primary,
                style = AppType.itemTitle(),
            )
            secondary?.let {
                Text(
                    it,
                    style = AppType.bodySecondary(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            meta?.let {
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppType.caption(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (link != null) {
            AppIconButton(
                icon = MaterialIcons.Copy,
                contentDescription = stringResource(Res.string.streams_copy_link),
                onClick = { scope.launch { clipboard.setClipEntry(clipEntryOfText(link)) } },
                iconSize = AppIconSize.sm,
            )
        }
    }
}

/**
 * Notice for a refresh that failed while the list from the previous load is
 * still on screen, so the picker stays usable and the failure is not silent.
 */
@Composable
private fun RefreshFailedNotice(onRetry: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalWindowSize.current.screenPadding, vertical = AppSpacing.xs)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = AppSpacing.md, end = AppSpacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(
            stringResource(Res.string.streams_error_short),
            style = AppType.caption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AppIconButton(
            icon = MaterialIcons.Refresh,
            contentDescription = stringResource(Res.string.action_refresh),
            onClick = onRetry,
            iconSize = AppIconSize.sm,
        )
    }
}

private fun formatSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val decimals = if (value >= 10 || unit == 0) 0 else 1
    return "${round(value, decimals)} ${units[unit]}"
}

private fun round(
    value: Double,
    decimals: Int,
): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(value * factor) / factor
    return if (decimals == 0) scaled.toLong().toString() else scaled.toString()
}
