package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.AddonCatalog
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.model.CatalogExtras
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [MetadataRepository] backed by Stremio addons.
 *
 * Metadata is always resolved from the account's enabled addons, in their
 * order, and the first one that returns a meta wins. No provider is baked in.
 */
class StremioMetadataRepository internal constructor(
    private val addons: AddonRepository,
    private val clients: AddonClientPool,
) : MetadataRepository {
    constructor(addons: AddonRepository, client: HttpClient) : this(addons, AddonClientPool(client))

    override suspend fun detail(
        type: ContentType,
        id: String,
    ): MetaDetail? {
        for (baseUrl in candidates()) {
            val meta = runCatchingCancellable { clients.clientFor(baseUrl).fetchMeta(type.value, id) }.getOrNull()
            if (meta != null) return meta
        }
        return null
    }

    override suspend fun similar(
        type: ContentType,
        id: String,
        genre: String?,
    ): List<MetaPreview> {
        val urls = candidates()
        if (urls.isEmpty()) return emptyList()
        // The manifests are independent, so they are fetched together; the
        // catalogs are then tried in the account's order, first hit wins.
        val manifests =
            coroutineScope {
                urls.map { url -> async { manifestFor(url) } }.awaitAll()
            }
        for ((url, manifest) in manifests) {
            if (manifest == null) continue
            val items = tryCatalog(url, manifest, type, id, genre)
            if (items != null) return items
        }
        return emptyList()
    }

    /** Enabled addon base URLs, in the account's order, without duplicates. */
    private suspend fun candidates(): List<String> {
        val urls = mutableListOf<String>()
        val installed = runCatchingCancellable { addons.all() }.getOrNull() ?: return urls
        for (addon in installed) {
            val normalized = normalizeAddonBaseUrl(addon.url)
            if (normalized.isNotEmpty() && normalized !in urls) urls += normalized
        }
        return urls
    }

    /** One addon's manifest, or `null` when it is unreachable. */
    private suspend fun manifestFor(url: String): Pair<String, AddonManifest?> =
        url to runCatchingCancellable { clients.clientFor(url).fetchManifest() }.getOrNull()

    /** Returns the similar titles, or `null` when this addon cannot provide them. */
    private suspend fun tryCatalog(
        baseUrl: String,
        manifest: AddonManifest,
        type: ContentType,
        id: String,
        genre: String?,
    ): List<MetaPreview>? {
        val addonClient = clients.clientFor(baseUrl)
        return try {
            val catalog = pickCatalog(manifest.catalogs, type.value, genre) ?: return null
            val supportsGenre = !genre.isNullOrEmpty() && catalog.extra.any { it.name == CatalogExtras.GENRE }
            // Prefer a genre match, but fall back to the generic catalog so a
            // title is never left without a "similar" row.
            val byGenre =
                if (supportsGenre) {
                    runCatchingCancellable {
                        addonClient.fetchCatalog(type.value, catalog.id, mapOf(CatalogExtras.GENRE to genre))
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            val items = byGenre.ifEmpty { addonClient.fetchCatalog(type.value, catalog.id, null) }
            items.filter { it.id != id }.ifEmpty { null }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Catalog to use for a "similar" row.
     *
     * Prefers one that can filter by [genre], and skips catalogs that need an
     * extra we cannot provide (a search term, a list of ids, ...), because those
     * cannot be fetched on their own. Falls back to any usable catalog of
     * [type].
     */
    private fun pickCatalog(
        catalogs: List<AddonCatalog>,
        type: String,
        genre: String?,
    ): AddonCatalog? {
        val usable = catalogs.filter { it.type == type && canFetch(it, genre) }
        if (usable.isEmpty()) return null
        if (genre.isNullOrEmpty()) return usable.first()
        return usable.firstOrNull { catalog -> catalog.extra.any { it.name == CatalogExtras.GENRE } } ?: usable.first()
    }

    /** Whether [catalog] can be fetched without an extra we do not have. */
    private fun canFetch(
        catalog: AddonCatalog,
        genre: String?,
    ): Boolean =
        catalog.extra.none { extra ->
            extra.isRequired && !(extra.name == CatalogExtras.GENRE && !genre.isNullOrEmpty())
        }
}
