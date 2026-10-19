package com.misgus.pengu.ui.streams

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_clear
import com.misgus.pengu.app.shared.generated.resources.action_close
import com.misgus.pengu.app.shared.generated.resources.action_play
import com.misgus.pengu.app.shared.generated.resources.action_refresh
import com.misgus.pengu.app.shared.generated.resources.streams_copy_link
import com.misgus.pengu.app.shared.generated.resources.streams_count_many
import com.misgus.pengu.app.shared.generated.resources.streams_count_one
import com.misgus.pengu.app.shared.generated.resources.streams_empty
import com.misgus.pengu.app.shared.generated.resources.streams_error
import com.misgus.pengu.app.shared.generated.resources.streams_error_short
import com.misgus.pengu.app.shared.generated.resources.streams_filter
import com.misgus.pengu.app.shared.generated.resources.streams_loading
import com.misgus.pengu.app.shared.generated.resources.streams_no_match
import com.misgus.pengu.core.model.Stream
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.AppSearchField
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterImage
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.platform.clipEntryOfText
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.RouteViewModelStoreOwner
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScale
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowWidth
import com.misgus.pengu.ui.theme.motionLayer
import com.misgus.pengu.ui.theme.motionTween
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Widest the dialog gets, and a floor so it stays usable on a small window. */
private val StreamsDialogMaxWidth = 900.dp
private val StreamsDialogMinWidth = 280.dp

/**
 * Modal that lists the available streams.
 *
 * The stream text is shown as-is: addons put their configured, multi-line format
 * in `name` and `description`, so the UI just renders it.
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            val viewModel: StreamsViewModel =
                viewModel(key = request.id) {
                    StreamsViewModel(services.streams, request.type, request.id)
                }
            LaunchedEffect(request) { viewModel.load() }
            val state by viewModel.state.collectAsStateWithLifecycle()

            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            val enterAlpha =
                animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = motionTween(),
                    label = "dialogAlpha",
                )
            val enterScale =
                animateFloatAsState(
                    targetValue = if (shown) 1f else AppScale.DIALOG_ENTER,
                    animationSpec = motionTween(durationMillis = AppMotion.MEDIUM),
                    label = "dialogScale",
                )
            // A dialog opens its own window, so cap the width to the app window
            // instead of trusting the fixed size.
            val dialogWidth =
                minOf(StreamsDialogMaxWidth, LocalWindowWidth.current - AppSpacing.xl * 2)
                    .coerceAtLeast(StreamsDialogMinWidth)
            Box(
                modifier =
                    Modifier
                        .width(dialogWidth)
                        .fillMaxHeight(0.82f)
                        .motionLayer(scale = { enterScale.value }, alpha = { enterAlpha.value })
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.background),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Header(
                        request = request,
                        subtitle = subtitle(state),
                        onRefresh = { viewModel.load(force = true) },
                        onClose = onDismiss,
                    )
                    AppSearchField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = stringResource(Res.string.streams_filter),
                        clearDescription = stringResource(Res.string.action_clear),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
                    )
                    Content(state = state, onPlay = onSelect)
                }
            }
        }
    }
}

@Composable
private fun Header(
    request: StreamsRequest,
    subtitle: String,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        PosterImage(request.background, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(AppScrim.dialogHeader))
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            AppIconButton(
                icon = FluentIcons.Refresh,
                contentDescription = stringResource(Res.string.action_refresh),
                onClick = onRefresh,
            )
            AppIconButton(
                icon = FluentIcons.Close,
                contentDescription = stringResource(Res.string.action_close),
                onClick = onClose,
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(AppSpacing.xl),
        ) {
            Text(
                request.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = AppType.dialogTitle(),
            )
            request.year?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = AppType.body())
            }
            Text(subtitle, style = AppType.bodySecondary())
        }
    }
}

@Composable
private fun Content(
    state: StreamsUiState,
    onPlay: (Stream) -> Unit,
) {
    when {
        state.isLoading && !state.hasLoaded ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null && !state.hasLoaded ->
            Message(
                icon = FluentIcons.Error,
                title = stringResource(Res.string.streams_error),
            )
        state.entries.isEmpty() ->
            Message(
                icon = FluentIcons.SearchOff,
                title =
                    if (state.groups.isEmpty()) {
                        stringResource(Res.string.streams_empty)
                    } else {
                        stringResource(Res.string.streams_no_match)
                    },
            )
        else ->
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.entries) { entry ->
                    StreamCard(
                        stream = entry.stream,
                        addonName = entry.addonName,
                        onPlay = { onPlay(entry.stream) },
                    )
                }
            }
    }
}

@Composable
private fun StreamCard(
    stream: Stream,
    addonName: String,
    onPlay: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val primary = listOf(stream.name, stream.title).firstOrNull { !it.isNullOrEmpty() } ?: addonName
    val secondary =
        listOf(stream.description, stream.title)
            .firstOrNull { !it.isNullOrEmpty() && it != primary }
    val size = formatSize(stream.behaviorHints?.videoSize)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xs)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .padding(AppSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconButton(
            icon = FluentIcons.Play,
            contentDescription = stringResource(Res.string.action_play),
            onClick = onPlay,
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                primary,
                style = AppType.itemTitle(),
            )
            secondary?.let {
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(it, style = AppType.bodySecondary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        size?.let {
            Spacer(Modifier.width(AppSpacing.sm))
            Text(it, style = AppType.bodySecondary())
        }
        AppIconButton(
            icon = FluentIcons.Copy,
            contentDescription = stringResource(Res.string.streams_copy_link),
            onClick = {
                (stream.url ?: stream.externalUrl)?.let { url ->
                    scope.launch { clipboard.setClipEntry(clipEntryOfText(url)) }
                }
            },
        )
    }
}

@Composable
private fun subtitle(state: StreamsUiState): String =
    when {
        state.isLoading && !state.hasLoaded -> stringResource(Res.string.streams_loading)
        state.error != null && !state.hasLoaded -> stringResource(Res.string.streams_error_short)
        state.totalCount == 1 -> stringResource(Res.string.streams_count_one)
        else -> stringResource(Res.string.streams_count_many, state.totalCount)
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
