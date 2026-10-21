package com.misgus.pengu.data.tmdb

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.TmdbEpisode
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.core.model.releaseYear
import com.misgus.pengu.core.model.tmdbContentId
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.doubleOrNull
import com.misgus.pengu.data.intOrNull
import com.misgus.pengu.data.longOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.stringList
import com.misgus.pengu.data.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * TMDB payload -> domain. Kept apart from the client so the parsing is testable
 * with a literal JSON body, the same way the Stremio parsers are.
 */

/** Builds an absolute TMDB image URL, or `null` when the payload has no path. */
internal fun tmdbImage(
    path: String?,
    size: String,
): String? =
    path
        ?.takeIf { it.isNotBlank() }
        ?.let { "$IMAGE_BASE/$size$it" }

/** Maps a `/{movie|tv}/{id}` payload (with images, external ids and credits). */
internal fun tmdbTitleFromJson(
    json: JsonObject,
    mediaType: TmdbMediaType,
    language: String,
): TmdbTitle {
    val images = json["images"].objOrNull()
    // A series answers the aggregate form of the crew (one entry per person, with
    // every job they held); a movie answers the plain one. Both shapes are read
    // by [crewNames], so whichever arrives is enough.
    val crew = json["aggregate_credits"].objOrNull()?.takeIf { it["crew"].arrOrNull()?.isNotEmpty() == true } ?: json["credits"].objOrNull()
    return TmdbTitle(
        tmdbId = json["id"].intOrNull() ?: 0,
        mediaType = mediaType,
        title = (json["title"] ?: json["name"]).stringOrNull().orEmpty(),
        poster = tmdbImage(json["poster_path"].stringOrNull(), "w500"),
        textlessPoster = bestTextlessPoster(images?.get("posters").arrOrNull()),
        backdrop = tmdbImage(json["backdrop_path"].stringOrNull(), "w1280"),
        logo = bestLogo(images?.get("logos").arrOrNull(), language.substringBefore('-').lowercase()),
        overview = json["overview"].stringOrNull()?.takeIf { it.isNotBlank() },
        releaseDate = (json["release_date"] ?: json["first_air_date"]).stringOrNull()?.takeIf { it.isNotBlank() },
        runtimeMinutes = json["runtime"].intOrNull() ?: json["episode_run_time"].arrOrNull()?.firstOrNull()?.intOrNull(),
        genres = names(json["genres"]),
        countries = countries(json),
        status = json["status"].stringOrNull()?.takeIf { it.isNotBlank() },
        originalLanguage = originalLanguageName(json),
        seasonCount = json["number_of_seasons"].intOrNull(),
        episodeCount = json["number_of_episodes"].intOrNull(),
        budget = json["budget"].longOrNull()?.takeIf { it > 0 },
        revenue = json["revenue"].longOrNull()?.takeIf { it > 0 },
        cast = cast(json["credits"].objOrNull()?.get("cast")),
        director = crewNames(crew, DIRECTOR_JOBS),
        writer = crewNames(crew, WRITER_JOBS),
        voteAverage = json["vote_average"].doubleOrNull()?.takeIf { it > 0 },
        imdbId =
            json["external_ids"]
                .objOrNull()
                ?.get("imdb_id")
                .stringOrNull()
                ?.takeIf { it.isNotBlank() },
    )
}

/**
 * Maps a `/tv/{id}/season/{n}` payload's `episodes` array.
 *
 * An episode without a number cannot be matched to an addon video, so it is
 * dropped; every other field is optional and simply stays `null` when TMDB has
 * no value for it.
 */
internal fun tmdbEpisodesFromJson(json: JsonObject): List<TmdbEpisode> =
    json["episodes"]
        .arrOrNull()
        ?.mapNotNull { it.objOrNull() }
        ?.mapNotNull { episode ->
            val number = episode["episode_number"].intOrNull() ?: return@mapNotNull null
            TmdbEpisode(
                seasonNumber = episode["season_number"].intOrNull() ?: 0,
                episodeNumber = number,
                name = episode["name"].stringOrNull()?.takeIf { it.isNotBlank() },
                overview = episode["overview"].stringOrNull()?.takeIf { it.isNotBlank() },
                runtimeMinutes = episode["runtime"].intOrNull()?.takeIf { it > 0 },
                // TMDB scores an unrated episode `0.0`, which is not a score.
                rating = episode["vote_average"].doubleOrNull()?.takeIf { it > 0 },
                still = tmdbImage(episode["still_path"].stringOrNull(), "w780"),
                airDate = episode["air_date"].stringOrNull()?.takeIf { it.isNotBlank() },
            )
        } ?: emptyList()

/**
 * Maps one item of a recommendations / similar list.
 *
 * Those lists carry only the fields a card needs; the full record (logo, cast,
 * runtime) is fetched when the title is opened, from the same `tmdb:` id.
 */
internal fun tmdbPreviewFromJson(
    json: JsonObject,
    type: ContentType,
): MetaPreview? {
    val id = json["id"].intOrNull() ?: return null
    val title = (json["title"] ?: json["name"]).stringOrNull().orEmpty()
    if (title.isEmpty()) return null
    return MetaPreview(
        id = tmdbContentId(id),
        type = type,
        name = title,
        poster = tmdbImage(json["poster_path"].stringOrNull(), "w500"),
        background = tmdbImage(json["backdrop_path"].stringOrNull(), "w1280"),
        description = json["overview"].stringOrNull()?.takeIf { it.isNotBlank() },
        releaseInfo = (json["release_date"] ?: json["first_air_date"]).stringOrNull()?.let(::releaseYear)?.toString(),
    )
}

/**
 * The highest-resolution language-neutral poster TMDB has.
 *
 * TMDB marks a textless poster with a `null` `iso_639_1`; those are the ones a
 * phone can crop into a cover without cutting a title in half. The phone draws
 * the poster whole, so a phone wants the sharpest candidate and the pick is by
 * pixel count, with votes only breaking a tie. When TMDB has none, the caller
 * falls back to the wide backdrop.
 */
private fun bestTextlessPoster(posters: JsonArray?): String? =
    posters
        ?.mapNotNull { it.objOrNull() }
        ?.filter { it["iso_639_1"].stringOrNull() == null }
        ?.maxWithOrNull(
            compareBy(
                { (it["width"].intOrNull() ?: 0) * (it["height"].intOrNull() ?: 0) },
                { it["width"].intOrNull() ?: 0 },
                { it["vote_average"].doubleOrNull() ?: 0.0 },
            ),
        )?.get("file_path")
        ?.stringOrNull()
        ?.let { tmdbImage(it, "w500") }

/**
 * The best logo for [iso]: the requested language, then English, then the
 * language-neutral ones, then whatever exists.
 */
private fun bestLogo(
    logos: JsonArray?,
    iso: String,
): String? {
    val candidates = logos?.mapNotNull { it.objOrNull() } ?: return null
    if (candidates.isEmpty()) return null
    val byLanguage = { tag: String? -> candidates.filter { it["iso_639_1"].stringOrNull() == tag } }
    val pool =
        byLanguage(iso)
            .ifEmpty { byLanguage("en") }
            .ifEmpty { byLanguage(null) }
            .ifEmpty { candidates }
    return pool
        .maxWithOrNull(
            compareBy(
                { it["vote_average"].doubleOrNull() ?: 0.0 },
                { it["width"].intOrNull() ?: 0 },
            ),
        )?.get("file_path")
        ?.stringOrNull()
        ?.let { tmdbImage(it, "w500") }
}

/** Names of an array of `{ id, name }` objects (TMDB genres). */
private fun names(element: JsonElement?): List<String> =
    element
        .arrOrNull()
        ?.mapNotNull { it.objOrNull()?.get("name").stringOrNull() }
        ?: emptyList()

/** Production country names, falling back to the two-letter `origin_country` codes. */
private fun countries(json: JsonObject): List<String> = names(json["production_countries"]).ifEmpty { json["origin_country"].stringList() }

/**
 * The original language as a readable name.
 *
 * TMDB returns `spoken_languages` already localized for the requested language,
 * so the matching entry names the original language in the UI's language; the
 * two-letter code is the fallback when TMDB has no entry for it.
 */
private fun originalLanguageName(json: JsonObject): String? {
    val code = json["original_language"].stringOrNull()?.takeIf { it.isNotBlank() }
    val localized =
        json["spoken_languages"]
            .arrOrNull()
            ?.mapNotNull { it.objOrNull() }
            ?.firstOrNull { it["iso_639_1"].stringOrNull() == code }
            ?.get("name")
            ?.stringOrNull()
            ?.takeIf { it.isNotBlank() }
    return localized ?: code?.uppercase()
}

/** Cast members with a portrait, capped so a wide credit list does not bloat the model. */
private fun cast(element: JsonElement?): List<MetaPerson> =
    element
        .arrOrNull()
        ?.mapNotNull { it.objOrNull() }
        ?.map { person ->
            MetaPerson(
                name = person["name"].stringOrNull().orEmpty(),
                character = person["character"].stringOrNull()?.takeIf { it.isNotBlank() },
                photo = tmdbImage(person["profile_path"].stringOrNull(), "w185"),
            )
        }?.filter { it.name.isNotEmpty() }
        ?.take(MAX_CAST)
        ?: emptyList()

private const val IMAGE_BASE = "https://image.tmdb.org/t/p"
private const val MAX_CAST = 20

/** Crew roles TMDB spells as `job` (movie) or `jobs[].job` (series), lowercased. */
private val DIRECTOR_JOBS = setOf("director")
private val WRITER_JOBS = setOf("writer", "screenplay", "story", "teleplay")

/**
 * Names credited with any of [jobs] in a `credits` or `aggregate_credits` crew list.
 *
 * Capped: a series credits every episode director, and the row is a glance at the
 * main names, not a full crew list.
 */
private fun crewNames(
    credits: JsonObject?,
    jobs: Set<String>,
): List<String> =
    credits
        ?.get("crew")
        .arrOrNull()
        ?.mapNotNull { it.objOrNull() }
        ?.mapNotNull { member ->
            val held =
                member["jobs"]
                    .arrOrNull()
                    ?.mapNotNull { it.objOrNull()?.get("job").stringOrNull() }
                    ?: listOfNotNull(member["job"].stringOrNull())
            member["name"].stringOrNull()?.takeIf { name -> name.isNotEmpty() && held.any { it.lowercase() in jobs } }
        }?.distinct()
        ?.take(MAX_CREW)
        ?: emptyList()

private const val MAX_CREW = 6
