# ProGuard rules for the desktop release image.
#
# The Compose plugin already applies its own rules (Kotlin, Skiko, Compose,
# coroutines) and `-dontobfuscate`, so this file only covers what the app's own
# dependencies reference and never bundle: ProGuard treats a missing reference as
# an error, and every one of these is an optional integration the library probes
# at runtime and falls back from.

# ProGuard's optimizer rewrote Okio's inline `buffer()` to return a
# `RealBufferedSource` where the caller expected a `BufferedSource`, and the JVM
# rejected the class with a VerifyError the first time OkHttp connected: no
# addon request, so no catalogs and no images. Shrinking still runs; only the
# bytecode rewriting is off.
-dontoptimize

# OkHttp looks for these TLS providers on the classpath and uses the JDK's
# default when none is there.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ... and it registers a GraalVM native-image feature when it is compiled with it.
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.**

# Logback ships optional servlet and mail integrations that a desktop app never
# loads.
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**
-dontwarn ch.qos.logback.**

# Ktor's logger probes the Android runtime to pick its logger.
-dontnote io.ktor.util.logging.**

# JNA resolves its native methods by name and reads the structures by
# reflection; shrinking or renaming any of that breaks the player at startup
# (`Can't obtain static method dispose from class com.sun.jna.Native`).
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keepclassmembers class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**

# Logback is loaded through SLF4J's service loader, so nothing references it
# statically and ProGuard would shrink the provider away.
-keep class ch.qos.logback.** { *; }

# mpv calls back into these classes from native code (log sink, handle), so
# nothing in the bytecode references them and shrinking removes them.
-keep class org.openani.mediamp.** { *; }

# Service-loader entry points. `META-INF/services` names them as text, so no
# bytecode references them and shrinking deleted them: Coil then had no network
# fetcher (every poster stayed blank) and no SVG decoder, and Ktor could not
# discover its engines. Same rules the libraries ship for R8 themselves.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
