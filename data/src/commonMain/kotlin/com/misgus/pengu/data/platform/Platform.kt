package com.misgus.pengu.data.platform

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher

/** Dispatcher for blocking file I/O. */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Network timeouts shared by every platform engine.
 *
 * A request that never answers must not leave the UI loading forever, so the
 * engines are configured with finite request, connect and socket timeouts.
 */
const val HTTP_REQUEST_TIMEOUT_MS = 30_000L
const val HTTP_CONNECT_TIMEOUT_MS = 15_000L
const val HTTP_SOCKET_TIMEOUT_MS = 30_000L

/** Builds the platform HTTP client (CIO on desktop, OkHttp on Android). */
expect fun createHttpClient(): HttpClient

/**
 * Builds the client used for addon traffic, with an on-disk response cache.
 *
 * Addon responses (manifests, catalogs, metas) are the app's bulk download and
 * the ones that are safe to reuse: a catalog page is the same for every caller
 * and expires on its own schedule. The backend client deliberately has no cache,
 * because a stale `library_items` or `watch_progress` read would show the user
 * data they just changed.
 */
expect fun createAddonHttpClient(): HttpClient

/** Name of the subdirectory holding cached HTTP responses. */
internal const val HTTP_CACHE_DIR = "http"

/**
 * BCP-47 tag of the current system locale, e.g. `es-ES`.
 *
 * Used to pick the language TMDB answers in; the app falls back to English when
 * the locale cannot be read.
 */
expect fun systemLanguageTag(): String

/** `%APPDATA%\Pengu` on Windows, XDG config elsewhere. */
expect fun configDirectory(): String

/**
 * Directory for data that can be rebuilt: the HTTP cache, decoded artwork.
 *
 * Kept apart from [configDirectory] because a cache is disposable and, on
 * Windows, must not end up inside a roaming profile.
 */
expect fun cacheDirectory(): String

/**
 * Decodes a `gzip` response body.
 *
 * The engines do not ask for compression on their own, so the addon client sets
 * `Accept-Encoding: gzip` itself and decodes the body here: catalog JSON shrinks
 * about fivefold, which is what makes a cold Home paint cheap. `java.util.zip`
 * is available on both targets, so the JVM and Android share the implementation.
 */
expect fun gzipDecode(bytes: ByteArray): ByteArray

/** Reads [path] as UTF-8, or `null` when it does not exist / cannot be read. */
expect fun readTextFile(path: String): String?

/** Writes [text] to [path] as UTF-8, creating parent directories. */
expect fun writeTextFile(
    path: String,
    text: String,
)

/**
 * Restricts [path] to the current user where the platform supports it.
 *
 * Best-effort hardening for the session file: on Windows this is a no-op, and
 * it is not a replacement for an OS keychain.
 */
expect fun hardenFilePermissions(path: String)

/** Deletes [path] when it exists. */
expect fun deleteFile(path: String)
