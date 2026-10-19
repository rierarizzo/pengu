// Translates Nuvio (PostgREST) rows into domain models, and back.
//
// The mapping lives here, in the backend implementation, so the domain models
// stay free of database column names.

package com.misgus.pengu.data.backend

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import com.misgus.pengu.data.boolOrNull
import com.misgus.pengu.data.doubleOrNull
import com.misgus.pengu.data.instantMillisOrNull
import com.misgus.pengu.data.intOrNull
import com.misgus.pengu.data.stringList
import com.misgus.pengu.data.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** Builds a [BackendProfile] from a `profiles` row. */
fun backendProfileFromRow(json: JsonObject): BackendProfile =
    BackendProfile(
        id = json["id"].stringOrNull() ?: "",
        name = json["name"].stringOrNull() ?: "",
        profileId = json["profile_id"].intOrNull(),
        profileIndex = json["profile_index"].intOrNull(),
        avatarId = json["avatar_id"].stringOrNull(),
        avatarUrl = json["avatar_url"].stringOrNull(),
        avatarColorHex = json["avatar_color_hex"].stringOrNull(),
        pinEnabled = json["pin_enabled"].boolOrNull() ?: false,
    )

/** Builds a [LibraryItem] from a `library_items` row. */
fun libraryItemFromRow(json: JsonObject): LibraryItem =
    LibraryItem(
        id = json["id"].stringOrNull() ?: "",
        contentId = json["content_id"].stringOrNull() ?: "",
        contentType = ContentType(json["content_type"].stringOrNull() ?: ""),
        name = json["name"].stringOrNull() ?: "",
        poster = json["poster"].stringOrNull(),
        posterShape = json["poster_shape"].stringOrNull(),
        background = json["background"].stringOrNull(),
        logo = json["logo"].stringOrNull(),
        description = json["description"].stringOrNull(),
        releaseInfo = json["release_info"].stringOrNull(),
        imdbRating = json["imdb_rating"].doubleOrNull(),
        genres = json["genres"].stringList(),
        addonBaseUrl = json["addon_base_url"].stringOrNull(),
        addedAt = json["added_at"].instantMillisOrNull(),
        profileId = json["profile_id"].intOrNull(),
    )

/** Builds a [WatchProgress] from a `watch_progress` row. */
fun watchProgressFromRow(json: JsonObject): WatchProgress =
    WatchProgress(
        id = json["id"].stringOrNull() ?: "",
        contentId = json["content_id"].stringOrNull() ?: "",
        contentType = ContentType(json["content_type"].stringOrNull() ?: ""),
        videoId = json["video_id"].stringOrNull(),
        season = json["season"].intOrNull()?.let(::Season),
        episode = json["episode"].intOrNull()?.let(::Episode),
        position = json["position"].intOrNull()?.milliseconds,
        duration = json["duration"].intOrNull()?.milliseconds,
        lastWatched = json["last_watched"].instantMillisOrNull(),
        progressKey = json["progress_key"].stringOrNull(),
        profileId = json["profile_id"].intOrNull(),
    )

/** Builds an [Addon] from an `addons` row. */
fun addonFromRow(json: JsonObject): Addon =
    Addon(
        id = json["id"].stringOrNull() ?: "",
        url = json["url"].stringOrNull() ?: "",
        name = json["name"].stringOrNull(),
        enabled = json["enabled"].boolOrNull() ?: true,
        sortOrder = json["sort_order"].intOrNull() ?: 0,
        profileId = json["profile_id"].intOrNull(),
    )

/**
 * Serializes an [Addon] back into the row shape [addonFromRow] reads.
 *
 * Used by the local snapshot, which is written in the backend's own column
 * names so one mapper covers both directions.
 */
fun addonToJson(addon: Addon): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    map["id"] = JsonPrimitive(addon.id)
    map["url"] = JsonPrimitive(addon.url)
    addon.name?.let { map["name"] = JsonPrimitive(it) }
    map["enabled"] = JsonPrimitive(addon.enabled)
    map["sort_order"] = JsonPrimitive(addon.sortOrder)
    addon.profileId?.let { map["profile_id"] = JsonPrimitive(it) }
    return JsonObject(map)
}

/** Builds a [WatchedEntry] from a `watched_items` row. */
fun watchedEntryFromRow(json: JsonObject): WatchedEntry =
    WatchedEntry(
        contentId = json["content_id"].stringOrNull() ?: "",
        contentType = ContentType(json["content_type"].stringOrNull() ?: ""),
        season = json["season"].intOrNull()?.let(::Season),
        episode = json["episode"].intOrNull()?.let(::Episode),
    )

/**
 * Serializes a [LibraryItem] into the writable columns of `library_items`.
 *
 * `id` is left out: the backend assigns it. `(profile_id, user_id)` scope the
 * row. `added_at` is always sent because the column defaults to `0`.
 */
fun libraryItemToRow(
    item: LibraryItem,
    profileId: Int? = null,
    userId: String? = null,
): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    userId?.let { map["user_id"] = JsonPrimitive(it) }
    map["content_id"] = JsonPrimitive(item.contentId)
    map["content_type"] = JsonPrimitive(item.contentType.value)
    map["name"] = JsonPrimitive(item.name)
    item.poster?.let { map["poster"] = JsonPrimitive(it) }
    item.posterShape?.let { map["poster_shape"] = JsonPrimitive(it) }
    item.background?.let { map["background"] = JsonPrimitive(it) }
    item.logo?.let { map["logo"] = JsonPrimitive(it) }
    item.description?.let { map["description"] = JsonPrimitive(it) }
    item.releaseInfo?.let { map["release_info"] = JsonPrimitive(it) }
    item.imdbRating?.let { map["imdb_rating"] = JsonPrimitive(it) }
    if (item.genres.isNotEmpty()) map["genres"] = JsonArray(item.genres.map { JsonPrimitive(it) })
    item.addonBaseUrl?.let { map["addon_base_url"] = JsonPrimitive(it) }
    map["added_at"] = JsonPrimitive((item.addedAt ?: Clock.System.now()).toEpochMilliseconds())
    profileId?.let { map["profile_id"] = JsonPrimitive(it) }
    return JsonObject(map)
}

/**
 * Serializes a [WatchProgress] into the fields `sync_push_watch_progress`
 * expects. Times are sent in milliseconds.
 */
fun watchProgressToRow(
    progress: WatchProgress,
    profileId: Int? = null,
): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    map["content_id"] = JsonPrimitive(progress.contentId)
    map["content_type"] = JsonPrimitive(progress.contentType.value)
    progress.videoId?.let { map["video_id"] = JsonPrimitive(it) }
    progress.season?.let { map["season"] = JsonPrimitive(it.number) }
    progress.episode?.let { map["episode"] = JsonPrimitive(it.number) }
    progress.position?.let { map["position"] = JsonPrimitive(it.inWholeMilliseconds) }
    progress.duration?.let { map["duration"] = JsonPrimitive(it.inWholeMilliseconds) }
    map["last_watched"] = JsonPrimitive((progress.lastWatched ?: Clock.System.now()).toEpochMilliseconds())
    progress.progressKey?.let { map["progress_key"] = JsonPrimitive(it) }
    profileId?.let { map["profile_id"] = JsonPrimitive(it) }
    return JsonObject(map)
}
