package com.misgus.pengu.data.backend

import com.misgus.pengu.core.BackendException
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.data.arrOrNull
import com.misgus.pengu.data.objOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * PostgREST data access for a Nuvio (Supabase) deployment.
 *
 * Every call resolves a fresh token from [auth] and, when the backend answers
 * `401` even though the token looked valid, renews once and retries. Row-level
 * security on the backend scopes each read to the signed-in user.
 */
class NuvioRest(
    private val transport: NuvioTransport,
    private val discovery: NuvioDiscovery,
    private val auth: NuvioAuth,
) {
    /**
     * Runs a PostgREST read on [table].
     *
     * [order] and [filter] are passed through as-is, e.g. `added_at.desc` and
     * `profile_id=eq.1`. Callers must escape user-supplied values in [filter]
     * (see `postgrestLiteral`).
     */
    suspend fun select(
        table: String,
        order: String? = null,
        filter: String? = null,
    ): List<JsonObject> {
        val query =
            buildString {
                append("?select=*")
                order?.let { append("&order=$it") }
                filter?.let { append("&$it") }
            }
        val json = authenticated("GET", "${transport.baseUrl}/rest/v1/$table$query")
        val array = json.arrOrNull() ?: throw BackendException("Expected a JSON array from $table")
        return array.mapNotNull { it.objOrNull() }
    }

    /** Inserts [row] into [table] (PostgREST `POST`, `return=minimal`). */
    suspend fun insert(
        table: String,
        row: JsonObject,
    ) {
        authenticated(
            "POST",
            "${transport.baseUrl}/rest/v1/$table",
            body = row,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
    }

    /** Calls a PostgREST RPC (`POST /rest/v1/rpc/<name>`) and ignores the result. */
    suspend fun callRpc(
        name: String,
        params: JsonObject,
    ) {
        authenticated(
            "POST",
            "${transport.baseUrl}/rest/v1/rpc/$name",
            body = params,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
    }

    /** Patches the rows of [table] matching the PostgREST [filter]. */
    suspend fun update(
        table: String,
        filter: String,
        row: JsonObject,
    ) {
        authenticated(
            "PATCH",
            "${transport.baseUrl}/rest/v1/$table?$filter",
            body = row,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
    }

    /** Deletes the rows of [table] matching the PostgREST [filter]. */
    suspend fun delete(
        table: String,
        filter: String,
    ) {
        authenticated(
            "DELETE",
            "${transport.baseUrl}/rest/v1/$table?$filter",
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
    }

    /**
     * Request with the current token, renewing once on `401`.
     *
     * A token can be rejected while still looking valid (clock skew, revoked
     * session), so renew and retry with the new one before giving up.
     */
    private suspend fun authenticated(
        method: String,
        url: String,
        body: JsonElement? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement? {
        discovery.ensure()
        val token = auth.accessToken()
        val response = transport.send(method, url, body, token, discovery.publishableKey, extraHeaders)
        if (response.status.value == 401) {
            val renewed =
                runCatchingCancellable {
                    auth.refresh(force = true)
                    auth.session?.accessToken
                }.getOrNull()
            if (!renewed.isNullOrEmpty() && renewed != token) {
                return transport.request(method, url, body, renewed, discovery.publishableKey, extraHeaders)
            }
        }
        return transport.decode(response, url)
    }
}
