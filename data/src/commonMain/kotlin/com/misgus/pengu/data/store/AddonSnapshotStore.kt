package com.misgus.pengu.data.store

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.backend.addonFromRow
import com.misgus.pengu.data.backend.addonToJson
import com.misgus.pengu.data.objOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Last known addon list, per profile, kept on disk.
 *
 * The addon list is otherwise a backend read, which puts a round-trip in front of
 * every catalog request: the app cannot ask an addon for a catalog before it
 * knows which addons exist. Reading the previous list from disk lets the first
 * catalogs start immediately, while the backend answer refreshes the snapshot
 * behind them.
 */
interface AddonSnapshotStore {
    /** The addons last seen for [profileKey], or `null` when nothing was stored. */
    suspend fun read(profileKey: String): List<Addon>?

    /** Replaces the snapshot for [profileKey]. */
    suspend fun write(
        profileKey: String,
        addons: List<Addon>,
    )
}

/** Store that remembers nothing: every read goes to the backend. */
class NoopAddonSnapshotStore : AddonSnapshotStore {
    override suspend fun read(profileKey: String): List<Addon>? = null

    override suspend fun write(
        profileKey: String,
        addons: List<Addon>,
    ) = Unit
}

/**
 * [AddonSnapshotStore] stored as a JSON file.
 *
 * Shape: `{ "<profileKey>": [ { "id": ..., "url": ... } ] }`. The addon rows
 * keep the backend's column names, so the same mapper reads and writes them. A
 * missing or corrupt file degrades to "no snapshot" (the backend is read
 * instead) rather than failing.
 */
class FileAddonSnapshotStore(
    private val path: String = AppPaths.addonsFile,
) : AddonSnapshotStore {
    override suspend fun read(profileKey: String): List<Addon>? {
        val profile = JsonMapStore(path).read()[profileKey]?.arrOrNull() ?: return null
        return profile.mapNotNull { it.objOrNull()?.let(::addonFromRow) }
    }

    override suspend fun write(
        profileKey: String,
        addons: List<Addon>,
    ) {
        val store = JsonMapStore(path)
        val data = store.read().toMutableMap()
        data[profileKey] = JsonArray(addons.map(::addonToJson))
        store.write(JsonObject(data))
    }
}
