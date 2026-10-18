package com.misgus.pengu.data.addons

import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.StreamRepository
import com.misgus.pengu.core.repository.SubtitleRepository
import io.ktor.client.HttpClient

/**
 * The four Stremio repositories built on one [AddonClientPool].
 *
 * A pooled client caches its addon's manifest, so the composition roots build
 * the repositories through here instead of one by one: a single pool means the
 * manifest is fetched once per process, not once per repository. That matters on
 * Home, where the catalog rows and the watched-badge metadata lookups hit the
 * same addons back to back.
 */
class StremioAddonRepositories(
    addons: AddonRepository,
    client: HttpClient,
) {
    private val clients = AddonClientPool(client)

    val catalogs: CatalogRepository = StremioCatalogRepository(addons, clients)

    val metadata: MetadataRepository = StremioMetadataRepository(addons, clients)

    val streams: StreamRepository = StremioStreamRepository(addons, clients)

    val subtitles: SubtitleRepository = StremioSubtitleRepository(addons, clients)
}
