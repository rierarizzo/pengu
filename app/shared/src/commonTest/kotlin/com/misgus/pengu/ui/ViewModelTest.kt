package com.misgus.pengu.ui

import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.repository.AccountState
import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.CatalogRepository
import com.misgus.pengu.core.repository.CatalogRow
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.watched.WatchedBadgeResolver
import com.misgus.pengu.ui.home.HomeViewModel
import com.misgus.pengu.ui.library.LibraryViewModel
import com.misgus.pengu.ui.services.PlaybackUpdates
import com.misgus.pengu.ui.support.FakeAccountRepository
import com.misgus.pengu.ui.support.FakeCatalogRepository
import com.misgus.pengu.ui.support.FakeLibraryRepository
import com.misgus.pengu.ui.support.FakeMetadataRepository
import com.misgus.pengu.ui.support.FakeProgressRepository
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
import kotlin.test.assertFalse
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
                    metadata = FakeMetadataRepository(),
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
    fun paintsTheCachedFeedBeforeTheCatalogsAnswer() =
        runTest(dispatcher) {
            val ref =
                CatalogRef(
                    addonName = "Addon",
                    addonBaseUrl = "https://addon",
                    type = ContentType("movie"),
                    id = "top",
                    name = "Top",
                )
            val cachedItem = MetaPreview(id = "cached", type = ContentType("movie"), name = "Cached")
            val freshItem = MetaPreview(id = "fresh", type = ContentType("movie"), name = "Fresh")
            val gate = CompletableDeferred<Unit>()
            val catalogs =
                FakeCatalogRepository(
                    refs = listOf(ref),
                    items = mapOf(ref.key to listOf(freshItem)),
                    gates = mapOf(ref.key to gate),
                    cached = listOf(CatalogRow(ref, ref.name, listOf(cachedItem))),
                )
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = catalogs,
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    metadata = FakeMetadataRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            // The addon has not answered, so the row on screen is the stored one.
            assertEquals(
                listOf("cached"),
                viewModel.state.value.rows
                    .single()
                    .items
                    .map { it.id },
            )
            assertTrue(viewModel.state.value.isLoading)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf("fresh"),
                viewModel.state.value.rows
                    .single()
                    .items
                    .map { it.id },
            )
            // And what the next cold start paints is the fresh feed, not the stale one.
            assertEquals(
                listOf("fresh"),
                catalogs.written
                    ?.single()
                    ?.items
                    ?.map { it.id },
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
                    metadata = FakeMetadataRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            assertEquals(7, viewModel.state.value.rows.size)
        }

    @Test
    fun waitsForEveryCatalogBeforePublishing() =
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
                    metadata = FakeMetadataRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            // Nothing half-loaded: the answered first batch stays off screen while
            // the rest is still in flight, so the loading state covers the whole load.
            assertTrue(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.rows
                    .isEmpty(),
            )
            assertTrue(
                viewModel.state.value.featured
                    .isEmpty(),
            )

            gate.complete(Unit)
            advanceUntilIdle()

            // Rows keep the catalog order once every batch is in.
            assertEquals(
                listOf("c0", "c1", "c2", "c3", "c4", "c5"),
                viewModel.state.value.rows
                    .map { it.ref.id },
            )
            assertTrue(viewModel.state.value.hasLoaded)
            assertFalse(viewModel.state.value.isLoading)
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
                    metadata = FakeMetadataRepository(),
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
    fun showsATitleThatWasNeverSaved() =
        runTest(dispatcher) {
            val progress = FakeProgressRepository(progress = listOf(watchProgress("tt9", lastWatched = 1)))
            val metadata =
                FakeMetadataRepository(
                    detail =
                        MetaDetail(
                            id = "tt9",
                            type = ContentType("movie"),
                            name = "Unsaved movie",
                            background = "https://img/backdrop.jpg",
                        ),
                )
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(),
                    library = FakeLibraryRepository(),
                    progress = progress,
                    metadata = metadata,
                    badges = WatchedBadgeResolver(progress, FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            // Watching something is enough to show it: the row is about what is in
            // progress, not about what the library saved.
            val row = viewModel.state.value.continueWatching
            val entry = row.first()
            assertEquals("tt9", entry.item.contentId)
            assertEquals("Unsaved movie", entry.item.name)
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
                    metadata = FakeMetadataRepository(),
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
                    metadata = FakeMetadataRepository(),
                    badges = WatchedBadgeResolver(progress, FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            val row = viewModel.state.value.continueWatching
            assertTrue(row.isEmpty())
        }

    @Test
    fun paintsTheFeedAndTheWatchHistoryTogether() =
        runTest(dispatcher) {
            val top = ref("top")
            val libraryGate = CompletableDeferred<Unit>()
            val viewModel =
                HomeViewModel(
                    account = FakeAccountRepository(),
                    catalogs = FakeCatalogRepository(listOf(top), mapOf(top.key to listOf(preview("tt1")))),
                    library = FakeLibraryRepository(listOf(item("tt1", "movie")), gate = libraryGate),
                    progress = FakeProgressRepository(progress = listOf(watchProgress("tt1", lastWatched = 1_000))),
                    metadata = FakeMetadataRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            viewModel.load()
            advanceUntilIdle()

            // The backend read is still in flight, so nothing is painted yet: the
            // first frame must not show the rows and then grow the watch row above
            // them.
            assertTrue(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.rows
                    .isEmpty(),
            )
            assertTrue(
                viewModel.state.value.continueWatching
                    .isEmpty(),
            )

            libraryGate.complete(Unit)
            advanceUntilIdle()

            // Both arrive in the same paint.
            assertEquals(
                listOf("tt1"),
                viewModel.state.value.rows
                    .single()
                    .items
                    .map(MetaPreview::id),
            )
            assertEquals(
                listOf("tt1"),
                viewModel.state.value.continueWatching
                    .map { it.item.contentId },
            )
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun aManualBillboardDrawsOnlyFromThePickedCatalogs() =
        runTest(dispatcher) {
            val picked = ref("picked")
            val other = ref("other")
            val viewModel =
                homeViewModel(
                    catalogs =
                        FakeCatalogRepository(
                            refs = listOf(picked, other),
                            items =
                                mapOf(
                                    picked.key to listOf(preview("tt-picked")),
                                    other.key to listOf(preview("tt-other")),
                                ),
                        ),
                    settings =
                        InMemorySettingsRepository(
                            AppSettings(heroSource = HeroSource.MANUAL, heroCatalogKeys = listOf(picked.key)),
                        ),
                )

            viewModel.load()
            advanceUntilIdle()

            // Every row still loads; only the billboard is narrowed.
            assertEquals(2, viewModel.state.value.rows.size)
            assertEquals(
                listOf("tt-picked"),
                viewModel.state.value.featured
                    .map(MetaPreview::id),
            )
        }

    @Test
    fun aManualBillboardFallsBackToEveryRowWhenItsCatalogsAreGone() =
        runTest(dispatcher) {
            val top = ref("top")
            val viewModel =
                homeViewModel(
                    catalogs = FakeCatalogRepository(listOf(top), mapOf(top.key to listOf(preview("tt1")))),
                    settings =
                        InMemorySettingsRepository(
                            AppSettings(
                                heroSource = HeroSource.MANUAL,
                                heroCatalogKeys = listOf("https://old|movie|gone"),
                            ),
                        ),
                )

            viewModel.load()
            advanceUntilIdle()

            assertEquals(
                listOf("tt1"),
                viewModel.state.value.featured
                    .map(MetaPreview::id),
            )
        }

    @Test
    fun changingTheSourceRePicksTheBillboardAtOnce() =
        runTest(dispatcher) {
            val picked = ref("picked")
            val other = ref("other")
            val settings = InMemorySettingsRepository()
            val viewModel =
                homeViewModel(
                    catalogs =
                        FakeCatalogRepository(
                            refs = listOf(picked, other),
                            items =
                                mapOf(
                                    picked.key to listOf(preview("tt-picked")),
                                    other.key to listOf(preview("tt-other")),
                                ),
                        ),
                    settings = settings,
                )

            viewModel.load()
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.featured
                    .any { it.id == "tt-other" },
            )

            settings.setHeroSource(HeroSource.MANUAL)
            settings.setHeroCatalogKeys(listOf(picked.key))
            advanceUntilIdle()

            // No reload and no timer: the deliberate change is applied at once.
            assertEquals(
                listOf("tt-picked"),
                viewModel.state.value.featured
                    .map(MetaPreview::id),
            )
        }

    @Test
    fun dropsTheRowsOfThePreviousProfileWhileTheNewOneLoads() =
        runTest(dispatcher) {
            val top = ref("top")
            val catalogs =
                SwappingCatalogRepository(
                    refs = listOf(top),
                    first = listOf(preview("tt-one")),
                    second = listOf(preview("tt-two")),
                )
            val account =
                FakeAccountRepository(AccountState(isSignedIn = true, requiresProfile = true))
            val viewModel =
                HomeViewModel(
                    account = account,
                    catalogs = catalogs,
                    library = FakeLibraryRepository(),
                    progress = FakeProgressRepository(),
                    metadata = FakeMetadataRepository(),
                    badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
                )
            account.selectProfile(BackendProfile(id = "p1", name = "One"))
            advanceUntilIdle()
            assertEquals(
                listOf("tt-one"),
                viewModel.state.value.rows
                    .single()
                    .items
                    .map(MetaPreview::id),
            )

            account.selectProfile(BackendProfile(id = "p2", name = "Two"))
            advanceUntilIdle()

            // The previous profile's rows are gone while the new ones load: Home
            // shows its loading state instead of refreshing behind them.
            assertTrue(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.rows
                    .isEmpty(),
            )

            catalogs.releaseSecondCall.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf("tt-two"),
                viewModel.state.value.rows
                    .single()
                    .items
                    .map(MetaPreview::id),
            )
        }

    private fun ref(id: String) =
        CatalogRef(
            addonName = "Addon",
            addonBaseUrl = "https://addon",
            type = ContentType("movie"),
            id = id,
            name = id,
        )

    private fun preview(id: String) = MetaPreview(id = id, type = ContentType("movie"), name = id)

    private fun homeViewModel(
        catalogs: FakeCatalogRepository,
        settings: SettingsRepository = InMemorySettingsRepository(),
    ): HomeViewModel =
        HomeViewModel(
            account = FakeAccountRepository(),
            catalogs = catalogs,
            library = FakeLibraryRepository(),
            progress = FakeProgressRepository(),
            metadata = FakeMetadataRepository(),
            badges = WatchedBadgeResolver(FakeProgressRepository(), FakeMetadataRepository()),
            settings = settings,
        )

    /** Catalog whose second read waits for the test, so a load can be held in flight. */
    private class SwappingCatalogRepository(
        private val refs: List<CatalogRef>,
        private val first: List<MetaPreview>,
        private val second: List<MetaPreview>,
    ) : CatalogRepository {
        val releaseSecondCall = CompletableDeferred<Unit>()
        private var calls = 0

        override suspend fun catalogs(): List<CatalogRef> = refs

        override suspend fun items(
            ref: CatalogRef,
            extra: Map<String, String>?,
        ): List<MetaPreview> {
            val call = calls++
            if (call > 0) releaseSecondCall.await()
            return if (call == 0) first else second
        }
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
