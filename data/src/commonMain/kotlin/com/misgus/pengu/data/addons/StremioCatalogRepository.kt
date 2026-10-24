package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [CatalogRepository] backed by Stremio addons.
 *
 * Catalogs are read from the enabled addons' manifests, so any addon that
 * declares catalogs shows up without a hardcoded provider.
 */
class StremioCatalogRepository internal constructor(
    private val addons: AddonRepository,
    private val clients: AddonClientPool,
) : CatalogRepository {
    override suspend fun catalogs(): List<CatalogRef> {
        val installed: List<Addon> = runCatchingCancellable { addons.all() }.getOrDefault(emptyList())
        // Resolve the clients up front (single-threaded) so the manifest fetches
        // can run in parallel: one addon no longer waits for the previous one's
        // round-trip.
        val targets =
            installed.mapNotNull { addon ->
                val baseUrl = normalizeAddonBaseUrl(addon.url)
                if (baseUrl.isEmpty()) null else addon to clients.clientFor(baseUrl)
            }
        return coroutineScope {
            targets
                .map { (addon, addonClient) ->
                    async {
                        runCatchingCancellable {
                            val manifest = addonClient.fetchManifest()
                            manifest.catalogs
                                .map { catalog ->
                                    CatalogRef(
                                        addonName = addon.name ?: manifest.name,
                                        addonBaseUrl = addonClient.baseUrl,
                                        type = ContentType(catalog.type),
                                        id = catalog.id,
                                        name = catalog.name,
                                        extras = catalog.extra,
                                    )
                                }
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll()
                .flatten()
        }
    }

    override suspend fun items(
        ref: CatalogRef,
        extra: Map<String, String>?,
    ): List<MetaPreview> = clients.clientFor(ref.addonBaseUrl).fetchCatalog(ref.type.value, ref.id, extra)
}
