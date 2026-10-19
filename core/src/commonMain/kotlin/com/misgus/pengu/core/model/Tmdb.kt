package com.misgus.pengu.core.model

/** Which TMDB endpoint a title lives on. */
enum class TmdbMediaType {
    MOVIE,
    SERIES,
}

/**
 * A title as TMDB describes it, with every field optional except its identity.
 *
 * The data layer resolves the external id and maps the payload into this shape;
 * the domain then merges it into a Stremio record, filling only what the addon
 * left empty (see [enrichedWith]). Image fields are already absolute URLs, so
 * the UI renders them like any other artwork and never needs the TMDB client or
 * the API key.
 */
data class TmdbTitle(
    val tmdbId: Int,
    val mediaType: TmdbMediaType,
    val title: String,
    val poster: String? = null,
    /** Language-neutral (textless) poster, the best TMDB one has to offer. */
    val textlessPoster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val overview: String? = null,
    /** ISO date (`2024-03-01`), for a movie's release or a series' first air date. */
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    /** Lifecycle status as TMDB spells it (`Released`, `Ended`, ...). */
    val status: String? = null,
    /** Original language, as a display name when TMDB localizes it. */
    val originalLanguage: String? = null,
    /** Season count, for a series. */
    val seasonCount: Int? = null,
    /** Episode count, for a series. */
    val episodeCount: Int? = null,
    /** Production budget in USD, when TMDB reports one above zero. */
    val budget: Long? = null,
    /** Worldwide revenue in USD, when TMDB reports one above zero. */
    val revenue: Long? = null,
    val cast: List<MetaPerson> = emptyList(),
    /** IMDb id, when TMDB knows one. Used to enrich, never to replace the addon id. */
    val imdbId: String? = null,
)
