package com.misgus.pengu.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.detail_budget
import com.misgus.pengu.app.shared.generated.resources.detail_cast
import com.misgus.pengu.app.shared.generated.resources.detail_country
import com.misgus.pengu.app.shared.generated.resources.detail_crew
import com.misgus.pengu.app.shared.generated.resources.detail_details
import com.misgus.pengu.app.shared.generated.resources.detail_director
import com.misgus.pengu.app.shared.generated.resources.detail_episode_generic
import com.misgus.pengu.app.shared.generated.resources.detail_episodes
import com.misgus.pengu.app.shared.generated.resources.detail_original_language
import com.misgus.pengu.app.shared.generated.resources.detail_released
import com.misgus.pengu.app.shared.generated.resources.detail_revenue
import com.misgus.pengu.app.shared.generated.resources.detail_seasons
import com.misgus.pengu.app.shared.generated.resources.detail_status
import com.misgus.pengu.app.shared.generated.resources.detail_status_canceled
import com.misgus.pengu.app.shared.generated.resources.detail_status_ended
import com.misgus.pengu.app.shared.generated.resources.detail_status_in_production
import com.misgus.pengu.app.shared.generated.resources.detail_status_pilot
import com.misgus.pengu.app.shared.generated.resources.detail_status_planned
import com.misgus.pengu.app.shared.generated.resources.detail_status_post_production
import com.misgus.pengu.app.shared.generated.resources.detail_status_released
import com.misgus.pengu.app.shared.generated.resources.detail_status_returning
import com.misgus.pengu.app.shared.generated.resources.detail_status_rumored
import com.misgus.pengu.app.shared.generated.resources.detail_writer
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.ui.detail.CrewMember
import com.misgus.pengu.ui.detail.CrewRole
import com.misgus.pengu.ui.detail.DetailUiState
import com.misgus.pengu.ui.streams.StreamsRequest
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.safeBottomInset
import org.jetbrains.compose.resources.stringResource

/**
 * Scrollable body of the detail screen: episodes, cast/crew, release details and
 * the "more like this" row.
 *
 * Kept apart from the billboard so each file stays focused on one part of the
 * screen.
 */
@Composable
internal fun DetailScrollContent(
    state: DetailUiState,
    item: LibraryItem,
    openStreams: (StreamsRequest) -> Unit,
    onOpenDetail: (LibraryItem) -> Unit,
) {
    if (state.isSeries && state.episodes.isNotEmpty()) {
        Spacer(Modifier.height(SectionSpacing))
        val episodeFallback = stringResource(Res.string.detail_episode_generic)
        EpisodesSection(
            contentId = state.contentId,
            videos = state.episodes,
        ) { video ->
            openStreams(
                StreamsRequest(
                    type = ContentTypes.SERIES,
                    id = video.id,
                    title = "${state.name} · ${video.title ?: episodeFallback}",
                    year = state.year?.toString(),
                    contentId = state.contentId,
                    // The addon's own season and episode: an episode id such as
                    // `kitsu:43248:1` cannot be read as `content:season:episode`.
                    season = video.season,
                    episode = video.episode,
                    episodeTitle = video.title,
                ),
            )
        }
    }

    val detailEntries =
        buildList {
            state.released?.takeIf { it.isNotEmpty() }?.let { add(stringResource(Res.string.detail_released) to it) }
            state.country?.takeIf { it.isNotEmpty() }?.let { add(stringResource(Res.string.detail_country) to it) }
            state.status?.let { add(stringResource(Res.string.detail_status) to statusLabel(it)) }
            state.originalLanguage?.let { add(stringResource(Res.string.detail_original_language) to it) }
            state.seasonCount?.let { add(stringResource(Res.string.detail_seasons) to it.toString()) }
            state.episodeCount?.let { add(stringResource(Res.string.detail_episodes) to it.toString()) }
            state.budget?.let { add(stringResource(Res.string.detail_budget) to formatMoney(it)) }
            state.revenue?.let { add(stringResource(Res.string.detail_revenue) to formatMoney(it)) }
        }
    if (state.cast.isNotEmpty()) {
        Spacer(Modifier.height(SectionSpacing))
        PeopleRow(stringResource(Res.string.detail_cast), state.cast)
    }
    if (state.crew.isNotEmpty()) {
        val directorLabel = stringResource(Res.string.detail_director)
        val writerLabel = stringResource(Res.string.detail_writer)
        val crew = state.crew.map { member -> member.toMetaPerson(directorLabel, writerLabel) }
        Spacer(Modifier.height(SectionSpacing))
        PeopleRow(stringResource(Res.string.detail_crew), crew)
    }
    if (detailEntries.isNotEmpty()) {
        Spacer(Modifier.height(SectionSpacing))
        DetailsSection(stringResource(Res.string.detail_details), detailEntries)
    }
    if (state.similar.isNotEmpty()) {
        Spacer(Modifier.height(SectionSpacing))
        SimilarRow(state.similar, addonBaseUrl = item.addonBaseUrl) { preview ->
            onOpenDetail(LibraryItem.fromPreview(preview, addonBaseUrl = item.addonBaseUrl))
        }
    }
    Spacer(Modifier.height(AppSpacing.xl + safeBottomInset))
}

/** Vertical gap between two detail sections; the same everywhere so they read evenly. */
private val SectionSpacing = AppSpacing.lg

/** Localized label for the provider's English lifecycle status; unknown values pass through. */
@Composable
private fun statusLabel(status: String): String =
    when (status.lowercase()) {
        "released" -> stringResource(Res.string.detail_status_released)
        "ended" -> stringResource(Res.string.detail_status_ended)
        "returning series" -> stringResource(Res.string.detail_status_returning)
        "canceled", "cancelled" -> stringResource(Res.string.detail_status_canceled)
        "in production" -> stringResource(Res.string.detail_status_in_production)
        "post production" -> stringResource(Res.string.detail_status_post_production)
        "planned" -> stringResource(Res.string.detail_status_planned)
        "rumored" -> stringResource(Res.string.detail_status_rumored)
        "pilot" -> stringResource(Res.string.detail_status_pilot)
        else -> status
    }

/** Groups a whole US-dollar amount (`63000000` -> `$63,000,000`); the sources report USD. */
private fun formatMoney(amount: Long): String {
    val grouped =
        amount
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
    return '$' + grouped
}

/** Maps a crew member to the shared [MetaPerson] shape, with localized role labels. */
private fun CrewMember.toMetaPerson(
    directorLabel: String,
    writerLabel: String,
): MetaPerson =
    MetaPerson(
        name = name,
        character =
            roles.joinToString(", ") { role ->
                if (role == CrewRole.DIRECTOR) directorLabel else writerLabel
            },
    )
