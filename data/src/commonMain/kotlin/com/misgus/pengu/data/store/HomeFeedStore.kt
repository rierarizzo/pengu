package com.misgus.pengu.data.store

import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRow
import com.misgus.pengu.data.addons.addonCatalogExtraFromJson
import com.misgus.pengu.data.addons.metaPreviewFromJson
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Last Home feed, per profile, kept on disk.
 *
 * A cold Home otherwise waits for every addon's manifest and then for its
 * catalog: with the usual dozen addons that is several round-trips before the
 * first row paints, and the addon HTTP cache does not help because most addons
 * declare no freshness. Painting the previous feed first removes that wait; the
 * network answer replaces it a moment later.
 */
interface HomeFeedStore {
    /** The rows last painted for [profileKey], or `null` when nothing was stored. */
    suspend fun read(profileKey: String): List<CatalogRow>?

    /** Replaces the rows stored for [profileKey]. */
    suspend fun write(
        profileKey: String,
        rows: List<CatalogRow>,
    )
}

/** Store that remembers nothing: Home always waits for the addons. */
class NoopHomeFeedStore : HomeFeedStore {
    override suspend fun read(profileKey: String): List<CatalogRow>? = null

    override suspend fun write(
        profileKey: String,
        rows: List<CatalogRow>,
    ) = Unit
}

/**
 * [HomeFeedStore] stored as a JSON file.
 *
 * Shape: `{ "<profileKey>": [ { "title": ..., "ref": {...}, "items": [...] } ] }`.
 * Items keep the Stremio keys, so the same parser that reads an addon catalog
 * reads them back. A missing or corrupt file degrades to "no feed" (Home loads
 * from the addons) rather than failing.
 */
class FileHomeFeedStore(
    private val path: String = AppPaths.homeFeedFile,
) : HomeFeedStore {
    override suspend fun read(profileKey: String): List<CatalogRow>? {
        val rows = JsonMapStore(path).read()[profileKey]?.arrOrNull() ?: return null
        // A row without a catalog or an addon cannot be addressed anymore, so it
        // is dropped instead of painting a dead header.
        return rows
            .mapNotNull { it.objOrNull()?.let(::catalogRowFromJson) }
            .filter { it.ref.id.isNotEmpty() && it.ref.addonBaseUrl.isNotEmpty() }
    }

    override suspend fun write(
        profileKey: String,
        rows: List<CatalogRow>,
    ) {
        val store = JsonMapStore(path)
        val data = store.read().toMutableMap()
        data[profileKey] = JsonArray(rows.map(::catalogRowToJson))
        store.write(JsonObject(data))
    }
}

private fun catalogRowToJson(row: CatalogRow): JsonObject =
    JsonObject(
        mapOf(
            "title" to JsonPrimitive(row.title),
            "ref" to
                JsonObject(
                    mapOf(
                        "addonName" to JsonPrimitive(row.ref.addonName),
                        "addonBaseUrl" to JsonPrimitive(row.ref.addonBaseUrl),
                        "type" to JsonPrimitive(row.ref.type.value),
                        "id" to JsonPrimitive(row.ref.id),
                        "name" to JsonPrimitive(row.ref.name),
                        "extras" to JsonArray(row.ref.extras.map(::catalogExtraToJson)),
                    ),
                ),
            "items" to JsonArray(row.items.map(::metaPreviewToJson)),
        ),
    )

private fun catalogExtraToJson(extra: AddonCatalogExtra): JsonObject =
    JsonObject(
        mapOf(
            "name" to JsonPrimitive(extra.name),
            "isRequired" to JsonPrimitive(extra.isRequired),
            "options" to JsonArray(extra.options.map(::JsonPrimitive)),
        ),
    )

/** The keys [metaPreviewFromJson] reads, so writer and parser stay paired. */
private fun metaPreviewToJson(item: MetaPreview): JsonObject =
    JsonObject(
        buildMap {
            put("id", JsonPrimitive(item.id))
            put("type", JsonPrimitive(item.type.value))
            put("name", JsonPrimitive(item.name))
            item.poster?.let { put("poster", JsonPrimitive(it)) }
            item.posterShape?.let { put("posterShape", JsonPrimitive(it)) }
            item.background?.let { put("background", JsonPrimitive(it)) }
            item.logo?.let { put("logo", JsonPrimitive(it)) }
            item.description?.let { put("description", JsonPrimitive(it)) }
            item.releaseInfo?.let { put("releaseInfo", JsonPrimitive(it)) }
            item.imdbRating?.let { put("imdbRating", JsonPrimitive(it)) }
            item.genres.takeIf { it.isNotEmpty() }?.let { put("genres", JsonArray(it.map(::JsonPrimitive))) }
        },
    )

private fun catalogRowFromJson(json: JsonObject): CatalogRow? {
    val ref = json["ref"].objOrNull() ?: return null
    val items = json["items"].arrOrNull() ?: return null
    return CatalogRow(
        ref =
            CatalogRef(
                addonName = ref["addonName"].stringOrNull() ?: "",
                addonBaseUrl = ref["addonBaseUrl"].stringOrNull() ?: "",
                type = ContentType(ref["type"].stringOrNull() ?: ""),
                id = ref["id"].stringOrNull() ?: "",
                name = ref["name"].stringOrNull() ?: "",
                extras =
                    ref["extras"]
                        .arrOrNull()
                        ?.mapNotNull { it.objOrNull()?.let(::addonCatalogExtraFromJson) }
                        .orEmpty(),
            ),
        title = json["title"].stringOrNull() ?: "",
        items = items.mapNotNull { it.objOrNull()?.let(::metaPreviewFromJson) },
    )
}
