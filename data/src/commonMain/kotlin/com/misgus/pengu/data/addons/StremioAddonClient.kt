package com.misgus.pengu.data.addons

import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.Stream
import com.misgus.pengu.core.model.Subtitle
import com.misgus.pengu.data.appJson
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.objOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** Thrown when an addon request fails or returns something unexpected. */
class StremioAddonException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    override fun toString(): String = "StremioAddonException: $message"
}

/**
 * Client for the Stremio addon protocol.
 *
 * One instance talks to one addon (one base URL). It implements manifest,
 * catalog, meta, stream and subtitle.
 *
 * The UI must never use this class directly; it is consumed through the
 * repository layer.
 */
class StremioAddonClient(
    baseUrl: String,
    private val client: HttpClient,
) {
    /** Addon base URL, without a trailing slash. */
    val baseUrl: String = normalizeAddonBaseUrl(baseUrl)

    /**
     * Manifest cache. A manifest is immutable for an addon's lifetime, so it is
     * fetched at most once per client and shared by every caller (Home,
     * Discover, Search all reuse the same catalog repository).
     */
    private val manifestMutex = Mutex()
    private var cachedManifest: AddonManifest? = null

    /**
     * In-flight GETs by URL, so a duplicate request joins the first one instead
     * of opening a second round-trip. An entry is dropped once the request
     * settles, so a later caller still gets fresh data.
     */
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<JsonObject>>()

    suspend fun fetchManifest(): AddonManifest {
        cachedManifest?.let { return it }
        return manifestMutex.withLock {
            cachedManifest
                ?: addonManifestFromJson(getJson("$baseUrl/manifest.json")).also { cachedManifest = it }
        }
    }

    /** Fetches a catalog page. [extra] holds protocol params such as `skip`. */
    suspend fun fetchCatalog(
        type: String,
        id: String,
        extra: Map<String, String>? = null,
    ): List<MetaPreview> {
        val json = getJson(catalogUrl(type, id, extra))
        return json["metas"]
            .arrOrNull()
            ?.mapNotNull { it.objOrNull()?.let(::metaPreviewFromJson) }
            ?: emptyList()
    }

    /**
     * Fetches a catalog that answers with `metasDetailed` instead of `metas` —
     * Cinemeta's `calendar-videos` and `last-videos`, which return the full meta
     * (with `videos`) for the ids passed in `extra`.
     */
    suspend fun fetchCatalogDetailed(
        type: String,
        id: String,
        extra: Map<String, String>? = null,
    ): List<MetaDetail> {
        val json = getJson(catalogUrl(type, id, extra))
        return json["metasDetailed"]
            .arrOrNull()
            ?.mapNotNull { it.objOrNull()?.let(::metaDetailFromJson) }
            ?: emptyList()
    }

    /** Fetches the full metadata for [id], or `null` if the addon has none. */
    suspend fun fetchMeta(
        type: String,
        id: String,
    ): MetaDetail? {
        val json = getJson("$baseUrl/meta/$type/$id.json")
        return json["meta"].objOrNull()?.let(::metaDetailFromJson)
    }

    /** Fetches the available streams for [id]. */
    suspend fun fetchStreams(
        type: String,
        id: String,
    ): List<Stream> {
        val json = getJson("$baseUrl/stream/$type/$id.json")
        return json["streams"]
            .arrOrNull()
            ?.mapNotNull { it.objOrNull()?.let(::streamFromJson) }
            ?: emptyList()
    }

    /** Fetches the available subtitles for [id]. */
    suspend fun fetchSubtitles(
        type: String,
        id: String,
    ): List<Subtitle> {
        val json = getJson("$baseUrl/subtitles/$type/$id.json")
        return json["subtitles"]
            .arrOrNull()
            ?.mapNotNull { it.objOrNull()?.let(::subtitleFromJson) }
            ?: emptyList()
    }

    private fun catalogUrl(
        type: String,
        id: String,
        extra: Map<String, String>?,
    ): String {
        val path = "$baseUrl/catalog/$type/$id"
        if (extra.isNullOrEmpty()) return "$path.json"
        // The extra segment keeps `=` and `&` raw (AIOStreams and Nuvio do not
        // decode a fully-encoded segment); only the values are encoded.
        val segment = extra.entries.joinToString("&") { "${it.key}=${encodeValue(it.value)}" }
        return "$path/$segment.json"
    }

    /**
     * Fetches [url], sharing one request with any concurrent caller that wants the
     * same URL.
     *
     * A catalog can be requested by more than one screen at once (Home, Discover,
     * the catalog grid), and the same addon can be asked for the same URL twice;
     * the in-flight map turns that into a single request instead of a duplicate
     * round-trip.
     */
    private suspend fun getJson(url: String): JsonObject {
        val (deferred, isOwner) =
            inFlightMutex.withLock {
                val existing = inFlight[url]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<JsonObject>().also { inFlight[url] = it } to true
                }
            }
        if (!isOwner) return deferred.await()
        return try {
            fetchJson(url).also(deferred::complete)
        } catch (cancellation: CancellationException) {
            deferred.cancel(cancellation)
            throw cancellation
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            inFlightMutex.withLock { inFlight.remove(url) }
        }
    }

    private suspend fun fetchJson(url: String): JsonObject {
        val response =
            try {
                client.get(url)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                throw StremioAddonException("Request to $url failed", error)
            }
        if (response.status.value != 200) {
            throw StremioAddonException(
                "Request to $url failed with HTTP ${response.status.value}",
            )
        }
        val text =
            try {
                // Decode as UTF-8 explicitly: addons may not declare a charset.
                response.readRawBytes().decodeToString()
            } catch (error: Exception) {
                throw StremioAddonException("Could not read the response from $url", error)
            }
        val decoded =
            try {
                appJson.parseToJsonElement(text)
            } catch (error: Exception) {
                throw StremioAddonException("Invalid JSON from $url", error)
            }
        return decoded.objOrNull()
            ?: throw StremioAddonException("Expected a JSON object from $url")
    }
}

/**
 * Turns an addon URL into its base URL.
 *
 * Accepts a manifest URL (`https://x/manifest.json`), with or without a query
 * string, or a plain base (`https://x/`), and returns `https://x`.
 */
fun normalizeAddonBaseUrl(url: String): String {
    val withoutQuery = url.substringBefore('?')
    val withoutManifest = withoutQuery.removeSuffix("/manifest.json")
    return withoutManifest.trimEnd('/')
}

private const val HEX = "0123456789ABCDEF"

/** RFC 3986 percent-encoding for a single URL path/query value. */
internal fun encodeValue(value: String): String =
    buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            // ASCII only: non-ASCII bytes must be percent-encoded, and a Latin-1
            // char above 0x7F is not a valid literal URL character.
            val isUnreserved = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-_.~"
            if (isUnreserved) {
                append(char)
            } else {
                append('%')
                append(HEX[code shr 4])
                append(HEX[code and 0x0F])
            }
        }
    }
