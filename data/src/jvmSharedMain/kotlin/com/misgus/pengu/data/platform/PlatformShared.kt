package com.misgus.pengu.data.platform

import kotlinx.coroutines.Dispatchers

// The JVM and Android targets share these, so they are declared once.

actual val ioDispatcher = Dispatchers.IO

actual fun readTextFile(path: String): String? = PlatformFileIO.read(path)

actual fun writeTextFile(
    path: String,
    text: String,
) = PlatformFileIO.write(path, text)

actual fun deleteFile(path: String) = PlatformFileIO.delete(path)
