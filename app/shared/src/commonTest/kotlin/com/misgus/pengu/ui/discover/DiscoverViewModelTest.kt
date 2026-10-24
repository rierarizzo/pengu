package com.misgus.pengu.ui.discover

import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.ui.support.FakeAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun ref(
        type: String,
        id: String,
        addon: String = "addon",
        extras: List<AddonCatalogExtra> = emptyList(),
    ) = CatalogRef(
        addonName = addon,
        addonBaseUrl = "https://$addon",
        type = ContentType(type),
        id = id,
        name = id,
        extras = extras,
    )

    private fun preview(
        id: String,
        type: String = "movie",
    ) = MetaPreview(id = id, type = ContentType(type), name = id)

    /** Answers each catalog with its items and records the extras it was asked with. */
    private class RecordingCatalogRepository(
        private val refs: List<CatalogRef>,
        private val items: Map<String, List<MetaPreview>>,
        private val pages: Map<String, List<List<MetaPreview>>> = emptyMap(),
    ) : CatalogRepository {
        val calls = mutableListOf<Pair<String, Map<String, String>?>>()

        override suspend fun catalogs(): List<CatalogRef> = refs

        override suspend fun items(
            ref: CatalogRef,
            extra: Map<String, String>?,
        ): List<MetaPreview> {
            calls += ref.key to extra
            val paged = pages[ref.key]
            if (paged != null) {
                val skip = extra?.get("skip")?.toIntOrNull() ?: 0
                // Pages of two items each, so `skip` is a page offset.
                return paged.getOrNull(skip / 2).orEmpty()
            }
            return items[ref.key].orEmpty()
        }
    }

    @Test
    fun offersTheFirstDiscoverCatalogOfTheFirstType() =
        runTest(dispatcher) {
            val series = ref("series", "top_series", addon = "A")
            val movie = ref("movie", "top_movie", addon = "A")
            val repo =
                RecordingCatalogRepository(
                    refs = listOf(series, movie),
                    items = mapOf(series.key to listOf(preview("s1", "series")), movie.key to listOf(preview("m1"))),
                )
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf(ContentType("movie"), ContentType("series")), state.types)
            assertEquals(ContentType("movie"), state.selectedType)
            assertEquals(listOf("top_movie"), state.catalogs.map { it.id })
            // Only the selected catalog is asked, and with no extras.
            assertEquals(listOf<Pair<String, Map<String, String>?>>(movie.key to null), repo.calls)
            assertEquals(listOf("m1"), state.items.map { it.id })
        }

    @Test
    fun neverOffersASearchOnlyCatalog() =
        runTest(dispatcher) {
            val searchOnly = ref("movie", "search", extras = listOf(AddonCatalogExtra("search", isRequired = true)))
            val browsable = ref("movie", "top")
            val repo =
                RecordingCatalogRepository(
                    refs = listOf(searchOnly, browsable),
                    items = mapOf(browsable.key to listOf(preview("m1"))),
                )
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(
                listOf("top"),
                viewModel.state.value.catalogs
                    .map { it.id },
            )
            assertEquals(
                listOf("m1"),
                viewModel.state.value.items
                    .map { it.id },
            )
        }

    @Test
    fun switchingTheTypeShowsItsOwnCatalogsAndFirstPage() =
        runTest(dispatcher) {
            val movie = ref("movie", "top_movie")
            val series = ref("series", "top_series")
            val repo =
                RecordingCatalogRepository(
                    refs = listOf(movie, series),
                    items = mapOf(movie.key to listOf(preview("m1")), series.key to listOf(preview("s1", "series"))),
                )
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            viewModel.selectType(ContentType("series"))
            advanceUntilIdle()

            assertEquals(ContentType("series"), viewModel.state.value.selectedType)
            assertEquals(
                listOf("top_series"),
                viewModel.state.value.catalogs
                    .map { it.id },
            )
            assertEquals(
                listOf("s1"),
                viewModel.state.value.items
                    .map { it.id },
            )
        }

    @Test
    fun aGenreSelectionIsSentToTheCatalog() =
        runTest(dispatcher) {
            val catalog =
                ref(
                    "movie",
                    "top",
                    extras = listOf(AddonCatalogExtra("genre", options = listOf("Action", "Drama")), AddonCatalogExtra("skip")),
                )
            val repo = RecordingCatalogRepository(listOf(catalog), mapOf(catalog.key to listOf(preview("m1"))))
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(listOf("Action", "Drama"), viewModel.state.value.genres)
            assertNull(viewModel.state.value.selectedGenre)

            viewModel.selectGenre("Drama")
            advanceUntilIdle()

            assertEquals("Drama", viewModel.state.value.selectedGenre)
            assertEquals(mapOf("genre" to "Drama"), repo.calls.last().second)
        }

    @Test
    fun aRequiredGenreIsPickedAndAlwaysSent() =
        runTest(dispatcher) {
            val catalog =
                ref(
                    "movie",
                    "top",
                    extras =
                        listOf(
                            AddonCatalogExtra("genre", isRequired = true, options = listOf("Action", "Drama")),
                            AddonCatalogExtra("skip"),
                        ),
                )
            val repo = RecordingCatalogRepository(listOf(catalog), mapOf(catalog.key to listOf(preview("m1"))))
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.genreRequired)
            assertEquals("Action", viewModel.state.value.selectedGenre)
            assertEquals(mapOf("genre" to "Action"), repo.calls.single().second)
        }

    @Test
    fun pagesTheSelectedCatalogUntilItIsExhausted() =
        runTest(dispatcher) {
            val catalog = ref("movie", "top", extras = listOf(AddonCatalogExtra("skip")))
            val repo =
                RecordingCatalogRepository(
                    refs = listOf(catalog),
                    items = emptyMap(),
                    pages =
                        mapOf(
                            catalog.key to
                                listOf(
                                    listOf(preview("m1"), preview("m2")),
                                    listOf(preview("m3"), preview("m4")),
                                ),
                        ),
                )
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(
                listOf("m1", "m2"),
                viewModel.state.value.items
                    .map { it.id },
            )
            assertTrue(viewModel.state.value.hasMore)

            viewModel.loadMore()
            advanceUntilIdle()

            // The next page is asked with the offset of what is already on screen.
            assertEquals(mapOf("skip" to "2"), repo.calls.last().second)
            assertEquals(
                listOf("m1", "m2", "m3", "m4"),
                viewModel.state.value.items
                    .map { it.id },
            )

            viewModel.loadMore()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.hasMore)
        }

    @Test
    fun aCatalogWithoutSkipDoesNotPage() =
        runTest(dispatcher) {
            val catalog = ref("movie", "top")
            val repo = RecordingCatalogRepository(listOf(catalog), mapOf(catalog.key to listOf(preview("m1"))))
            val viewModel = DiscoverViewModel(FakeAccountRepository(), repo)
            viewModel.load()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasMore)
        }
}
