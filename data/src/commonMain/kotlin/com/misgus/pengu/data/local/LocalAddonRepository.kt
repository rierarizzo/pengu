package com.misgus.pengu.data.local

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.data.addons.AddonClientPool
import com.misgus.pengu.data.addons.normalizeAddonBaseUrl
import io.ktor.client.HttpClient

/**
 * [AddonRepository] for local mode: a fixed list configured at startup.
 *
 * The list comes from the composition (`PENGU_LOCAL_ADDONS` on desktop), so
 * there is no write path: the settings page shows it read-only. Manifests are
 * still read so the rows can show each addon's logo and description.
 */
class LocalAddonRepository(
    private val addons: List<Addon>,
    httpClient: HttpClient? = null,
) : AddonRepository {
    private val clients = httpClient?.let(::AddonClientPool)

    override suspend fun all(): List<Addon> = addons.filter { it.enabled }

    override suspend fun installed(): List<Addon> = addons

    override suspend fun manifest(url: String): AddonManifest? {
        val clients = clients ?: return null
        val baseUrl = normalizeAddonBaseUrl(url)
        if (baseUrl.isEmpty()) return null
        return runCatchingCancellable { clients.clientFor(baseUrl).fetchManifest() }.getOrNull()
    }
}
