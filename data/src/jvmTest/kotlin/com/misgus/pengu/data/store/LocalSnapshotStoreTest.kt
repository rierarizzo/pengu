package com.misgus.pengu.data.store

import com.misgus.pengu.core.model.Addon
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The on-disk addon list the app answers from before the backend does. */
class LocalSnapshotStoreTest {
    private fun tempDir(): File =
        File.createTempFile("pengu", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    private val addon =
        Addon(
            id = "a1",
            url = "https://addon/manifest.json",
            name = "Addon",
            enabled = true,
            sortOrder = 2,
            profileId = 7,
        )

    @Test
    fun addonSnapshotIsStoredPerProfile() =
        runTest {
            val store = FileAddonSnapshotStore(File(tempDir(), "addons.json").path)

            assertNull(store.read("p1"))
            store.write("p1", listOf(addon))

            assertEquals(listOf(addon), store.read("p1"))
            assertNull(store.read("p2"))
        }

    @Test
    fun addonSnapshotDegradesToNothingOnACorruptFile() =
        runTest {
            val file = File(tempDir(), "addons.json")
            file.writeText("not json")

            assertNull(FileAddonSnapshotStore(file.path).read("p1"))
        }
}
