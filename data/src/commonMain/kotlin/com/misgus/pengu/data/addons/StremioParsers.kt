package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.AddonCatalog
import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.model.AddonResource
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPerson
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.Stream
import com.misgus.pengu.core.model.StreamBehaviorHints
import com.misgus.pengu.core.model.Subtitle
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.boolOrNull
import com.misgus.pengu.data.doubleOrNull
import com.misgus.pengu.data.intOrNull
import com.misgus.pengu.data.longOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.stringList
import com.misgus.pengu.data.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun addonManifestFromJson(json: JsonObject): AddonManifest =
    AddonManifest(
        id = json["id"].stringOrNull() ?: "",
        name = json["name"].stringOrNull() ?: "",
        version = json["version"].stringOrNull() ?: "",
        description = json["description"].stringOrNull(),
        logo = json["logo"].stringOrNull(),
        background = json["background"].stringOrNull(),
        contactEmail = json["contactEmail"].stringOrNull(),
        types = json["types"].stringList(),
        idPrefixes = json["idPrefixes"].stringList(),
        resources = json["resources"].arrOrNull()?.map(::addonResourceFromJson) ?: emptyList(),
        catalogs =
            json["catalogs"]
                .arrOrNull()
                ?.mapNotNull { it.objOrNull()?.let(::addonCatalogFromJson) }
                ?: emptyList(),
    )

internal fun addonResourceFromJson(element: JsonElement?): AddonResource {
    (element as? JsonPrimitive)?.takeIf { it.isString }?.let {
        return AddonResource(name = it.content)
    }
    val json = element.objOrNull() ?: return AddonResource(name = "")
    return AddonResource(
        name = json["name"].stringOrNull() ?: "",
        types = json["types"].stringList(),
        idPrefixes = if (json["idPrefixes"] == null) null else json["idPrefixes"].stringList(),
    )
}

internal fun addonCatalogFromJson(json: JsonObject): AddonCatalog =
    AddonCatalog(
        type = json["type"].stringOrNull() ?: "",
        id = json["id"].stringOrNull() ?: "",
        name = json["name"].stringOrNull() ?: "",
        extra =
            json["extra"]
                .arrOrNull()
                ?.mapNotNull { it.objOrNull()?.let(::addonCatalogExtraFromJson) }
                ?: emptyList(),
        genres = json["genres"].stringList(),
    )

internal fun addonCatalogExtraFromJson(json: JsonObject): AddonCatalogExtra =
    AddonCatalogExtra(
        name = json["name"].stringOrNull() ?: "",
        isRequired = json["isRequired"].boolOrNull() ?: false,
        options = json["options"].stringList(),
    )

internal fun metaPreviewFromJson(json: JsonObject): MetaPreview =
    MetaPreview(
        id = json["id"].stringOrNull() ?: "",
        type = ContentType(json["type"].stringOrNull() ?: ""),
        name = json["name"].stringOrNull() ?: "",
        poster = json["poster"].stringOrNull(),
        posterShape = json["posterShape"].stringOrNull(),
        background = json["background"].stringOrNull(),
        logo = json["logo"].stringOrNull(),
        description = json["description"].stringOrNull(),
        releaseInfo = json["releaseInfo"].stringOrNull(),
        imdbRating = json["imdbRating"].doubleOrNull(),
        genres = json["genres"].stringList(),
    )

internal fun metaDetailFromJson(json: JsonObject): MetaDetail {
    // Credits come from three sources, in priority order:
    // 1. `app_extras` — the rich, non-standard extension AIOMetadata/Nuvio use.
    // 2. top-level `cast`/`director`/`writer` — the (deprecated) protocol fields.
    // 3. `links` — the protocol's current form.
    val appExtras = json["app_extras"].objOrNull()
    val links = json["links"]
    return MetaDetail(
        id = json["id"].stringOrNull() ?: "",
        type = ContentType(json["type"].stringOrNull() ?: ""),
        name = json["name"].stringOrNull() ?: "",
        poster = json["poster"].stringOrNull(),
        background = json["background"].stringOrNull(),
        logo = json["logo"].stringOrNull(),
        description = json["description"].stringOrNull(),
        releaseInfo = json["releaseInfo"].stringOrNull(),
        imdbRating = json["imdbRating"].doubleOrNull(),
        runtime = json["runtime"].stringOrNull(),
        genres = json["genres"].stringList(),
        videos =
            json["videos"]
                .arrOrNull()
                ?.mapNotNull { it.objOrNull()?.let(::metaVideoFromJson) }
                ?: emptyList(),
        cast =
            mergePeople(
                listOf(
                    appExtras?.get("cast"),
                    json["cast"],
                    linkNames(links, setOf("cast", "actor", "actors")),
                ),
            ),
        director =
            mergeNames(
                listOf(
                    appExtras?.get("directors"),
                    json["director"],
                    linkNames(links, setOf("director", "directors")),
                ),
            ),
        writer =
            mergeNames(
                listOf(
                    appExtras?.get("writers"),
                    json["writer"],
                    linkNames(links, setOf("writer", "writers", "screenplay")),
                ),
            ),
        released = json["released"].stringOrNull(),
        country = json["country"].stringOrNull(),
    )
}

internal fun metaVideoFromJson(json: JsonObject): MetaVideo =
    MetaVideo(
        id = json["id"].stringOrNull() ?: "",
        // Some addons name episodes with `name`; the protocol uses `title`.
        title = (json["title"] ?: json["name"]).stringOrNull(),
        season = json["season"].intOrNull()?.let(::Season),
        episode = json["episode"].intOrNull()?.let(::Episode),
        number = json["number"].intOrNull(),
        released = json["released"].stringOrNull(),
        thumbnail = json["thumbnail"].stringOrNull(),
    )

/** Accepts the object form (`{name, character, photo}`) or a bare name. */
internal fun metaPersonFromJson(element: JsonElement?): MetaPerson {
    (element as? JsonPrimitive)?.takeIf { it.isString }?.let {
        return MetaPerson(name = it.content)
    }
    val json = element.objOrNull() ?: return MetaPerson(name = "")
    return MetaPerson(
        name = json["name"].stringOrNull() ?: "",
        character = json["character"].stringOrNull(),
        photo = json["photo"].stringOrNull(),
    )
}

internal fun streamFromJson(json: JsonObject): Stream {
    val hints = json["behaviorHints"].objOrNull()
    return Stream(
        url = json["url"].stringOrNull(),
        ytId = json["ytId"].stringOrNull(),
        infoHash = json["infoHash"].stringOrNull(),
        fileIdx = json["fileIdx"].intOrNull(),
        externalUrl = json["externalUrl"].stringOrNull(),
        name = json["name"].stringOrNull(),
        title = json["title"].stringOrNull(),
        description = json["description"].stringOrNull(),
        behaviorHints = hints?.let(::streamBehaviorHintsFromJson),
        httpHeaders = hints?.let(::proxyHeadersFromHints),
    )
}

private fun streamBehaviorHintsFromJson(json: JsonObject): StreamBehaviorHints =
    StreamBehaviorHints(
        bingeGroup = json["bingeGroup"].stringOrNull(),
        filename = json["filename"].stringOrNull(),
        videoSize = json["videoSize"].longOrNull(),
    )

private fun proxyHeadersFromHints(hints: JsonObject): Map<String, String>? {
    val request = hints["proxyHeaders"].objOrNull()?.get("request").objOrNull() ?: return null
    val headers = request["headers"].objOrNull() ?: return null
    val result =
        headers
            .mapNotNull { (key, value) ->
                value.stringOrNull()?.let { key to it }
            }.toMap()
    return result.ifEmpty { null }
}

internal fun subtitleFromJson(json: JsonObject): Subtitle =
    Subtitle(
        id = json["id"].stringOrNull() ?: "",
        url = json["url"].stringOrNull() ?: "",
        lang = json["lang"].stringOrNull(),
        title = json["title"].stringOrNull(),
        langCode = json["lang_code"].stringOrNull(),
        aiTranslated = json["ai_translated"].boolOrNull() ?: false,
        fromTrusted = json["from_trusted"].boolOrNull() ?: false,
    )

/**
 * Merges people from several sources, deduped by name and in priority order:
 * `app_extras` (objects, with photos), the top-level field (strings, a CSV
 * string, or objects) and `links` names. The first source wins on duplicates.
 */
private fun mergePeople(sources: List<JsonElement?>): List<MetaPerson> {
    val people = mutableListOf<MetaPerson>()

    fun add(person: MetaPerson) {
        if (person.name.isEmpty()) return
        if (people.any { it.name == person.name }) return
        people.add(person)
    }

    fun addValue(value: JsonElement?) {
        when (value) {
            is JsonPrimitive -> {
                if (!value.isString) return
                value.content.split(',').forEach { add(metaPersonFromJson(JsonPrimitive(it.trim()))) }
            }
            is kotlinx.serialization.json.JsonArray -> value.forEach { add(metaPersonFromJson(it)) }
            else -> Unit
        }
    }

    sources.forEach { addValue(it) }
    return people
}

private fun mergeNames(sources: List<JsonElement?>): List<String> = mergePeople(sources).map { it.name }

/**
 * Names of `links` whose category is one of [categories] (case-insensitive).
 */
private fun linkNames(
    links: JsonElement?,
    categories: Set<String>,
): JsonElement? {
    val array = links.arrOrNull() ?: return null
    val names =
        array.mapNotNull { entry ->
            val link = entry.objOrNull() ?: return@mapNotNull null
            val category = link["category"].stringOrNull()?.lowercase() ?: return@mapNotNull null
            if (category !in categories) return@mapNotNull null
            link["name"].stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }
    return kotlinx.serialization.json.JsonArray(names.map { JsonPrimitive(it) })
}
