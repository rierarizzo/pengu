package com.misgus.pengu.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.core.model.enrichedWith
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.TmdbLookup
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A role a crew member is credited with. */
enum class CrewRole {
    DIRECTOR,
    WRITER,
}

/** A crew member and the roles they are credited with. */
data class CrewMember(
    val name: String,
    val roles: Set<CrewRole>,
)

/** Merged, render-ready state of the detail screen. */
data class DetailUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
    val isSeries: Boolean = false,
    val name: String = "",
    /**
     * The id streams are requested with.
     *
     * Normally the library item's id, but a title opened from a TMDB
     * recommendation carries a `tmdb:` id the addons may not know, so the
     * resolved IMDb id takes over once TMDB supplies it.
     */
    val contentId: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    /** Language-neutral poster, preferred by the phone billboard. */
    val textlessPoster: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: Double? = null,
    val genres: List<String> = emptyList(),
    /** Runtime as the addon spells it (`1 h 37 min`). */
    val runtime: String? = null,
    /** Runtime in minutes, from TMDB; the UI formats it with the locale's strings. */
    val runtimeMinutes: Int? = null,
    val released: String? = null,
    val country: String? = null,
    val status: String? = null,
    val originalLanguage: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val cast: List<MetaPerson> = emptyList(),
    val crew: List<CrewMember> = emptyList(),
    val episodes: List<MetaVideo> = emptyList(),
    val similar: List<MetaPreview> = emptyList(),
    val year: Int? = null,
)

/**
 * Loads the metadata for one [LibraryItem] and merges it with what the library
 * already knows.
 *
 * The library item is the immediate fallback, so the hero renders while the
 * addon response is in flight. The addon answer is published as soon as it
 * lands, and TMDB fills its gaps right after, so a slow enrichment never delays
 * the page.
 */
class DetailViewModel(
    private val metadata: MetadataRepository,
    private val tmdb: TmdbRepository,
    val item: LibraryItem,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        if (_state.value.isLoading || (_state.value.hasLoaded && !force)) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val request = tmdbRequest()
            // The addon and TMDB are independent, so they run together; the
            // addon answer is awaited first so the page paints as it did before
            // the enrichment existed.
            val (addonMeta, tmdbTitle, error) =
                coroutineScope {
                    val metaDeferred = async { runCatchingCancellable { metadata.detail(item.contentType, item.contentId) } }
                    val tmdbDeferred =
                        if (tmdb.isConfigured) {
                            async { runCatchingCancellable { tmdb.lookup(request) } }
                        } else {
                            null
                        }
                    val metaResult = metaDeferred.await()
                    val addonMeta = metaResult.getOrNull()
                    _state.value =
                        merge(
                            addonMeta,
                            tmdb = null,
                            contentId = item.contentId,
                            error = metaResult.exceptionOrNull(),
                        )
                    Triple(addonMeta, tmdbDeferred?.await()?.getOrNull(), metaResult.exceptionOrNull())
                }
            // A TMDB-only hit (a recommendation) carries an id the addons may
            // not resolve, so the metadata is retried through the IMDb id TMDB
            // knows; that id then drives the streams, which every addon accepts.
            val resolved = retryViaImdb(addonMeta, tmdbTitle)
            val contentId = streamContentId(addonMeta, resolved, tmdbTitle)
            _state.value = merge(resolved, tmdb = tmdbTitle, contentId = contentId, error = error)
            loadSimilar(resolved, tmdbTitle, request, contentId)
        }
    }

    /**
     * Retries the addon metadata with TMDB's IMDb id when the original content
     * id did not resolve (a `tmdb:` recommendation, an id only one addon knows).
     */
    private suspend fun retryViaImdb(
        addonMeta: MetaDetail?,
        tmdbTitle: TmdbTitle?,
    ): MetaDetail? {
        if (addonMeta != null) return addonMeta
        val imdbId = tmdbTitle?.imdbId ?: return null
        if (imdbId == item.contentId) return null
        return runCatchingCancellable { metadata.detail(item.contentType, imdbId) }.getOrNull()
    }

    /**
     * The id streams are requested with: the original id when the addons already
     * answered it, otherwise TMDB's IMDb id when there is one.
     */
    private fun streamContentId(
        addonMeta: MetaDetail?,
        resolved: MetaDetail?,
        tmdbTitle: TmdbTitle?,
    ): String {
        val imdbId = tmdbTitle?.imdbId
        return when {
            resolved == null -> item.contentId
            addonMeta != null && addonMeta.id == item.contentId -> item.contentId
            imdbId != null -> imdbId
            else -> item.contentId
        }
    }

    /**
     * Fills [DetailUiState.similar] with TMDB recommendations, falling back to
     * the addon's "similar" catalog when there is no key or TMDB has nothing.
     */
    private suspend fun loadSimilar(
        addonMeta: MetaDetail?,
        tmdbTitle: TmdbTitle?,
        request: TmdbLookup,
        contentId: String,
    ) {
        val genre = tmdbTitle?.genres?.firstOrNull() ?: addonMeta?.genres?.firstOrNull()
        val recommended =
            if (tmdb.isConfigured) {
                runCatchingCancellable { tmdb.recommendations(request) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        if (recommended.isNotEmpty()) {
            _state.update { it.copy(similar = recommended) }
            return
        }
        val similar =
            runCatchingCancellable {
                metadata.similar(
                    type = item.contentType,
                    id = contentId,
                    genre = genre,
                )
            }.getOrDefault(emptyList())
        _state.update { it.copy(similar = similar) }
    }

    private fun merge(
        addonMeta: MetaDetail?,
        tmdb: TmdbTitle?,
        contentId: String,
        error: Throwable?,
    ): DetailUiState {
        val meta = addonMeta.enrichedWith(tmdb)
        val cast = meta?.cast ?: emptyList()
        val crew = buildCrew(meta)
        val year = releaseYear(meta?.releaseInfo ?: item.releaseInfo)
        return DetailUiState(
            isLoading = false,
            hasLoaded = true,
            error = error,
            isSeries = item.contentType == ContentTypes.SERIES,
            contentId = contentId,
            name = nonEmpty(meta?.name) ?: item.name,
            poster = nonEmpty(meta?.poster) ?: item.poster,
            background = nonEmpty(meta?.background) ?: item.background,
            logo = nonEmpty(meta?.logo) ?: item.logo,
            textlessPoster = nonEmpty(meta?.textlessPoster) ?: item.textlessPoster,
            description = nonEmpty(meta?.description) ?: item.description,
            releaseInfo = nonEmpty(meta?.releaseInfo) ?: item.releaseInfo,
            imdbRating = meta?.imdbRating ?: item.imdbRating,
            genres = meta?.genres?.takeIf { it.isNotEmpty() } ?: item.genres,
            runtime = nonEmpty(meta?.runtime),
            runtimeMinutes = meta?.runtimeMinutes,
            released = nonEmpty(meta?.released),
            country = nonEmpty(meta?.country),
            status = nonEmpty(meta?.status),
            originalLanguage = nonEmpty(meta?.originalLanguage),
            seasonCount = meta?.seasonCount,
            episodeCount = meta?.episodeCount,
            budget = meta?.budget,
            revenue = meta?.revenue,
            cast = cast,
            crew = crew,
            episodes = meta?.videos?.filter { it.season != null } ?: emptyList(),
            similar = _state.value.similar,
            year = year,
        )
    }

    private fun initialState(): DetailUiState {
        val year = releaseYear(item.releaseInfo)
        return DetailUiState(
            isSeries = item.contentType == ContentTypes.SERIES,
            contentId = item.contentId,
            name = item.name,
            poster = item.poster,
            background = item.background,
            logo = item.logo,
            textlessPoster = item.textlessPoster,
            description = item.description,
            releaseInfo = item.releaseInfo,
            imdbRating = item.imdbRating,
            genres = item.genres,
            year = year,
        )
    }

    private fun tmdbRequest(): TmdbLookup =
        TmdbLookup(
            type = item.contentType,
            contentId = item.contentId,
            name = item.name,
            year = item.year,
        )

    private fun buildCrew(meta: MetaDetail?): List<CrewMember> {
        if (meta == null) return emptyList()
        val roles = linkedMapOf<String, MutableSet<CrewRole>>()
        meta.director.forEach { roles.getOrPut(it) { mutableSetOf() }.add(CrewRole.DIRECTOR) }
        meta.writer.forEach { roles.getOrPut(it) { mutableSetOf() }.add(CrewRole.WRITER) }
        return roles.map { (name, values) -> CrewMember(name = name, roles = values) }
    }

    private fun nonEmpty(value: String?): String? = value?.takeIf { it.isNotEmpty() }
}
