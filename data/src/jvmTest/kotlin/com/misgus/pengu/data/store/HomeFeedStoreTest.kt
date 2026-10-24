package com.misgus.pengu.data.store

import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRow
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The on-disk Home feed the app paints before the addons answer. */
class HomeFeedStoreTest {
    private fun tempDir(): File =
        File.createTempFile("pengu", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    private val ref =
        CatalogRef(
            addonName = "Addon",
            addonBaseUrl = "https://addon",
            type = ContentType("series"),
            id = "top",
            name = "Top",
            extras = listOf(AddonCatalogExtra(name = "genre", options = listOf("Drama"))),
        )

    private val item =
        MetaPreview(
            id = "tt1",
            type = ContentType("series"),
            name = "Show",
            poster = "https://addon/poster.jpg",
            background = "https://addon/background.jpg",
            logo = "https://addon/logo.svg",
            description = "A show",
            releaseInfo = "2024",
            imdbRating = 8.1,
            genres = listOf("Drama"),
        )

    @Test
    fun feedIsStoredPerProfile() =
        runTest {
            val store = FileHomeFeedStore(File(tempDir(), "home_feed.json").path)

            assertNull(store.read("p1"))
            store.write("p1", listOf(CatalogRow(ref, ref.name, listOf(item))))

            assertEquals(listOf(CatalogRow(ref, ref.name, listOf(item))), store.read("p1"))
            assertNull(store.read("p2"))
        }

    @Test
    fun feedDegradesToNothingOnACorruptFile() =
        runTest {
            val file = File(tempDir(), "home_feed.json")
            file.writeText("not json")

            assertNull(FileHomeFeedStore(file.path).read("p1"))
        }
}
