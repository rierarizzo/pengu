package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.StreamGroup
import com.misgus.pengu.core.repository.StreamRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [StreamRepository] backed by Stremio addons.
 *
 * Unlike metadata, streams are **aggregated**: every enabled addon is asked, in
 * parallel, and the results are grouped by addon. An addon that fails or has no
 * streams for the title is skipped.
 */
class StremioStreamRepository internal constructor(
    private val addons: AddonRepository,
    private val clients: AddonClientPool,
) : StreamRepository {
    constructor(addons: AddonRepository, client: HttpClient) : this(addons, AddonClientPool(client))

    override suspend fun all(
        type: ContentType,
        id: String,
    ): List<StreamGroup> {
        val installed: List<Addon> = runCatchingCancellable { addons.all() }.getOrDefault(emptyList())
        if (installed.isEmpty()) return emptyList()
        val groups =
            coroutineScope {
                installed.map { addon -> async { streamsFor(addon, type, id) } }.awaitAll()
            }
        return groups.filterNotNull()
    }

    private suspend fun streamsFor(
        addon: Addon,
        type: ContentType,
        id: String,
    ): StreamGroup? {
        val baseUrl = normalizeAddonBaseUrl(addon.url)
        if (baseUrl.isEmpty()) return null
        return try {
            val streams = clients.clientFor(baseUrl).fetchStreams(type.value, id)
            if (streams.isEmpty()) {
                null
            } else {
                StreamGroup(addonName = addon.name ?: hostOf(baseUrl), streams = streams)
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A streams-less or unreachable addon is skipped.
            null
        }
    }
}

internal fun hostOf(url: String): String = url.substringAfter("://", url).substringBefore('/').substringBefore(':')
