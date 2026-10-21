package com.misgus.pengu.data.tmdb

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.TmdbEpisode
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.TmdbLanguage
import com.misgus.pengu.core.repository.TmdbLookup
import com.misgus.pengu.core.repository.TmdbRepository
import com.misgus.pengu.core.repository.TmdbVerifyResult
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.platform.systemLanguageTag
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.hours

/**
 * [TmdbRepository] over the TMDB v3 API.
 *
 * It is wired by the composition roots and handed to the detail screen and the
 * home billboard. The API key is read from the settings on every call, so
 * clearing it in Settings turns the enrichment off immediately and no request
 * leaves the app without it.
 */
class TmdbMetadataRepository(
    http: HttpClient,
    private val settings: SettingsRepository,
) : TmdbRepository {
    private val client = TmdbClient(http)
    private val resolver = TmdbIdResolver(client)
    private val details = TmdbCache<TmdbTitle>(DETAIL_TTL)
    private val related = TmdbCache<List<MetaPreview>>(RECOMMENDATIONS_TTL)
    private val seasons = TmdbCache<List<TmdbEpisode>>(DETAIL_TTL)

    override val isConfigured: Boolean
        get() =
            settings.state.value.tmdbApiKey
                .isNotBlank()

    override suspend fun lookup(request: TmdbLookup): TmdbTitle? {
        val key = settings.state.value.tmdbApiKey
        if (key.isBlank()) return null
        val language = language()
        val ref = resolve(request, key, language) ?: return null
        return detail(ref, key, language)
    }

    override suspend fun recommendations(request: TmdbLookup): List<MetaPreview> {
        val key = settings.state.value.tmdbApiKey
        if (key.isBlank()) return emptyList()
        val language = language()
        val ref = resolve(request, key, language) ?: return emptyList()
        val cacheKey = "${ref.mediaType}:${ref.id}:$language"
        related.get(cacheKey)?.let { return it }
        val items =
            fetchRelated("/${ref.pathSegment}/${ref.id}/recommendations", ref, key, language)
                .ifEmpty { fetchRelated("/${ref.pathSegment}/${ref.id}/similar", ref, key, language) }
        related.put(cacheKey, items)
        return items
    }

    override suspend fun season(
        request: TmdbLookup,
        seasonNumber: Int,
    ): List<TmdbEpisode> {
        val key = settings.state.value.tmdbApiKey
        if (key.isBlank()) return emptyList()
        val language = language()
        val ref = resolve(request, key, language) ?: return emptyList()
        // Only a series has seasons: a movie's single "episode" is the movie
        // itself, which the detail page already describes.
        if (ref.mediaType != TmdbMediaType.SERIES) return emptyList()
        val cacheKey = "${ref.id}:$seasonNumber:$language"
        seasons.get(cacheKey)?.let { return it }
        val json =
            client.getJson(
                path = "/${ref.pathSegment}/${ref.id}/season/$seasonNumber",
                key = key,
                language = language,
            )
        val episodes = tmdbEpisodesFromJson(json)
        seasons.put(cacheKey, episodes)
        return episodes
    }

    override suspend fun verify(apiKey: String): TmdbVerifyResult {
        if (apiKey.isBlank()) return TmdbVerifyResult.INVALID_KEY
        return try {
            client.getJson(path = "/configuration", key = apiKey.trim())
            TmdbVerifyResult.VALID
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: TmdbException) {
            if (error.status == UNAUTHORIZED) TmdbVerifyResult.INVALID_KEY else TmdbVerifyResult.UNREACHABLE
        } catch (_: Throwable) {
            TmdbVerifyResult.UNREACHABLE
        }
    }

    private suspend fun resolve(
        request: TmdbLookup,
        key: String,
        language: String,
    ): TmdbRef? =
        resolver.resolve(
            parsed = parseContentId(request.contentId),
            type = request.type,
            name = request.name,
            year = request.year,
            key = key,
            language = language,
        )

    private suspend fun detail(
        ref: TmdbRef,
        key: String,
        language: String,
    ): TmdbTitle? {
        val cacheKey = "${ref.mediaType}:${ref.id}:$language"
        details.get(cacheKey)?.let { return it }
        val append =
            if (ref.mediaType == TmdbMediaType.SERIES) {
                "images,external_ids,credits,aggregate_credits"
            } else {
                "images,external_ids,credits"
            }
        val json =
            client.getJson(
                path = "/${ref.pathSegment}/${ref.id}",
                key = key,
                language = language,
                params =
                    mapOf(
                        // A series' crew roles only survive aggregation (every
                        // episode director, every writer), so it is asked for on
                        // top of the plain credits.
                        "append_to_response" to append,
                        "include_image_language" to imageLanguages(language),
                    ),
            )
        val title = tmdbTitleFromJson(json, ref.mediaType, language)
        details.put(cacheKey, title)
        return title
    }

    private suspend fun fetchRelated(
        path: String,
        ref: TmdbRef,
        key: String,
        language: String,
    ): List<MetaPreview> {
        val json = client.getJson(path = path, key = key, language = language)
        val type = ref.mediaType.toContentType()
        return json["results"]
            .arrOrNull()
            ?.mapNotNull { it.objOrNull()?.let { item -> tmdbPreviewFromJson(item, type) } }
            ?.take(MAX_RECOMMENDATIONS)
            ?: emptyList()
    }

    /** The language TMDB answers in: the settings choice, or the system locale. */
    private fun language(): String {
        val tag =
            when (settings.state.value.tmdbLanguage) {
                TmdbLanguage.SYSTEM -> systemLanguageTag()
                TmdbLanguage.ENGLISH -> ENGLISH
                TmdbLanguage.SPANISH -> SPANISH
            }
        return tag.takeIf { it.isNotBlank() } ?: ENGLISH
    }

    /**
     * Which images to ask for, as ISO-639-1 codes.
     *
     * The requested language and English cover the logos, and the literal `null`
     * asks for the language-neutral images, which is where the textless poster
     * the mobile billboard needs lives.
     */
    private fun imageLanguages(language: String): String =
        listOf(language.substringBefore('-').lowercase(), "en", "null")
            .distinct()
            .joinToString(",")

    private val TmdbRef.pathSegment: String
        get() = if (mediaType == TmdbMediaType.MOVIE) "movie" else "tv"

    private fun TmdbMediaType.toContentType(): ContentType = if (this == TmdbMediaType.MOVIE) ContentTypes.MOVIE else ContentTypes.SERIES

    private companion object {
        const val ENGLISH = "en-US"
        const val SPANISH = "es-ES"
        const val UNAUTHORIZED = 401
        const val MAX_RECOMMENDATIONS = 20
        val DETAIL_TTL = 24.hours
        val RECOMMENDATIONS_TTL = 24.hours
    }
}
