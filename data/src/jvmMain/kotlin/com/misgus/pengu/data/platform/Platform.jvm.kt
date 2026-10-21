package com.misgus.pengu.data.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale

actual val ioDispatcher = Dispatchers.IO

actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()

actual fun createHttpClient(): HttpClient =
    HttpClient(CIO) {
        installHttpDefaults()
    }

/**
 * Addon traffic runs on OkHttp, like the phones.
 *
 * CIO is HTTP/1.1 only, so a cold Home opened one TCP+TLS connection per
 * catalog — several of them usually live on the same host. OkHttp multiplexes
 * them over HTTP/2 and, with [IPv4FirstDns], does not wait on a broken AAAA
 * record before trying IPv4. Backend and TMDB traffic keeps CIO.
 */
actual fun createAddonHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                dns(IPv4FirstDns())
                // Public addons are reached directly: a system proxy that only
                // knows a corporate network would just fail every request.
                proxy(Proxy.NO_PROXY)
            }
        }
        installHttpDefaults(cacheStorage = diskCacheStorage())
    }

actual fun configDirectory(): String {
    val env = System.getenv()
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val base =
        if (isWindows) {
            env["APPDATA"] ?: env["LOCALAPPDATA"] ?: "."
        } else {
            env["XDG_CONFIG_HOME"] ?: "${env["HOME"] ?: "."}/.config"
        }
    return File(base, "Pengu").path
}

actual fun cacheDirectory(): String {
    val env = System.getenv()
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    // A cache must not live under `%APPDATA%`: Windows syncs that directory
    // across machines, and a few megabytes of catalog JSON are not worth a
    // profile round-trip.
    val base =
        if (isWindows) {
            env["LOCALAPPDATA"] ?: env["APPDATA"] ?: "."
        } else {
            env["XDG_CACHE_HOME"] ?: "${env["HOME"] ?: "."}/.cache"
        }
    return File(base, "Pengu").path
}

actual fun readTextFile(path: String): String? = PlatformFileIO.read(path)

actual fun writeTextFile(
    path: String,
    text: String,
) = PlatformFileIO.write(path, text)

actual fun deleteFile(path: String) = PlatformFileIO.delete(path)

actual fun hardenFilePermissions(path: String) {
    val file = File(path)
    if (!file.exists()) return
    runCatching {
        // Only meaningful on POSIX filesystems; on Windows this is a no-op.
        val view = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)
        if (view != null) {
            Files.setPosixFilePermissions(
                file.toPath(),
                PosixFilePermissions.fromString("rw-------"),
            )
        }
    }
}
