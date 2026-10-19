package com.misgus.pengu.data.store

import com.misgus.pengu.core.catalog.HomeSnapshot
import com.misgus.pengu.core.catalog.HomeSnapshotRow
import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The two on-disk snapshots the app paints from before the network answers. */
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

    private val ref =
        CatalogRef(
            addonName = "Addon",
            addonBaseUrl = "https://addon",
            type = ContentType("movie"),
            id = "top",
            name = "Top",
            extras = listOf(AddonCatalogExtra(name = "skip", isRequired = false, options = listOf("0", "100"))),
        )

    private val preview =
        MetaPreview(
            id = "tt1",
            type = ContentType("movie"),
            name = "Movie",
            poster = "https://img/poster.jpg",
            posterShape = "poster",
            background = "https://img/backdrop.jpg",
            logo = "https://img/logo.png",
            textlessPoster = "https://img/textless.jpg",
            description = "A synopsis.",
            releaseInfo = "2024",
            imdbRating = 8.4,
            genres = listOf("Action", "Drama"),
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

    @Test
    fun homeSnapshotRoundTripsEveryPaintedField() =
        runTest {
            val store = FileHomeSnapshotStore(File(tempDir(), "home.json").path)
            val snapshot = HomeSnapshot(rows = listOf(HomeSnapshotRow(ref, listOf(preview))), featured = listOf(preview))

            store.write("p1", snapshot)

            assertEquals(snapshot, store.read("p1"))
        }

    @Test
    fun homeSnapshotWithoutRowsIsNothingToPaint() =
        runTest {
            val store = FileHomeSnapshotStore(File(tempDir(), "home.json").path)

            assertEquals(HomeSnapshot(emptyList()), store.read("p1") ?: HomeSnapshot(emptyList()))
            store.write("p1", HomeSnapshot(emptyList(), listOf(preview)))
            assertNull(store.read("p1"))
        }
}
