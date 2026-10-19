package com.misgus.pengu.core.model

/*
 * Merging third-party metadata (TMDB) into what the addons already returned.
 *
 * The merge is deliberately one-directional: it only fills a field the addon
 * left empty, and it never touches an identifier. `MetaDetail.id` and
 * `MetaVideo.id` are what the stream and subtitle addons are queried with, and
 * `MetaPreview.id` is what a poster opens, so replacing them with a TMDB id
 * would break playback rather than improve it. Both the detail screen and the
 * home billboard go through these functions, so the rule cannot drift.
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
 * Fills a [MetaDetail]'s empty fields from [tmdb].
 *
 * A `null` receiver with a TMDB hit yields a detail built from TMDB alone, so a
 * title no addon knows (a TMDB recommendation, for instance) still renders a
 * full page. When both are `null` the result is `null`.
 */
fun MetaDetail?.enrichedWith(tmdb: TmdbTitle?): MetaDetail? {
    if (tmdb == null) return this
    val base = this ?: tmdb.toMetaDetail()
    return base.copy(
        poster = base.poster.nonEmpty() ?: tmdb.poster,
        background = base.background.nonEmpty() ?: tmdb.backdrop,
        logo = base.logo.nonEmpty() ?: tmdb.logo,
        textlessPoster = base.textlessPoster.nonEmpty() ?: tmdb.textlessPoster,
        description = base.description.nonEmpty() ?: tmdb.overview,
        releaseInfo = base.releaseInfo.nonEmpty() ?: tmdb.releaseInfo(),
        runtimeMinutes = base.runtimeMinutes ?: tmdb.runtimeMinutes,
        genres = base.genres.ifEmpty { tmdb.genres },
        cast = base.cast.ifEmpty { tmdb.cast },
        released = base.released.nonEmpty() ?: tmdb.releaseDate,
        country = base.country.nonEmpty() ?: tmdb.country(),
        status = base.status.nonEmpty() ?: tmdb.status,
        originalLanguage = base.originalLanguage.nonEmpty() ?: tmdb.originalLanguage,
        seasonCount = base.seasonCount ?: tmdb.seasonCount,
        episodeCount = base.episodeCount ?: tmdb.episodeCount,
        budget = base.budget ?: tmdb.budget,
        revenue = base.revenue ?: tmdb.revenue,
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
