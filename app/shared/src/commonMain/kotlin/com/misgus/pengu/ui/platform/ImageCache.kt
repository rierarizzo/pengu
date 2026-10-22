package com.misgus.pengu.ui.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drops every image the app has cached: the bitmaps in memory and the decoded
 * posters, backdrops and logos on disk.
 *
 * The next paint fetches them again. That is what a user reaches for when an
 * addon replaced an artwork at the same URL, or when the cache has grown and the
 * old images are no longer worth their space.
 *
 * Runs off the main thread: the disk half deletes files.
 */
suspend fun clearImageCache(context: PlatformContext) =
    withContext(Dispatchers.Default) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
