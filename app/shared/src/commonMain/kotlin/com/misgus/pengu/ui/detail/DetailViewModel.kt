package com.misgus.pengu.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.util.runCatchingCancellable
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
    /**
     * Whether the load has landed.
     *
     * Nothing on the page paints before it, so no section shows the library
     * item's copy and then the metadata's a moment later.
     */
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
    val isSeries: Boolean = false,
    val name: String = "",
    /** The id streams are requested with: [LibraryItem.contentId]. */
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
    /** Runtime in minutes, when the addon sends a number; the UI formats it with the locale's strings. */
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
 * The library item is the immediate fallback for the fields the addon may not
 * fill, and [load] publishes the page once: a section is never drawn from the
 * library and then redrawn from the addon.
 */
class DetailViewModel(
    private val metadata: MetadataRepository,
    val item: LibraryItem,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        if (_state.value.isLoading || (_state.value.hasLoaded && !force)) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = runCatchingCancellable { metadata.detail(item.contentType, item.contentId) }
            val meta = result.getOrNull()
            _state.value = merge(meta, error = result.exceptionOrNull())
            loadSimilar(meta)
        }
    }

    /** Fills [DetailUiState.similar] from the addon's "similar" catalog. */
    private suspend fun loadSimilar(meta: MetaDetail?) {
        val similar =
            runCatchingCancellable {
                metadata.similar(
                    type = item.contentType,
                    id = item.contentId,
                    genre = meta?.genres?.firstOrNull(),
                )
            }.getOrDefault(emptyList())
        _state.update { it.copy(similar = similar) }
    }

    private fun merge(
        meta: MetaDetail?,
        error: Throwable?,
    ): DetailUiState {
        val cast = meta?.cast ?: emptyList()
        val crew = buildCrew(meta)
        val year = releaseYear(meta?.releaseInfo ?: item.releaseInfo)
        return DetailUiState(
            isLoading = false,
            hasLoaded = true,
            error = error,
            isSeries = item.contentType == ContentTypes.SERIES,
            contentId = item.contentId,
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

    private fun buildCrew(meta: MetaDetail?): List<CrewMember> {
        if (meta == null) return emptyList()
        val roles = linkedMapOf<String, MutableSet<CrewRole>>()
        meta.director.forEach { roles.getOrPut(it) { mutableSetOf() }.add(CrewRole.DIRECTOR) }
        meta.writer.forEach { roles.getOrPut(it) { mutableSetOf() }.add(CrewRole.WRITER) }
        return roles.map { (name, values) -> CrewMember(name = name, roles = values) }
    }

    private fun nonEmpty(value: String?): String? = value?.takeIf { it.isNotEmpty() }
}
