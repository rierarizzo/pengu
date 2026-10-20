package com.misgus.pengu.ui

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.ui.home.HomeViewModel
import com.misgus.pengu.ui.library.LibraryViewModel
import com.misgus.pengu.ui.services.PlaybackUpdates
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
    fun publishesTheFirstBatchBeforeTheRestIsLoaded() =
        runTest(dispatcher) {
            val refs =
                (0 until 6).map { index ->
                    CatalogRef(
                        addonName = "Addon",
                        addonBaseUrl = "https://addon",
                        type = ContentType("movie"),
                        id = "c$index",
                        name = "C$index",
                    )
                }
            val items =
                refs.associate { ref ->
                    ref.key to listOf(MetaPreview(id = ref.id, type = ContentType("movie"), name = ref.name))
                }
            // The second batch (c4, c5) is held; the first (c0..c3) is free.
            val gate = CompletableDeferred<Unit>()
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs =
                        FakeCatalogRepository(
                            refs = refs,
                            items = items,
                            gates = mapOf(refs[4].key to gate, refs[5].key to gate),
                        ),
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            // The first batch is on screen, and already leads the billboard, while
            // the second is still loading.
            assertEquals(
                listOf("c0", "c1", "c2", "c3"),
                viewModel.state.value.rows
                    .map { it.ref.id },
            )
            assertTrue(
                viewModel.state.value.featured
                    .isNotEmpty(),
            )
            assertTrue(viewModel.state.value.isLoading)

            gate.complete(Unit)
            advanceUntilIdle()

            // Rows keep the catalog order once every batch is in.
            assertEquals(
                listOf("c0", "c1", "c2", "c3", "c4", "c5"),
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

    @Test
    fun repaintsContinueWatchingWhenAPlaybackCloses() =
        runTest(dispatcher) {
            val progress = FakeProgressRepository()
            val updates = PlaybackUpdates()
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(),
                    library = FakeLibraryRepository(listOf(item("m1", "movie"))),
                    progress = progress,
                    badges = WatchedBadgeResolver(progress, FakeMetadataRepository()),
                    playbackUpdates = updates,
                )
            viewModel.load()
            advanceUntilIdle()
            val before = viewModel.state.value.continueWatching
            assertTrue(before.isEmpty())

            // The player persisted a mid-way position and closed.
            progress.save(
                WatchProgress(
                    id = "p1",
                    contentId = "m1",
                    contentType = ContentType("movie"),
                    position = 30.minutes,
                    duration = 100.minutes,
                    lastWatched = Instant.fromEpochMilliseconds(1),
                ),
            )
            updates.notifyClosed()
            advanceUntilIdle()

            val row = viewModel.state.value.continueWatching
            assertEquals(listOf("m1"), row.map { it.item.contentId })
        }

    @Test
    fun collapsesContinueWatchingToOneRowPerTitle() =
        runTest(dispatcher) {
            val progress =
                FakeProgressRepository(
                    progress =
                        listOf(
                            watchProgress("s1", lastWatched = 1, season = 1, episode = 7),
                            watchProgress("s1", lastWatched = 5, season = 1, episode = 10),
                            watchProgress("m1", lastWatched = 3),
                        ),
                )
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(),
                    library = FakeLibraryRepository(listOf(item("s1", "series"), item("m1", "movie"))),
                    progress = progress,
                    badges = WatchedBadgeResolver(progress, FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            val row = viewModel.state.value.continueWatching
            // Newest title first, and the series only keeps the episode it is in
            // the middle of.
            assertEquals(listOf("s1", "m1"), row.map { it.item.contentId })
            val newest = row.first()
            assertEquals(10, newest.progress.episode?.number)
        }

    @Test
    fun dropsASeriesWhoseLatestEpisodeIsFinished() =
        runTest(dispatcher) {
            val progress =
                FakeProgressRepository(
                    progress =
                        listOf(
                            watchProgress("s1", lastWatched = 1, season = 1, episode = 3),
                            watchProgress("s1", lastWatched = 5, position = 96, season = 1, episode = 4),
                        ),
                )
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(),
                    library = FakeLibraryRepository(listOf(item("s1", "series"))),
                    progress = progress,
                    badges = WatchedBadgeResolver(progress, FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            val row = viewModel.state.value.continueWatching
            assertTrue(row.isEmpty())
        }

    private fun watchProgress(
        contentId: String,
        lastWatched: Long,
        position: Int = 30,
        season: Int? = null,
        episode: Int? = null,
    ) = WatchProgress(
        id = "$contentId-$season-$episode-$lastWatched",
        contentId = contentId,
        contentType = ContentType(if (season == null) "movie" else "series"),
        season = season?.let(::Season),
        episode = episode?.let(::Episode),
        position = position.minutes,
        duration = 100.minutes,
        lastWatched = Instant.fromEpochMilliseconds(lastWatched),
    )

    private fun item(
        contentId: String,
        type: String,
    ) = LibraryItem(id = contentId, contentId = contentId, contentType = ContentType(type), name = contentId)
}
