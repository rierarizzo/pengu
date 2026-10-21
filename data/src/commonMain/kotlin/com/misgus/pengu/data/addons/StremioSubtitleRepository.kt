package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Subtitle
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.repository.SubtitleRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [SubtitleRepository] backed by Stremio addons.
 *
 * Subtitles are aggregated from every enabled addon, in parallel, and deduped
 * by URL. An addon that fails or has no subtitles for the title is skipped.
 */
class StremioSubtitleRepository internal constructor(
    private val addons: AddonRepository,
    private val clients: AddonClientPool,
) : SubtitleRepository {
    override suspend fun all(
        type: ContentType,
        id: String,
    ): List<Subtitle> {
        val installed: List<Addon> = runCatchingCancellable { addons.all() }.getOrDefault(emptyList())
        if (installed.isEmpty()) return emptyList()
        val lists =
            coroutineScope {
                installed.map { addon -> async { subtitlesFor(addon, type, id) } }.awaitAll()
            }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Subtitle>()
        for (list in lists) {
            for (subtitle in list) {
                if (subtitle.url.isEmpty() || !seen.add(subtitle.url)) continue
                result += subtitle
            }
        }
        return result
    }

    private suspend fun subtitlesFor(
        addon: Addon,
        type: ContentType,
        id: String,
    ): List<Subtitle> {
        val baseUrl = normalizeAddonBaseUrl(addon.url)
        if (baseUrl.isEmpty()) return emptyList()
        return try {
            val name = addon.name ?: hostOf(baseUrl)
            clients.clientFor(baseUrl).fetchSubtitles(type.value, id).map { it.withAddon(name) }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
    }
}
