package com.misgus.pengu.core.repository

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.TmdbEpisode
import com.misgus.pengu.core.model.TmdbTitle

/**
 * A title to look up in TMDB.
 *
 * [contentId] is the Stremio id the title was opened with. It may be an IMDb id
 * (`tt0133093`), a TMDB id (`tmdb:603`), a TVDB id, or an id only the addon
 * understands; the implementation turns it into a TMDB id, [name] and [year]
 * being the fallbacks when it cannot.
 */
data class TmdbLookup(
    val type: ContentType,
    val contentId: String,
    val name: String,
    val year: Int? = null,
)

/** Outcome of checking an API key from the settings screen. */
enum class TmdbVerifyResult {
    VALID,
    INVALID_KEY,
    UNREACHABLE,
}

/**
 * Optional TMDB enrichment: artwork, text and cast the addons did not provide.
 *
 * This is a *presentation* provider, not a metadata source: it is read by the
 * detail screen and the home billboard, never by the stream, subtitle or watched
 * paths, and every lookup degrades to `null` when there is no API key or no
 * confident match. The addon metadata stays the source of truth.
 */
interface TmdbRepository {
    /** Whether an API key is set. Callers can skip the lookup entirely when it is not. */
    val isConfigured: Boolean

    /** TMDB's take on [request], or `null` when unconfigured, unmatched or unreachable. */
    suspend fun lookup(request: TmdbLookup): TmdbTitle?

    /**
     * Titles related to [request], the "You may also like" row.
     *
     * Built from TMDB's recommendations (and its "similar" list as a fallback),
     * so it does not depend on an addon declaring a suitable catalog.
     */
    suspend fun recommendations(request: TmdbLookup): List<MetaPreview>

    /**
     * The copy TMDB has for season [seasonNumber] of [request]: synopsis, score,
     * runtime and still for each episode, which the addon's videos do not carry.
     *
     * Empty when unconfigured, unmatched, unreachable, or when the title is not
     * a series.
     */
    suspend fun season(
        request: TmdbLookup,
        seasonNumber: Int,
    ): List<TmdbEpisode>

    /** Checks [apiKey] against TMDB, for the settings screen's "Test" action. */
    suspend fun verify(apiKey: String): TmdbVerifyResult
}

/** Repository used when TMDB is not wired (tests, or a build without it). */
class NoTmdbRepository : TmdbRepository {
    override val isConfigured: Boolean = false

    override suspend fun lookup(request: TmdbLookup): TmdbTitle? = null

    override suspend fun recommendations(request: TmdbLookup): List<MetaPreview> = emptyList()

    override suspend fun season(
        request: TmdbLookup,
        seasonNumber: Int,
    ): List<TmdbEpisode> = emptyList()

    override suspend fun verify(apiKey: String): TmdbVerifyResult = TmdbVerifyResult.UNREACHABLE
}
