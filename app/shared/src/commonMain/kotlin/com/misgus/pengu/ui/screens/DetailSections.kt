package com.misgus.pengu.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_cancel
import com.misgus.pengu.app.shared.generated.resources.action_clear
import com.misgus.pengu.app.shared.generated.resources.action_save
import com.misgus.pengu.app.shared.generated.resources.detail_episode
import com.misgus.pengu.app.shared.generated.resources.detail_episodes
import com.misgus.pengu.app.shared.generated.resources.detail_not_rated
import com.misgus.pengu.app.shared.generated.resources.detail_rating_value
import com.misgus.pengu.app.shared.generated.resources.detail_season_short
import com.misgus.pengu.app.shared.generated.resources.detail_specials
import com.misgus.pengu.app.shared.generated.resources.detail_you_may_also_like
import com.misgus.pengu.app.shared.generated.resources.detail_your_rating
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.MarqueeText
import com.misgus.pengu.ui.components.PosterImage
import com.misgus.pengu.ui.components.RemoteImage
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.motionBorder
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.POSTER_ART_ASPECT
import com.misgus.pengu.ui.theme.PosterCaption
import com.misgus.pengu.ui.theme.cellWidth
import com.misgus.pengu.ui.theme.gridSpacing
import com.misgus.pengu.ui.theme.motionTween
import com.misgus.pengu.ui.theme.posterCardHeight
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/** Cast or crew carousel of the detail screen. */
@Composable
internal fun PeopleRow(
    title: String,
    people: List<MetaPerson>,
) {
    val windowSize = LocalWindowSize.current
    Column {
        Text(
            title,
            modifier = Modifier.padding(horizontal = windowSize.screenPadding),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(AppSpacing.md))
        LazyRow(
            modifier = Modifier.height(200.dp),
            contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            items(people) { person -> PersonCard(person) }
        }
    }
}

@Composable
private fun PersonCard(person: MetaPerson) {
    val interaction = rememberInteraction()
    Column(
        modifier = Modifier.width(128.dp).hoverable(interaction.source),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val photo = person.photo
            if (!photo.isNullOrEmpty()) {
                RemoteImage(
                    url = photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    person.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        MarqueeText(
            text = person.name,
            style = PosterCaption.title(),
            active = interaction.active,
            textAlign = TextAlign.Center,
        )
        person.character?.takeIf { it.isNotEmpty() }?.let {
            MarqueeText(
                text = it,
                style = PosterCaption.meta(),
                active = interaction.active,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Label/value block with release data (released date, country, ...). */
@Composable
internal fun DetailsSection(entries: List<Pair<String, String>>) {
    val windowSize = LocalWindowSize.current
    Column {
        Row(
            modifier = Modifier.padding(horizontal = windowSize.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xl * 2),
        ) {
            entries.forEach { (label, value) ->
                Column {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** "You may also like" carousel. */
@Composable
internal fun SimilarRow(
    items: List<MetaPreview>,
    onOpen: (MetaPreview) -> Unit,
) {
    // The same size as the catalog grids, so a title looks the same everywhere.
    val settingsRepository = LocalAppServices.current.settings
    val settings by settingsRepository.state.collectAsStateWithLifecycle()
    val posterSize = settings.posterSize
    val windowSize = LocalWindowSize.current
    Column {
        Text(
            stringResource(Res.string.detail_you_may_also_like),
            modifier = Modifier.padding(horizontal = AppSpacing.xl).sectionHeading(),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(AppSpacing.sm))
        val cardWidth = posterSize.cellWidth(windowSize)
        LazyRow(
            modifier = Modifier.height(posterCardHeight(cardWidth, settings.hideLabels)),
            contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
        ) {
            items(items) { item ->
                val interaction = rememberInteraction()
                val borderColor =
                    animateColorAsState(
                        targetValue =
                            if (interaction.active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        animationSpec = motionTween(),
                        label = "borderColor",
                    )
                Column(
                    modifier =
                        Modifier
                            .width(cardWidth)
                            .clickableSurface(interaction, onClick = { onOpen(item) }, indication = null),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(POSTER_ART_ASPECT)
                            .motionBorder(color = { borderColor.value }, width = AppSizes.borderWidth, cornerRadius = AppCorner.small)
                            .clip(MaterialTheme.shapes.small),
                    ) {
                        PosterImage(item.poster, Modifier.fillMaxSize())
                    }
                    if (!settings.hideLabels) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        MarqueeText(
                            text = item.name,
                            style = PosterCaption.title(),
                            active = interaction.active,
                        )
                        item.releaseInfo?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = PosterCaption.meta(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Episode picker with a season selector for series. */
@Composable
internal fun EpisodesSection(
    videos: List<MetaVideo>,
    onPlayEpisode: (MetaVideo) -> Unit,
) {
    val seasons = remember(videos) { orderSeasons(videos.mapNotNull { it.season }.distinct()) }
    if (seasons.isEmpty()) return
    val windowSize = LocalWindowSize.current
    var season by remember(seasons) { mutableStateOf(defaultSeason(seasons)) }
    val episodes =
        remember(videos, season) {
            videos.filter { it.season == season }.sortedBy { it.episode?.number ?: it.number ?: 0 }
        }
    val episodesState = rememberLazyListState()
    // A season change swaps the list under the same rail; without this the rail
    // would keep the previous season's offset and open S2 at the episode S1 was
    // left on.
    LaunchedEffect(season) { episodesState.scrollToItem(0) }

    Column {
        Text(
            stringResource(Res.string.detail_episodes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = windowSize.screenPadding).sectionHeading(),
        )
        if (seasons.size > 1) {
            Spacer(Modifier.height(AppSpacing.sm))
            // The picker scrolls like the episode rail: a long run of seasons, or
            // a phone's narrow width, would otherwise crowd or clip the buttons.
            LazyRow(
                contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                items(seasons) { value ->
                    ActionButton(
                        text = seasonLabel(value),
                        onClick = { season = value },
                        style = ActionButtonStyle.Text,
                        selected = value == season,
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.md))
        LazyRow(
            state = episodesState,
            modifier = Modifier.heightIn(min = 190.dp),
            contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
        ) {
            items(episodes) { video -> EpisodeCard(video) { onPlayEpisode(video) } }
        }
    }
}

/**
 * Seasons in display order: regular ones ascending first, then season `0`
 * ("Specials") at the end.
 */
private fun orderSeasons(seasons: List<Season>): List<Season> =
    seasons.filter { it.number != 0 }.sortedBy { it.number }.let { regular ->
        if (seasons.any { it.number == 0 }) regular + Season(0) else regular
    }

/** Season 1 opens the picker when present; otherwise the first season shown. */
private fun defaultSeason(seasons: List<Season>): Season = seasons.firstOrNull { it.number == 1 } ?: seasons.first()

/** Season `0` is presented as "Specials"; every other season as `S<n>`. */
@Composable
private fun seasonLabel(season: Season): String =
    if (season.number == 0) stringResource(Res.string.detail_specials) else stringResource(Res.string.detail_season_short, season.number)

private val RELEASE_MONTHS =
    listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

private val RELEASE_DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

/**
 * Formats an addon release value (`2024-03-01` or a full ISO datetime) as a
 * short, readable English date such as `1 March 2024`.
 *
 * Returns `null` when there is no usable date, so the caller can omit the line.
 */
internal fun formatReleaseDate(raw: String?): String? {
    val match = RELEASE_DATE.find(raw ?: "") ?: return null
    val (year, month, day) = match.destructured
    val monthName = RELEASE_MONTHS.getOrNull(month.toInt() - 1) ?: return null
    return "${day.toInt()} $monthName $year"
}

@Composable
private fun EpisodeCard(
    video: MetaVideo,
    onTap: () -> Unit,
) {
    val number = video.episode ?: video.number
    val title = video.title ?: stringResource(Res.string.detail_episode, number?.toString() ?: "").trim()
    val interaction = rememberInteraction()
    val borderColor =
        animateColorAsState(
            targetValue = if (interaction.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = motionTween(),
            label = "borderColor",
        )
    Column(modifier = Modifier.width(220.dp).clickableSurface(interaction, onTap, indication = null)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .motionBorder(color = { borderColor.value }, width = AppSizes.borderWidth, cornerRadius = AppCorner.small)
                .clip(MaterialTheme.shapes.small),
        ) {
            PosterImage(video.thumbnail, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(AppSpacing.sm))
        MarqueeText(
            text = "${number?.let { "$it. " } ?: ""}$title".trim(),
            style = PosterCaption.title(),
            active = interaction.active,
        )
        formatReleaseDate(video.released)?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = PosterCaption.meta())
        }
    }
}

/** 1–10 rating dialog for the current title. */
@Composable
internal fun RatingDialog(
    current: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var value by remember { mutableStateOf(current ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.detail_your_rating)) },
        text = {
            Column {
                Text(
                    if (value == 0) stringResource(Res.string.detail_not_rated) else stringResource(Res.string.detail_rating_value, value),
                    style = MaterialTheme.typography.titleLarge,
                )
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toInt() },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }
        },
        confirmButton = {
            ActionButton(
                text = stringResource(Res.string.action_save),
                onClick = {
                    onSave(if (value == 0) null else value)
                    onDismiss()
                },
                style = ActionButtonStyle.Text,
            )
        },
        dismissButton = {
            Row {
                ActionButton(
                    text = stringResource(Res.string.action_clear),
                    onClick = {
                        onSave(null)
                        onDismiss()
                    },
                    style = ActionButtonStyle.Text,
                )
                ActionButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                    style = ActionButtonStyle.Text,
                )
            }
        },
    )
}
