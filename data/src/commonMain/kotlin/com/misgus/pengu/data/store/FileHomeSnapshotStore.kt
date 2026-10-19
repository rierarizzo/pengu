package com.misgus.pengu.data.store

import com.misgus.pengu.core.catalog.HomeSnapshot
import com.misgus.pengu.core.catalog.HomeSnapshotRow
import com.misgus.pengu.core.catalog.HomeSnapshotStore
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.data.addons.addonCatalogExtraFromJson
import com.misgus.pengu.data.addons.metaPreviewFromJson
import com.misgus.pengu.data.addons.metaPreviewToJson
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.instantMillisOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [HomeSnapshotStore] stored as a JSON file.
 *
 * Shape: `{ "<profileKey>": { "rows": [ { "ref": ..., "items": [ ... ] } ],
 * "featured": [ ... ], "featuredAt": 1700000000000 } }`. A missing or corrupt
 * file degrades to "no snapshot" (Home starts from the network) instead of
 * failing, and a snapshot without rows is treated the same way: there is nothing
 * to paint.
 */
class FileHomeSnapshotStore(
    private val path: String = AppPaths.homeSnapshotFile,
) : HomeSnapshotStore {
    override suspend fun read(profileKey: String): HomeSnapshot? {
        val entry = JsonMapStore(path).read()[profileKey]?.objOrNull() ?: return null
        val rows =
            entry["rows"]
                .arrOrNull()
                ?.mapNotNull { it.objOrNull()?.let(::rowFromJson) }
                .orEmpty()
        if (rows.isEmpty()) return null
        val featured =
            entry["featured"]
                .arrOrNull()
                ?.mapNotNull { it.objOrNull()?.let(::metaPreviewFromSnapshot) }
                .orEmpty()
        return HomeSnapshot(
            rows = rows,
            featured = featured,
            featuredAt = entry["featuredAt"].instantMillisOrNull(),
        )
    }

    override suspend fun write(
        profileKey: String,
        snapshot: HomeSnapshot,
    ) {
        if (snapshot.rows.isEmpty()) return
        val store = JsonMapStore(path)
        val data = store.read().toMutableMap()
        val entry = mutableMapOf<String, JsonElement>()
        entry["rows"] = JsonArray(snapshot.rows.map(::rowToJson))
        entry["featured"] = JsonArray(snapshot.featured.map(::metaPreviewToJson))
        snapshot.featuredAt?.let { entry["featuredAt"] = JsonPrimitive(it.toEpochMilliseconds()) }
        data[profileKey] = JsonObject(entry)
        store.write(JsonObject(data))
    }

    private fun rowToJson(row: HomeSnapshotRow): JsonObject =
        JsonObject(
            mapOf(
                "ref" to catalogRefToJson(row.ref),
                "items" to JsonArray(row.items.map(::metaPreviewToJson)),
            ),
        )

    private fun rowFromJson(json: JsonObject): HomeSnapshotRow? {
        val ref = json["ref"].objOrNull()?.let(::catalogRefFromJson) ?: return null
        val items = json["items"].arrOrNull()?.mapNotNull { it.objOrNull()?.let(::metaPreviewFromSnapshot) }
        if (items.isNullOrEmpty()) return null
        return HomeSnapshotRow(ref, items)
    }

    /** [metaPreviewFromJson] plus the enrichment-only field it does not know. */
    private fun metaPreviewFromSnapshot(json: JsonObject): MetaPreview =
        metaPreviewFromJson(json).copy(textlessPoster = json["textlessPoster"].stringOrNull())

    private fun catalogRefToJson(ref: CatalogRef): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        map["addonName"] = JsonPrimitive(ref.addonName)
        map["addonBaseUrl"] = JsonPrimitive(ref.addonBaseUrl)
        map["type"] = JsonPrimitive(ref.type.value)
        map["id"] = JsonPrimitive(ref.id)
        map["name"] = JsonPrimitive(ref.name)
        if (ref.extras.isNotEmpty()) {
            map["extras"] =
                JsonArray(
                    ref.extras.map { extra ->
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(extra.name),
                                "isRequired" to JsonPrimitive(extra.isRequired),
                                "options" to JsonArray(extra.options.map(::JsonPrimitive)),
                            ),
                        )
                    },
                )
        }
        return JsonObject(map)
    }

    private fun catalogRefFromJson(json: JsonObject): CatalogRef =
        CatalogRef(
            addonName = json["addonName"].stringOrNull() ?: "",
            addonBaseUrl = json["addonBaseUrl"].stringOrNull() ?: "",
            type = ContentType(json["type"].stringOrNull() ?: ""),
            id = json["id"].stringOrNull() ?: "",
            name = json["name"].stringOrNull() ?: "",
            extras = json["extras"].arrOrNull()?.mapNotNull { it.objOrNull()?.let(::addonCatalogExtraFromJson) }.orEmpty(),
        )
}
