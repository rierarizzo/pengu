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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.detail_episode
import com.misgus.pengu.app.shared.generated.resources.detail_episodes
import com.misgus.pengu.app.shared.generated.resources.detail_season_short
import com.misgus.pengu.app.shared.generated.resources.detail_specials
import com.misgus.pengu.app.shared.generated.resources.detail_you_may_also_like
import com.misgus.pengu.app.shared.generated.resources.media_rating
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.ProgressThresholds
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.TmdbEpisode
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.CardProgressBar
import com.misgus.pengu.ui.components.MarqueeText
import com.misgus.pengu.ui.components.PosterImage
import com.misgus.pengu.ui.components.RemoteImage
import com.misgus.pengu.ui.components.SkeletonBox
import com.misgus.pengu.ui.components.WatchedBadge
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.motionBorder
import com.misgus.pengu.ui.components.ratingText
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.rememberShimmerProgress
import com.misgus.pengu.ui.components.runtimeLabel
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppCorner
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.COMPACT_SCALE
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.POSTER_ART_ASPECT
import com.misgus.pengu.ui.theme.PosterCaption
import com.misgus.pengu.ui.theme.WindowSize
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
            modifier = Modifier.padding(horizontal = windowSize.screenPadding).sectionHeading(),
            style = AppType.sectionTitle(),
        )
        Spacer(Modifier.height(AppSpacing.md))
        LazyRow(
            // Just tall enough for the avatar plus its two caption lines, so the
            // section leaves no dead space under it.
            modifier = Modifier.height(88.dp + PosterCaption.blockHeight()),
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
            Text(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = PosterCaption.meta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Titled table of label/value rows for a title's extra metadata. */
@Composable
internal fun DetailsSection(
    title: String,
    entries: List<Pair<String, String>>,
) {
    val windowSize = LocalWindowSize.current
    Column {
        Text(
            title,
            style = AppType.sectionTitle(),
            modifier = Modifier.padding(horizontal = windowSize.screenPadding).sectionHeading(),
        )
        Spacer(Modifier.height(AppSpacing.md))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = windowSize.screenPadding)) {
            entries.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(AppSizes.borderWidth).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label.uppercase(),
                        style = AppType.caption(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value,
                        style = AppType.body(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
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
            modifier = Modifier.padding(horizontal = windowSize.screenPadding).sectionHeading(),
            style = AppType.sectionTitle(),
        )
        Spacer(Modifier.height(AppSpacing.md))
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
    contentId: String,
    videos: List<MetaVideo>,
    episodeDetails: Map<Int, List<TmdbEpisode>>,
    seasonsLoading: Set<Int>,
    onLoadSeason: (Int) -> Unit,
    onPlayEpisode: (MetaVideo) -> Unit,
) {
    val seasons = remember(videos) { orderSeasons(videos.mapNotNull { it.season }.distinct()) }
    if (seasons.isEmpty()) return
    val windowSize = LocalWindowSize.current
    val watchState = rememberEpisodeWatchState(contentId)
    var season by remember(seasons) { mutableStateOf(defaultSeason(seasons)) }
    val episodes =
        remember(videos, season) {
            videos.filter { it.season == season }.sortedBy { it.episode?.number ?: it.number ?: 0 }
        }
    // TMDB's copy of the open season, indexed by episode number so a card finds
    // its own row in one lookup.
    val details =
        remember(episodeDetails, season) {
            episodeDetails[season.number].orEmpty().associateBy { it.episodeNumber }
        }
    // Cards are painted once, with the copy that stays: while the open season is
    // in flight they hold a placeholder the size of a card instead of the addon's
    // still and title, which TMDB would replace a moment later.
    val loading = season.number in seasonsLoading
    val episodesState = rememberLazyListState()
    // A season change swaps the list under the same rail; without this the rail
    // would keep the previous season's offset and open S2 at the episode S1 was
    // left on.
    LaunchedEffect(season) {
        onLoadSeason(season.number)
        episodesState.scrollToItem(0)
    }

    Column {
        Text(
            stringResource(Res.string.detail_episodes),
            style = AppType.sectionTitle(),
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
            // No fixed height: a card reserves its own synopsis lines, so the
            // rail is as tall as the tallest one instead of guessing with a dp.
            contentPadding = PaddingValues(horizontal = windowSize.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(windowSize.gridSpacing),
        ) {
            items(episodes) { video ->
                val key = episodeKey(video.season, video.episode)
                EpisodeCard(
                    video = video,
                    detail = details[video.episode?.number ?: video.number],
                    loading = loading,
                    // A progress bar and a watched badge say the same thing twice,
                    // so the bar is dropped once an episode counts as watched.
                    watched = watchState.isWatched(key),
                    progress = key?.let(watchState::fraction)?.takeUnless { watchState.isWatched(key) },
                ) { onPlayEpisode(video) }
            }
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

/**
 * Width of one episode card.
 *
 * A phone steps it down by the same factor the poster rails use
 * ([COMPACT_SCALE]), so an episode card stays in proportion with the posters
 * above and below it instead of filling the screen.
 */
private val EpisodeCardWidth: Dp
    @Composable get() = if (LocalWindowSize.current == WindowSize.COMPACT) 320.dp * COMPACT_SCALE else 320.dp

/** Lines of episode synopsis a card shows; the rest is behind the player title. */
private const val EPISODE_SYNOPSIS_LINES = 3

@Composable
private fun EpisodeCard(
    video: MetaVideo,
    detail: TmdbEpisode?,
    loading: Boolean,
    watched: Boolean,
    progress: Float?,
    onTap: () -> Unit,
) {
    // The card is still clickable while it loads: opening the streams does not
    // depend on the copy TMDB is about to send.
    if (loading) {
        EpisodeCardSkeleton(onTap)
        return
    }
    val number = video.episode ?: video.number
    val title = detail?.name ?: video.title ?: stringResource(Res.string.detail_episode, number?.toString() ?: "").trim()
    val interaction = rememberInteraction()
    val borderColor =
        animateColorAsState(
            targetValue = if (interaction.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = motionTween(),
            label = "borderColor",
        )
    Column(modifier = Modifier.width(EpisodeCardWidth).clickableSurface(interaction, onTap, indication = null)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .motionBorder(color = { borderColor.value }, width = AppSizes.borderWidth, cornerRadius = AppCorner.small)
                .clip(MaterialTheme.shapes.small),
        ) {
            PosterImage(detail?.still ?: video.thumbnail, Modifier.fillMaxSize())
            progress?.let { fraction ->
                CardProgressBar(fraction, Modifier.align(Alignment.BottomCenter))
            }
            if (watched) {
                Box(Modifier.align(Alignment.TopEnd).padding(AppSpacing.xs)) { WatchedBadge() }
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        MarqueeText(
            text = "${number?.let { "$it. " } ?: ""}$title".trim(),
            style = PosterCaption.title(),
            active = interaction.active,
        )
        val meta = episodeMetaParts(video, detail)
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = PosterCaption.meta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The addon's own copy is used when it sends one: it is already there and
        // it is the final value, so nothing has to be swapped in later.
        (detail?.overview ?: video.description)?.let {
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                it,
                // Two lines are always reserved, so a short synopsis does not
                // make its card shorter than the rest of the rail.
                minLines = 2,
                maxLines = EPISODE_SYNOPSIS_LINES,
                overflow = TextOverflow.Ellipsis,
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Score, runtime and air date of an episode, in the order a card reads them.
 *
 * Each part is the addon's when it sent one and TMDB's otherwise, and it is
 * dropped when neither has a value, so a missing runtime never leaves a dangling
 * separator. The score is the addon's IMDb one when present; TMDB only carries an
 * audience score.
 */
@Composable
private fun episodeMetaParts(
    video: MetaVideo,
    detail: TmdbEpisode?,
): List<String> =
    buildList {
        val rating = detail?.rating ?: video.rating
        rating?.let { add(stringResource(Res.string.media_rating, ratingText(it))) }
        (detail?.runtimeMinutes ?: video.runtimeMinutes)?.let { add(runtimeLabel(it)) }
        formatReleaseDate(detail?.airDate ?: video.released)?.let { add(it) }
    }

/**
 * The shape of a card with no copy in it yet, at the size a loaded one takes.
 *
 * Heights match the text roles the real card uses, so the rail does not jump
 * when the season lands.
 */
@Composable
private fun EpisodeCardSkeleton(onTap: () -> Unit) {
    val interaction = rememberInteraction()
    val progress = rememberShimmerProgress()
    Column(modifier = Modifier.width(EpisodeCardWidth).clickableSurface(interaction, onTap, indication = null)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            SkeletonBox(progress, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(AppSpacing.sm))
        SkeletonBox(progress, Modifier.fillMaxWidth(0.7f).height(TITLE_LINE))
        Spacer(Modifier.height(AppSpacing.xs))
        SkeletonBox(progress, Modifier.fillMaxWidth(0.45f).height(META_LINE))
        Spacer(Modifier.height(AppSpacing.xs))
        SkeletonBox(progress, Modifier.fillMaxWidth().height(SYNOPSIS_LINE))
        Spacer(Modifier.height(AppSpacing.xxs))
        SkeletonBox(progress, Modifier.fillMaxWidth(0.85f).height(SYNOPSIS_LINE))
    }
}

/** Line heights the placeholder reserves: one title, one meta, two synopsis lines. */
private val TITLE_LINE = 20.dp
private val META_LINE = 16.dp
private val SYNOPSIS_LINE = 18.dp

/** Watched episodes and resumable progress of one title, as the cards show them. */
internal data class EpisodeWatchState(
    /** `true` when the whole title is marked watched, which covers every episode. */
    val titleWatched: Boolean = false,
    val watched: Set<Pair<Int, Int>> = emptySet(),
    /** Where playback was left, for the episodes that are part-way. */
    val progress: Map<Pair<Int, Int>, Float> = emptyMap(),
) {
    fun isWatched(key: Pair<Int, Int>?): Boolean = titleWatched || (key != null && key in watched)

    fun fraction(key: Pair<Int, Int>): Float? = progress[key]
}

/**
 * Reads the watch state the episode cards badge.
 *
 * Read here, next to the cards, instead of in the detail ViewModel: only this
 * rail shows it, and the hero's own watched toggle is optimistic and lives in
 * `DetailScreen`. It follows the repository's `changes` flow, so an episode
 * watched anywhere refreshes the badge.
 */
@Composable
private fun rememberEpisodeWatchState(contentId: String): EpisodeWatchState {
    val progress = LocalAppServices.current.progress
    val changes by progress.changes.collectAsStateWithLifecycle()
    var state by remember(contentId) { mutableStateOf(EpisodeWatchState()) }
    LaunchedEffect(contentId, changes) {
        val entries = runCatchingCancellable { progress.watchedEntries(listOf(contentId)) }.getOrDefault(emptyList())
        val played =
            runCatchingCancellable { progress.all() }
                .getOrDefault(emptyList())
                .filter { it.contentId == contentId }
        state = episodeWatchState(entries, played)
    }
    return state
}

/**
 * The cards' watch state out of the account's watched rows and playback entries.
 *
 * An episode played past [ProgressThresholds.COMPLETION] counts as watched, the
 * same rule the poster badges follow; the card drops the bar for anything that
 * counts as watched, because a bar and a check say the same thing twice. A
 * title-level marker covers every episode.
 */
internal fun episodeWatchState(
    entries: List<WatchedEntry>,
    progressEntries: List<WatchProgress>,
): EpisodeWatchState {
    val fractions =
        progressEntries
            .mapNotNull { entry ->
                val key = episodeKey(entry.season, entry.episode) ?: return@mapNotNull null
                entry.fraction
                    ?.toFloat()
                    ?.takeIf { it > 0f }
                    ?.let { key to it }
            }.toMap()
    val finished = fractions.filterValues { it >= ProgressThresholds.COMPLETION.toFloat() }.keys
    return EpisodeWatchState(
        titleWatched = entries.any { it.isMarker },
        watched = entries.mapNotNull { episodeKey(it.season, it.episode) }.toSet() + finished,
        progress = fractions - finished,
    )
}

/** Season and episode numbers as one comparable key, or `null` for a title-level row. */
private fun episodeKey(
    season: Season?,
    episode: Episode?,
): Pair<Int, Int>? = if (season != null && episode != null) season.number to episode.number else null
