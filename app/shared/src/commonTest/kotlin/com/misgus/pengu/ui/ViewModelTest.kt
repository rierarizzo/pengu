package com.misgus.pengu.ui

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.ui.home.HomeViewModel
import com.misgus.pengu.ui.library.LibraryViewModel
import com.misgus.pengu.ui.support.FakeAccountRepository
import com.misgus.pengu.ui.support.FakeCatalogRepository
import com.misgus.pengu.ui.support.FakeLibraryRepository
import com.misgus.pengu.ui.support.FakeMetadataRepository
import com.misgus.pengu.ui.support.FakeProgressRepository
import com.misgus.pengu.ui.support.FakeTmdbRepository
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun item(
        id: String,
        type: String,
    ) = LibraryItem(id = id, contentId = id, contentType = ContentType(type), name = id)

    private fun viewModel(library: FakeLibraryRepository): LibraryViewModel {
        val account = FakeAccountRepository()
        val progress = FakeProgressRepository()
        val badges = WatchedBadgeResolver(progress, FakeMetadataRepository())
        return LibraryViewModel(account, library, progress, badges)
    }

    @Test
    fun loadsItems() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    FakeLibraryRepository(listOf(item("m1", "movie"), item("s1", "series"))),
                )
            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.hasLoaded)
            assertEquals(2, viewModel.state.value.items.size)
        }

    @Test
    fun clearsWhenSignedOut() =
        runTest(dispatcher) {
            val account = FakeAccountRepository()
            val viewModel =
                LibraryViewModel(
                    account,
                    FakeLibraryRepository(listOf(item("m1", "movie"))),
                    FakeProgressRepository(),
                    WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.items.size)

            account.signOut()
            advanceUntilIdle()
            assertEquals(0, viewModel.state.value.items.size)
        }
}

class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsCatalogRowsAndFeatured() =
        runTest(dispatcher) {
            val ref =
                CatalogRef(
                    addonName = "Addon",
                    addonBaseUrl = "https://addon",
                    type = ContentType("movie"),
                    id = "top",
                    name = "Top",
                )
            val preview =
                MetaPreview(
                    id = "tt1",
                    type = ContentType("movie"),
                    name = "Movie",
                )
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview))),
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.hasLoaded)
            assertEquals(1, viewModel.state.value.rows.size)
            assertEquals(
                "tt1",
                viewModel.state.value.featured
                    .single()
                    .id,
            )
        }

    @Test
    fun keepsARowForEveryBrowsableCatalog() =
        runTest(dispatcher) {
            val refs =
                (1..7).map { index ->
                    CatalogRef(
                        addonName = "Addon",
                        addonBaseUrl = "https://addon",
                        type = ContentType("movie"),
                        id = "top$index",
                        name = "Top $index",
                    )
                }
            val items =
                refs.associate { ref ->
                    ref.key to listOf(MetaPreview(id = ref.id, type = ContentType("movie"), name = ref.name))
                }
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(refs, items),
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            assertEquals(7, viewModel.state.value.rows.size)
        }

    @Test
    fun publishesEachRowWhileTheSlowestCatalogIsStillLoading() =
        runTest(dispatcher) {
            val slow =
                CatalogRef(
                    addonName = "Addon",
                    addonBaseUrl = "https://addon",
                    type = ContentType("movie"),
                    id = "slow",
                    name = "Slow",
                )
            val fast =
                CatalogRef(
                    addonName = "Addon",
                    addonBaseUrl = "https://addon",
                    type = ContentType("movie"),
                    id = "fast",
                    name = "Fast",
                )
            val gate = CompletableDeferred<Unit>()
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs =
                        FakeCatalogRepository(
                            refs = listOf(slow, fast),
                            items =
                                mapOf(
                                    slow.key to listOf(MetaPreview(id = "tt-slow", type = ContentType("movie"), name = "Slow")),
                                    fast.key to listOf(MetaPreview(id = "tt-fast", type = ContentType("movie"), name = "Fast")),
                                ),
                            gates = mapOf(slow.key to gate),
                        ),
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            // The fast catalog is on screen, and already leads the billboard,
            // while the slow one has not answered.
            assertEquals(
                listOf("fast"),
                viewModel.state.value.rows
                    .map { it.ref.id },
            )
            assertEquals(
                "tt-fast",
                viewModel.state.value.featured
                    .single()
                    .id,
            )
            assertTrue(viewModel.state.value.isLoading)

            gate.complete(Unit)
            advanceUntilIdle()

            // Rows keep the catalog order even though they arrived out of order.
            assertEquals(
                listOf("slow", "fast"),
                viewModel.state.value.rows
                    .map { it.ref.id },
            )
            assertTrue(viewModel.state.value.hasLoaded)
        }

    @Test
    fun fillsTheFeaturedArtworkFromTmdb() =
        runTest(dispatcher) {
            val ref =
                CatalogRef(
                    addonName = "Addon",
                    addonBaseUrl = "https://addon",
                    type = ContentType("movie"),
                    id = "top",
                    name = "Top",
                )
            val preview = MetaPreview(id = "tt1", type = ContentType("movie"), name = "Movie")
            val tmdb =
                TmdbTitle(
                    tmdbId = 1,
                    mediaType = TmdbMediaType.MOVIE,
                    title = "Movie",
                    backdrop = "https://img/backdrop.jpg",
                    textlessPoster = "https://img/textless.jpg",
                )
            val repository = FakeTmdbRepository(titles = mapOf("tt1" to tmdb))
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview))),
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                    tmdb = repository,
                )
            viewModel.load()
            advanceUntilIdle()

            val featured =
                viewModel.state.value.featured
                    .single()
            assertEquals("https://img/backdrop.jpg", featured.background)
            assertEquals("https://img/textless.jpg", featured.textlessPoster)
        }

    @Test
    fun doesNotQueryTmdbWhenItIsNotConfigured() =
        runTest(dispatcher) {
            val ref =
                CatalogRef(
                    addonName = "Addon",
                    addonBaseUrl = "https://addon",
                    type = ContentType("movie"),
                    id = "top",
                    name = "Top",
                )
            val preview = MetaPreview(id = "tt1", type = ContentType("movie"), name = "Movie")
            val repository = FakeTmdbRepository(isConfigured = false)
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview))),
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                    tmdb = repository,
                )
            viewModel.load()
            advanceUntilIdle()

            assertEquals(0, repository.lookups)
        }
}
