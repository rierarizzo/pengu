package com.misgus.pengu.data.backend

import com.misgus.pengu.core.BackendException
import com.misgus.pengu.data.appJson
import com.misgus.pengu.data.objOrNull
import com.misgus.pengu.data.stringOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement

/**
 * Raw HTTP against a Nuvio (Supabase) deployment.
 *
 * Transport only: it owns the base URL and the HTTP verbs, not the discovery
 * document or the session. Callers pass the `apikey` and the bearer token they
 * resolved, so [NuvioDiscovery], [NuvioAuth] and [NuvioRest] share one HTTP
 * client without sharing state.
 */
class NuvioTransport(
    baseUrl: String = DEFAULT_BASE_URL,
    private val http: HttpClient,
) {
    /** Base URL with any trailing slash removed. */
    val baseUrl: String = baseUrl.trimEnd('/')

    /** Runs one HTTP call, translating transport failures but not cancellation. */
    suspend fun send(
        method: String,
        url: String,
        body: JsonElement? = null,
        token: String? = null,
        apiKey: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse =
        try {
            when (method) {
                "GET" -> http.get(url) { applyNuvioHeaders(apiKey, token, extraHeaders) }
                "POST" ->
                    http.post(url) {
                        applyNuvioHeaders(apiKey, token, extraHeaders)
                        if (body != null) {
                            contentType(ContentType.Application.Json)
                            setBody(body.toString())
                        }
                    }
                "PATCH" ->
                    http.patch(url) {
                        applyNuvioHeaders(apiKey, token, extraHeaders)
                        if (body != null) {
                            contentType(ContentType.Application.Json)
                            setBody(body.toString())
                        }
                    }
                "DELETE" -> http.delete(url) { applyNuvioHeaders(apiKey, token, extraHeaders) }
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw BackendException("Request to $url failed", cause = error)
        }

    /**
     * [send], then decode the body.
     *
     * A non-2xx response becomes a [BackendException] carrying the status code
     * and the backend's message; an empty body decodes to `null`.
     */
    suspend fun request(
        method: String,
        url: String,
        body: JsonElement? = null,
        token: String? = null,
        apiKey: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement? = decode(send(method, url, body, token, apiKey, extraHeaders), url)

    /** Decodes a response that [send] already returned. */
    suspend fun decode(
        response: HttpResponse,
        url: String,
    ): JsonElement? {
        if (response.status.value !in 200..299) {
            throw BackendException(
                errorMessage(response.readRawBytes().decodeToString())
                    ?: "Request to $url failed with HTTP ${response.status.value}",
                statusCode = response.status.value,
            )
        }
        val bytes = response.readRawBytes()
        if (bytes.isEmpty()) return null
        return try {
            appJson.parseToJsonElement(bytes.decodeToString())
        } catch (error: Exception) {
            throw BackendException("Invalid JSON from $url", cause = error)
        }
    }

    /** Releases the underlying HTTP client. */
    fun close() {
        http.close()
    }

    /** Extracts a human-readable message from a Supabase error body. */
    private fun errorMessage(text: String): String? =
        runCatching {
            appJson.parseToJsonElement(text).objOrNull()?.let { obj ->
                listOf("msg", "error_description", "message", "error")
                    .firstNotNullOfOrNull { obj[it].stringOrNull()?.takeIf { value -> value.isNotEmpty() } }
            }
        }.getOrNull()

    companion object {
        /** Official Nuvio-hosted backend. */
        const val DEFAULT_BASE_URL = "https://api.nuvio.tv"
    }
}

/** Headers every Nuvio request carries: JSON, the public key and the bearer token. */
private fun HttpRequestBuilder.applyNuvioHeaders(
    apiKey: String?,
    token: String?,
    extraHeaders: Map<String, String>,
) {
    header("Accept", "application/json")
    if (!apiKey.isNullOrEmpty()) header("apikey", apiKey)
    token?.let { header("Authorization", "Bearer $it") }
    extraHeaders.forEach { (key, value) -> header(key, value) }
}
