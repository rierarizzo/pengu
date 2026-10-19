package com.misgus.pengu.core.model

/*
 * Merging third-party metadata (TMDB) into what the addons already returned.
 *
 * Two rules, one per screen, and both never touch an identifier: `MetaDetail.id`
 * and `MetaVideo.id` are what the stream and subtitle addons are queried with,
 * and `MetaPreview.id` is what a poster opens, so replacing them with a TMDB id
 * would break playback rather than improve it.
 *
 * - Home fills gaps: the billboard is built from catalog previews, which are
 *   already the addon's own artwork and copy, so TMDB only adds what is missing
 *   and the billboard paints immediately.
 * - The detail page gives TMDB priority: there, one consistent source is worth
 *   more than the fastest first paint, so nothing is shown from the addon and
 *   replaced a moment later.
 */

/** Fills a [MetaPreview]'s empty fields from [tmdb]; returns it untouched when there is nothing to fill. */
fun MetaPreview.enrichedWith(tmdb: TmdbTitle?): MetaPreview {
    if (tmdb == null) return this
    return copy(
        poster = poster.nonEmpty() ?: tmdb.poster,
        background = background.nonEmpty() ?: tmdb.backdrop,
        logo = logo.nonEmpty() ?: tmdb.logo,
        textlessPoster = textlessPoster.nonEmpty() ?: tmdb.textlessPoster,
        description = description.nonEmpty() ?: tmdb.overview,
        releaseInfo = releaseInfo.nonEmpty() ?: tmdb.releaseInfo(),
        genres = genres.ifEmpty { tmdb.genres },
    )
}

/**
 * Merges [tmdb] over a detail record, TMDB first.
 *
 * The detail page uses this rule, not [enrichedWith]'s: when TMDB knows the
 * title, its artwork and copy are what the page shows, so no image is painted
 * from the addon and swapped a moment later. The addon keeps what is not
 * presentation — [MetaDetail.id] and [MetaDetail.videos], which is what the
 * stream and subtitle addons are queried with — and the IMDb score, which this
 * TMDB payload does not carry.
 *
 * A `null` receiver with a TMDB hit yields the TMDB record alone, so a title no
 * addon knows still renders a full page. When both are `null` the result is
 * `null`.
 */
fun MetaDetail?.withTmdbPriority(tmdb: TmdbTitle?): MetaDetail? {
    if (tmdb == null) return this
    val base = this ?: tmdb.toMetaDetail()
    return base.copy(
        poster = tmdb.poster.nonEmpty() ?: base.poster,
        background = tmdb.backdrop.nonEmpty() ?: base.background,
        logo = tmdb.logo.nonEmpty() ?: base.logo,
        textlessPoster = tmdb.textlessPoster.nonEmpty() ?: base.textlessPoster,
        description = tmdb.overview.nonEmpty() ?: base.description,
        releaseInfo = tmdb.releaseInfo() ?: base.releaseInfo,
        runtimeMinutes = tmdb.runtimeMinutes ?: base.runtimeMinutes,
        genres = tmdb.genres.ifEmpty { base.genres },
        cast = tmdb.cast.ifEmpty { base.cast },
        released = tmdb.releaseDate.nonEmpty() ?: base.released,
        country = tmdb.country() ?: base.country,
        status = tmdb.status.nonEmpty() ?: base.status,
        originalLanguage = tmdb.originalLanguage.nonEmpty() ?: base.originalLanguage,
        seasonCount = tmdb.seasonCount ?: base.seasonCount,
        episodeCount = tmdb.episodeCount ?: base.episodeCount,
        budget = tmdb.budget ?: base.budget,
        revenue = tmdb.revenue ?: base.revenue,
    )
}

/** A TMDB title as a standalone detail record, for a title no addon returned. */
fun TmdbTitle.toMetaDetail(): MetaDetail =
    MetaDetail(
        id = tmdbContentId(tmdbId),
        type = contentType(),
        name = title,
        poster = poster,
        background = backdrop,
        logo = logo,
        textlessPoster = textlessPoster,
        description = overview,
        releaseInfo = releaseInfo(),
        runtimeMinutes = runtimeMinutes,
        genres = genres,
        cast = cast,
        released = releaseDate,
        country = country(),
        status = status,
        originalLanguage = originalLanguage,
        seasonCount = seasonCount,
        episodeCount = episodeCount,
        budget = budget,
        revenue = revenue,
    )

/**
 * A TMDB title as a catalog preview, used by the "You may also like" row.
 *
 * The id is prefixed (`tmdb:123`) so the detail screen can resolve it back to
 * TMDB without an extra lookup, and so it is never confused with an addon's id.
 */
fun TmdbTitle.toMetaPreview(): MetaPreview =
    MetaPreview(
        id = tmdbContentId(tmdbId),
        type = contentType(),
        name = title,
        poster = poster,
        background = backdrop,
        logo = logo,
        textlessPoster = textlessPoster,
        description = overview,
        releaseInfo = releaseInfo(),
        genres = genres,
    )

/** Stable `tmdb:<id>` content id, the shape [com.misgus.pengu.core.repository.TmdbRepository] parses back. */
fun tmdbContentId(tmdbId: Int): String = "tmdb:$tmdbId"

private fun TmdbTitle.contentType(): ContentType =
    when (mediaType) {
        TmdbMediaType.MOVIE -> ContentTypes.MOVIE
        TmdbMediaType.SERIES -> ContentTypes.SERIES
    }

private fun TmdbTitle.releaseInfo(): String? = releaseDate?.let(::releaseYear)?.toString()

private fun TmdbTitle.country(): String? = countries.joinToString(", ").takeIf { it.isNotEmpty() }

/** A non-blank value, so an empty addon string counts as missing. */
private fun String?.nonEmpty(): String? = this?.takeIf { it.isNotBlank() }
