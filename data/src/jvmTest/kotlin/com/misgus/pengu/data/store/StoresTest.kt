package com.misgus.pengu.data.store

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.watched.CachedSeriesState
import com.misgus.pengu.data.local.LocalLibraryRepository
import com.misgus.pengu.data.local.LocalProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StoresTest {
    private fun tempDir(): File =
        File.createTempFile("pengu", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    @Test
    fun jsonFileStoreRoundTrips() =
        runTest {
            val path = File(tempDir(), "library.json").path
            val store = JsonFileStore(path)
            val row =
                kotlinx.serialization.json.JsonObject(
                    mapOf("content_id" to kotlinx.serialization.json.JsonPrimitive("tt1")),
                )
            store.write(listOf(row))
            assertEquals(listOf(row), store.read())
        }

    @Test
    fun jsonFileStoreDegradesToEmptyOnCorruptFile() =
        runTest {
            val file = File(tempDir(), "broken.json")
            file.writeText("not json")
            assertTrue(JsonFileStore(file.path).read().isEmpty())
        }

    @Test
    fun jsonMapStoreRoundTrips() =
        runTest {
            val path = File(tempDir(), "map.json").path
            val store = JsonMapStore(path)
            store.write(
                kotlinx.serialization.json.JsonObject(
                    mapOf("tt1" to kotlinx.serialization.json.JsonPrimitive(9)),
                ),
            )
            assertEquals("9", (store.read()["tt1"] as kotlinx.serialization.json.JsonPrimitive).content)
        }

    @Test
    fun watchedSeriesCacheStoresByProfile() =
        runTest {
            val path = File(tempDir(), "watched_series.json").path
            val cache = FileWatchedSeriesCache(path)
            assertNull(cache.read("profile-1", "tt1"))
            cache.writeAll("profile-1", mapOf("tt1" to CachedSeriesState("sig", true)))
            assertEquals(CachedSeriesState("sig", true), cache.read("profile-1", "tt1"))
            cache.clear("profile-1")
            assertNull(cache.read("profile-1", "tt1"))
        }

    @Test
    fun localLibraryRepositoryAddsAndRemoves() =
        runTest {
            val path = File(tempDir(), "library.json").path
            val repository = LocalLibraryRepository(JsonFileStore(path))
            val item = LibraryItem(id = "", contentId = "tt1", contentType = ContentType("movie"), name = "Movie")
            repository.add(item)
            repository.add(item)
            assertEquals(1, repository.all().size)
            assertTrue(repository.contains("tt1"))
            repository.remove("tt1")
            assertFalse(repository.contains("tt1"))
        }

    @Test
    fun localLibraryHandlesConcurrentAdds() =
        runTest {
            val path = File(tempDir(), "library.json").path
            val repository = LocalLibraryRepository(JsonFileStore(path))
            (1..20)
                .map { index ->
                    async(Dispatchers.Default) {
                        repository.add(
                            LibraryItem(id = "", contentId = "tt$index", contentType = ContentType("movie"), name = "M$index"),
                        )
                    }
                }.awaitAll()
            assertEquals(20, repository.all().size)
        }

    @Test
    fun writeFailuresAreNotSwallowed() =
        runTest {
            val dir = tempDir()
            // A file in the place of the parent directory makes the write fail.
            val blocker = File(dir, "blocker").apply { writeText("x") }
            val store = JsonFileStore(File(blocker, "library.json").path)
            assertFailsWith<IOException> {
                store.write(listOf(JsonObject(mapOf("content_id" to JsonPrimitive("tt1")))))
            }
        }

    @Test
    fun localProgressRepositorySavesAndMarksWatched() =
        runTest {
            val dir = tempDir()
            val repository =
                LocalProgressRepository(
                    store = JsonFileStore(File(dir, "progress.json").path),
                    watched = JsonFileStore(File(dir, "watched.json").path),
                )
            repository.save(
                WatchProgress(
                    id = "",
                    contentId = "tt1",
                    contentType = ContentType("movie"),
                    position = 55.minutes,
                    duration = 60.minutes,
                    lastWatched = Instant.parse("2024-01-01T00:00:00Z"),
                    progressKey = "tt1",
                ),
            )
            assertEquals(1, repository.all().size)
            // Completed progress is reported as watched.
            assertTrue(repository.watchedEntries(listOf("tt1")).isNotEmpty())

            repository.markWatched("tt2", ContentType("movie"))
            assertTrue(repository.watchedEntries(listOf("tt2")).any { it.isMarker })
            repository.unmarkWatched("tt2")
            assertTrue(repository.watchedEntries(listOf("tt2")).isEmpty())

            repository.remove("tt1")
            assertTrue(repository.all().isEmpty())
        }
}
