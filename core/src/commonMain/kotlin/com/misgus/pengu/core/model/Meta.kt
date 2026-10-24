package com.misgus.pengu.core.model

/** A catalog item, called a "meta preview" in Stremio terms. */
data class MetaPreview(
    val id: String,
    val type: ContentType,
    val name: String,
    val poster: String? = null,
    /** `poster` or `landscape`. */
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    /**
     * Language-neutral (textless) poster, when the provider has one.
     *
     * The mobile billboard prefers it over the wide backdrop, which a phone
     * crops too aggressively: a textless poster is portrait and carries no
     * baked-in title, so the copy sits over the artwork instead of a crop of it.
     */
    val textlessPoster: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: Double? = null,
    val genres: List<String> = emptyList(),
)

/**
 * Whether the artwork is wide (16:9) instead of a portrait poster.
 *
 * The protocol's `posterShape` is `poster`, `square` or `landscape`; only the
 * last one changes the card, because the addon is saying its image is a banner
 * (a live event, a channel) and drawing it in a 2:3 cell crops it.
 */
val MetaPreview.isLandscape: Boolean
    get() = posterShape.equals("landscape", ignoreCase = true)

/**
 * A full metadata record, called a "meta detail" in Stremio terms.
 *
 * Credits are merged from `app_extras`, the top-level (deprecated) fields and
 * `links` in the data layer.
 */
data class MetaDetail(
    val id: String,
    val type: ContentType,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    /** Language-neutral (textless) poster, when the provider has one. */
    val textlessPoster: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    /** IMDb score, when the addons report one. */
    val imdbRating: Double? = null,
    /** Runtime as the metadata source spells it (Stremio's `1 h 37 min`). */
    val runtime: String? = null,
    /**
     * Runtime in minutes, when the source gives a number instead of a string.
     *
     * Kept apart from [runtime] because the formatted label is language
     * dependent and is resolved in the UI, where the string resources live.
     */
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    /** Episodes for series, or a single entry for movies. */
    val videos: List<MetaVideo> = emptyList(),
    /** Actors, with the character they play. */
    val cast: List<MetaPerson> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val released: String? = null,
    val country: String? = null,
    /** Lifecycle status (`Released`, `Ended`, ...), when the source reports it. */
    val status: String? = null,
    /** Original language, as a display name. */
    val originalLanguage: String? = null,
    /** Number of seasons, for a series. */
    val seasonCount: Int? = null,
    /** Number of episodes, for a series. */
    val episodeCount: Int? = null,
    /** Production budget in USD, when reported above zero. */
    val budget: Long? = null,
    /** Worldwide revenue in USD, when reported above zero. */
    val revenue: Long? = null,
)

/** One video of a [MetaDetail] (an episode, or the movie itself). */
data class MetaVideo(
    val id: String,
    val title: String? = null,
    val season: Season? = null,
    val episode: Episode? = null,
    val number: Int? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    /**
     * Synopsis, on the rare addon that sends one.
     *
     * The Stremio video object does not define it, so it is off-schema; the
     * detail page uses it when it is there.
     */
    val description: String? = null,
    val runtimeMinutes: Int? = null,
    /** Audience score out of 10, when the addon sends one. */
    val rating: Double? = null,
)

/** One person of a [MetaDetail]'s cast or crew. */
data class MetaPerson(
    val name: String,
    /** Character played (cast) or role held (crew). */
    val character: String? = null,
    /** Portrait URL. */
    val photo: String? = null,
)
