package com.misgus.pengu.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.calendar_error_title
import com.misgus.pengu.app.shared.generated.resources.calendar_more
import com.misgus.pengu.app.shared.generated.resources.calendar_next_month
import com.misgus.pengu.app.shared.generated.resources.calendar_no_upcoming
import com.misgus.pengu.app.shared.generated.resources.calendar_no_upcoming_message
import com.misgus.pengu.app.shared.generated.resources.calendar_prev_month
import com.misgus.pengu.app.shared.generated.resources.month_april
import com.misgus.pengu.app.shared.generated.resources.month_august
import com.misgus.pengu.app.shared.generated.resources.month_december
import com.misgus.pengu.app.shared.generated.resources.month_february
import com.misgus.pengu.app.shared.generated.resources.month_january
import com.misgus.pengu.app.shared.generated.resources.month_july
import com.misgus.pengu.app.shared.generated.resources.month_june
import com.misgus.pengu.app.shared.generated.resources.month_march
import com.misgus.pengu.app.shared.generated.resources.month_may
import com.misgus.pengu.app.shared.generated.resources.month_november
import com.misgus.pengu.app.shared.generated.resources.month_october
import com.misgus.pengu.app.shared.generated.resources.month_september
import com.misgus.pengu.app.shared.generated.resources.weekday_fri
import com.misgus.pengu.app.shared.generated.resources.weekday_mon
import com.misgus.pengu.app.shared.generated.resources.weekday_sat
import com.misgus.pengu.app.shared.generated.resources.weekday_sun
import com.misgus.pengu.app.shared.generated.resources.weekday_thu
import com.misgus.pengu.app.shared.generated.resources.weekday_tue
import com.misgus.pengu.app.shared.generated.resources.weekday_wed
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.Message
import com.misgus.pengu.ui.components.PosterImage
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.mirrorInRtl
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.shell.ContentNavigator
import com.misgus.pengu.ui.shell.ContentRoute
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Calendar section: a full-screen month grid where each day shows the episodes
 * of the Library's series that air on it.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    navigator: ContentNavigator,
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CalendarContent(
        state = state,
        onOpenDetail = { navigator.push(ContentRoute.Detail(it, knownSaved = true)) },
    )
}

@Composable
private fun CalendarContent(
    state: CalendarUiState,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    when {
        state.error != null && state.entries.isEmpty() ->
            Message(
                icon = FluentIcons.Error,
                title = stringResource(Res.string.calendar_error_title),
                message = stringResource(state.error.toUserMessage()),
            )
        state.hasLoaded && state.entries.isEmpty() ->
            Message(
                icon = FluentIcons.CalendarMonth,
                title = stringResource(Res.string.calendar_no_upcoming),
                message = stringResource(Res.string.calendar_no_upcoming_message),
            )
        // The grid paints immediately and fills in as series resolve.
        else ->
            MonthCalendar(
                entries = state.entries,
                isLoading = state.isLoading,
                onOpenDetail = onOpenDetail,
            )
    }
}

@Composable
private fun MonthCalendar(
    entries: List<CalendarEntry>,
    isLoading: Boolean,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val byDate = remember(entries) { entries.groupBy { it.date } }
    var month by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }

    Column(Modifier.fillMaxSize()) {
        MonthHeader(
            month = month,
            isLoading = isLoading,
            onPrevious = { month = month.plus(-1, DateTimeUnit.MONTH) },
            onNext = { month = month.plus(1, DateTimeUnit.MONTH) },
        )
        WeekdayHeader()
        MonthGrid(
            month = month,
            today = today,
            byDate = byDate,
            onOpenDetail = onOpenDetail,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthHeader(
    month: LocalDate,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LocalWindowSize.current.screenPadding, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = FluentIcons.ChevronLeft,
            contentDescription = stringResource(Res.string.calendar_prev_month),
            onClick = onPrevious,
            iconModifier = Modifier.mirrorInRtl(),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatMonth(month),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.sectionHeading(),
            )
            if (isLoading) {
                Spacer(Modifier.width(AppSpacing.sm))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = AppSizes.progressStroke)
            }
        }
        AppIconButton(
            icon = FluentIcons.ChevronRight,
            contentDescription = stringResource(Res.string.calendar_next_month),
            onClick = onNext,
            iconModifier = Modifier.mirrorInRtl(),
        )
    }
}

@Composable
private fun WeekdayHeader() {
    val labels =
        listOf(
            stringResource(Res.string.weekday_mon),
            stringResource(Res.string.weekday_tue),
            stringResource(Res.string.weekday_wed),
            stringResource(Res.string.weekday_thu),
            stringResource(Res.string.weekday_fri),
            stringResource(Res.string.weekday_sat),
            stringResource(Res.string.weekday_sun),
        )
    Row(Modifier.fillMaxWidth().padding(horizontal = LocalWindowSize.current.screenPadding)) {
        for (day in labels) {
            Text(
                text = day,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = AppSpacing.xs),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    byDate: Map<LocalDate, List<CalendarEntry>>,
    onOpenDetail: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysInMonth = month.daysUntil(month.plus(1, DateTimeUnit.MONTH))
    val leading = month.dayOfWeek.ordinal
    val cells: List<LocalDate?> =
        buildList {
            repeat(leading) { add(null) }
            for (day in 1..daysInMonth) add(LocalDate(month.year, month.month, day))
            while (size % DAYS_PER_WEEK != 0) add(null)
        }

    // Each week takes an equal share of the available height, so the grid fills
    // the window instead of being a fixed-height block that leaves a gap.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = LocalWindowSize.current.screenPadding,
                    end = LocalWindowSize.current.screenPadding,
                    bottom = AppSpacing.lg,
                ),
    ) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        isToday = date == today,
                        entries = date?.let { byDate[it].orEmpty() }.orEmpty(),
                        onOpenDetail = onOpenDetail,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    entries: List<CalendarEntry>,
    onOpenDetail: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier =
            modifier
                .clipToBounds()
                .border(AppSizes.borderWidth, borderColor)
                .background(if (entries.isEmpty()) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface)
                .padding(AppSpacing.xs),
    ) {
        date?.let {
            Text(
                text = it.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                entries.take(MAX_ENTRIES_PER_DAY).forEach { entry ->
                    EpisodeChip(entry = entry, onTap = { onOpenDetail(entry.item) })
                }
                if (entries.size > MAX_ENTRIES_PER_DAY) {
                    Text(
                        text = stringResource(Res.string.calendar_more, entries.size - MAX_ENTRIES_PER_DAY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeChip(
    entry: CalendarEntry,
    onTap: () -> Unit,
) {
    val interaction = rememberInteraction()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .focusRing(interaction, cornerRadius = AppCorner.extraSmall)
                .clickableSurface(interaction, onTap)
                .padding(AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 30.dp, height = 42.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
        ) {
            PosterImage(entry.item.poster, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = episodeLabel(entry),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.video.title?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun episodeLabel(entry: CalendarEntry): String {
    val video = entry.video
    return buildString {
        video.season?.let { append("S$it") }
        video.episode?.let { append("E$it") }
        if (isEmpty()) append(video.title.orEmpty())
    }
}

/** Formats a month as `May 2025`. */
@Composable
private fun formatMonth(month: LocalDate): String {
    val months =
        listOf(
            stringResource(Res.string.month_january),
            stringResource(Res.string.month_february),
            stringResource(Res.string.month_march),
            stringResource(Res.string.month_april),
            stringResource(Res.string.month_may),
            stringResource(Res.string.month_june),
            stringResource(Res.string.month_july),
            stringResource(Res.string.month_august),
            stringResource(Res.string.month_september),
            stringResource(Res.string.month_october),
            stringResource(Res.string.month_november),
            stringResource(Res.string.month_december),
        )
    return "${months[month.month.ordinal]} ${month.year}"
}

private const val DAYS_PER_WEEK = 7

/** One entry per day: the richer chip (poster, series, episode) needs the room. */
private const val MAX_ENTRIES_PER_DAY = 1
