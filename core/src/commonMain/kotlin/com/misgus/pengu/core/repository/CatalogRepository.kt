package com.misgus.pengu.core.repository

import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.CatalogExtras
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview

/**
 * One catalog offered by an addon, ready to be fetched.
 *
 * @property name Display name of the catalog.
 */
data class CatalogRef(
    /** Display name of the addon that owns the catalog. */
    val addonName: String,
    /** Addon base URL (no `/manifest.json`). */
    val addonBaseUrl: String,
    /** `movie`, `series`, ... */
    val type: ContentType,
    /** Catalog id inside the addon manifest. */
    val id: String,
    val name: String,
    /**
     * `extra` parameters the catalog declares (e.g. `search`, `genre`, `skip`).
     *
     * The UI reads it to know which catalogs accept a search query without
     * fetching every manifest itself.
     */
    val extras: List<AddonCatalogExtra> = emptyList(),
) {
    /** Stable key for dedupe and identity. */
    val key: String get() = "$addonBaseUrl|$type|$id"

    /** Whether the catalog accepts a free-text `search` parameter. */
    val searches: Boolean get() = extras.any { it.name == CatalogExtras.SEARCH }

    /**
     * Whether the catalog can be fetched with no query parameter.
     *
     * A catalog with a required extra cannot be browsed: it needs a query its
     * grid does not have (a search term, a list of ids, ...), and asking anyway
     * just returns empty. Home and Discover only use browsable catalogs; Search
     * uses [searches].
     */
    val browsable: Boolean get() = extras.none { it.isRequired }
}

/**
 * One row of the last Home feed: a catalog and the items it answered.
 *
 * Home paints these before the addons answer, so the type lives here and not in
 * the UI: the store that keeps them is part of the catalog path.
 */
data class CatalogRow(
    val ref: CatalogRef,
    val title: String,
    val items: List<MetaPreview>,
)

/**
 * Reads the catalogs the account's addons expose, and their items.
 *
 * The UI depends on this interface, never on the addon client in `data/`.
 */
interface CatalogRepository {
    /** Catalogs offered by the enabled addons, in the account's order. */
    suspend fun catalogs(): List<CatalogRef>

    /** Items of [ref]. [extra] holds protocol params such as `genre` or `search`. */
    suspend fun items(
        ref: CatalogRef,
        extra: Map<String, String>? = null,
    ): List<MetaPreview>

    /**
     * Rows of the last Home load for the active profile, or empty when none.
     *
     * Served so the feed paints before the first round-trip: every cold start
     * otherwise waits for each addon's manifest and then its catalog.
     */
    suspend fun cachedRows(): List<CatalogRow> = emptyList()

    /** Remembers [rows] as the feed to paint on the next cold start. */
    suspend fun cacheRows(rows: List<CatalogRow>) = Unit
}
