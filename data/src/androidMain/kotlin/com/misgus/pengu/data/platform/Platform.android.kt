package com.misgus.pengu.data.platform

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import java.io.File
import java.net.Proxy

private var applicationContext: Context? = null

/**
 * Wires the platform layer to the app [context].
 *
 * Called once from the Android entry point; without it the app falls back to a
 * temporary directory, which does not survive a process restart.
 */
fun initPlatform(context: Context) {
    applicationContext = context.applicationContext
}

actual fun createHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        configureEngine()
        installHttpDefaults()
    }

actual fun createAddonHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        configureEngine()
        installHttpDefaults(cacheStorage = diskCacheStorage())
    }

/** Android-only engine tuning, shared by both clients. */
private fun HttpClientConfig<OkHttpConfig>.configureEngine() {
    engine {
        config {
            // Some mobile networks and emulators answer with a broken IPv6 route
            // first, and the connect only falls back to IPv4 when that times out.
            // Ordering IPv4 first removes the stall without dropping IPv6 that
            // does work.
            dns(IPv4FirstDns())
            // The app talks to public addons directly; a system proxy that only
            // knows the corporate network would just fail every request.
            proxy(Proxy.NO_PROXY)
        }
    }
}

actual fun configDirectory(): String {
    val context =
        applicationContext
            ?: return File(System.getProperty("java.io.tmpdir") ?: ".", "Pengu").path
    return File(context.filesDir, "Pengu").path
}

actual fun cacheDirectory(): String {
    // `cacheDir` is the directory Android is allowed to empty when the device
    // runs out of space, which is exactly the contract a disposable cache wants.
    val context =
        applicationContext
            ?: return File(System.getProperty("java.io.tmpdir") ?: ".", "Pengu").path
    return File(context.cacheDir, "Pengu").path
}

actual fun hardenFilePermissions(path: String) {
    val file = File(path)
    if (!file.exists()) return
    runCatching {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }
}
