package com.misgus.pengu.ui.home

import com.misgus.pengu.core.catalog.HomeSnapshot
import com.misgus.pengu.core.catalog.HomeSnapshotRow
import com.misgus.pengu.core.catalog.HomeSnapshotStore
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.InMemorySettingsRepository
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.watched.WatchedBadgeResolver
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Home's stale-while-revalidate and its billboard: the previous paint is shown
 * immediately, the network answer replaces it, what was painted is stored for
 * the next cold start, and the set comes from the catalogs the settings say.
 */
class HomeSnapshotTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun ref(id: String) =
        CatalogRef(
            addonName = "Addon",
            addonBaseUrl = "https://addon",
            type = ContentType("movie"),
            id = id,
            name = id,
        )

    private fun preview(id: String) = MetaPreview(id = id, type = ContentType("movie"), name = id)

    private fun viewModel(
        catalogs: FakeCatalogRepository,
        snapshot: HomeSnapshotStore,
        settings: SettingsRepository = InMemorySettingsRepository(),
        now: () -> Instant = { Clock.System.now() },
    ): HomeViewModel {
        val progress = FakeProgressRepository()
        return HomeViewModel(
            account = FakeAccountRepository(),
            catalogs = catalogs,
            library = FakeLibraryRepository(),
            progress = progress,
            badges = WatchedBadgeResolver(progress, FakeMetadataRepository()),
            snapshot = snapshot,
            settings = settings,
            now = now,
        )
    }

    @Test
    fun paintsTheStoredRowsBeforeTheNetworkAnswers() =
        runTest(dispatcher) {
            val ref = ref("top")
            val gate = CompletableDeferred<Unit>()
            val stored =
                HomeSnapshot(
                    rows = listOf(HomeSnapshotRow(ref, listOf(preview("tt-old")))),
                    featured = listOf(preview("tt-old")),
                    featuredAt = Clock.System.now(),
                )
            val viewModel =
                viewModel(
                    catalogs =
                        FakeCatalogRepository(
                            refs = listOf(ref),
                            items = mapOf(ref.key to listOf(preview("tt-new"))),
                            gates = mapOf(ref.key to gate),
                        ),
                    snapshot = MemoryHomeSnapshotStore(mapOf("local" to stored)),
                )

            viewModel.load()
            advanceUntilIdle()

            // The catalogs are still in flight, and Home already shows content.
            assertTrue(viewModel.state.value.isLoading)
            val painted = viewModel.state.value.rows
            assertEquals(listOf("tt-old"), painted.single().items.map(MetaPreview::id))

            gate.complete(Unit)
            advanceUntilIdle()

            val loaded = viewModel.state.value.rows
            assertEquals(listOf("tt-new"), loaded.single().items.map(MetaPreview::id))
            // The billboard that was already on screen is kept, so its artwork
            // never swaps a moment after it appears.
            val featured = viewModel.state.value.featured
            assertEquals(listOf("tt-old"), featured.map(MetaPreview::id))
        }

    @Test
    fun storesWhatItPaintedForTheNextColdStart() =
        runTest(dispatcher) {
            val ref = ref("top")
            val store = MemoryHomeSnapshotStore()
            val now = Instant.parse("2024-06-01T12:00:00Z")
            val viewModel =
                viewModel(
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview("tt1")))),
                    snapshot = store,
                    now = { now },
                )

            viewModel.load()
            advanceUntilIdle()

            val stored = store.snapshots.getValue("local")
            val rows = stored.rows
            assertEquals(listOf("tt1"), rows.single().items.map(MetaPreview::id))
            assertEquals(listOf("tt1"), stored.featured.map(MetaPreview::id))
            // The pick time travels with the set, so an aged one can be re-picked.
            assertEquals(now, stored.featuredAt)
        }

    @Test
    fun startsFromTheNetworkWhenThereIsNoSnapshot() =
        runTest(dispatcher) {
            val ref = ref("top")
            val viewModel =
                viewModel(
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview("tt1")))),
                    snapshot = MemoryHomeSnapshotStore(),
                )

            viewModel.load()
            advanceUntilIdle()

            val rows = viewModel.state.value.rows
            assertEquals(listOf("tt1"), rows.single().items.map(MetaPreview::id))
        }

    @Test
    fun rePicksAStoredBillboardThatHasAgedOut() =
        runTest(dispatcher) {
            val ref = ref("top")
            val now = Instant.parse("2024-06-01T12:00:00Z")
            val store =
                MemoryHomeSnapshotStore(
                    mapOf(
                        "local" to
                            HomeSnapshot(
                                rows = listOf(HomeSnapshotRow(ref, listOf(preview("tt-old")))),
                                featured = listOf(preview("tt-old")),
                                featuredAt = now - 7.hours,
                            ),
                    ),
                )
            val viewModel =
                viewModel(
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview("tt-new")))),
                    snapshot = store,
                    now = { now },
                )

            viewModel.load()
            advanceUntilIdle()

            // The stored rows painted first, and the stale set was replaced by a
            // fresh pick, which is what the next cold start will reuse.
            val featured = viewModel.state.value.featured
            assertEquals(listOf("tt-new"), featured.map(MetaPreview::id))
            assertEquals(now, store.snapshots.getValue("local").featuredAt)
        }

    @Test
    fun keepsAStoredBillboardThatIsStillRecent() =
        runTest(dispatcher) {
            val ref = ref("top")
            val now = Instant.parse("2024-06-01T12:00:00Z")
            val pickedAt = now - 1.hours
            val store =
                MemoryHomeSnapshotStore(
                    mapOf(
                        "local" to
                            HomeSnapshot(
                                rows = listOf(HomeSnapshotRow(ref, listOf(preview("tt-old")))),
                                featured = listOf(preview("tt-old")),
                                featuredAt = pickedAt,
                            ),
                    ),
                )
            val viewModel =
                viewModel(
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview("tt-new")))),
                    snapshot = store,
                    now = { now },
                )

            viewModel.load()
            advanceUntilIdle()

            val featured = viewModel.state.value.featured
            assertEquals(listOf("tt-old"), featured.map(MetaPreview::id))
            // The pick time is not refreshed by a load: the window is measured
            // from the pick, not from the last write.
            assertEquals(pickedAt, store.snapshots.getValue("local").featuredAt)
        }

    @Test
    fun aManualBillboardDrawsOnlyFromThePickedCatalogs() =
        runTest(dispatcher) {
            val picked = ref("picked")
            val other = ref("other")
            val viewModel =
                viewModel(
                    catalogs =
                        FakeCatalogRepository(
                            refs = listOf(picked, other),
                            items =
                                mapOf(
                                    picked.key to listOf(preview("tt-picked")),
                                    other.key to listOf(preview("tt-other")),
                                ),
                        ),
                    snapshot = MemoryHomeSnapshotStore(),
                    settings =
                        InMemorySettingsRepository(
                            AppSettings(heroSource = HeroSource.MANUAL, heroCatalogKeys = listOf(picked.key)),
                        ),
                )

            viewModel.load()
            advanceUntilIdle()

            // Every row still loads; only the billboard is narrowed.
            assertEquals(2, viewModel.state.value.rows.size)
            val featured = viewModel.state.value.featured
            assertEquals(listOf("tt-picked"), featured.map(MetaPreview::id))
        }

    @Test
    fun aManualBillboardFallsBackToEveryRowWhenItsCatalogsAreGone() =
        runTest(dispatcher) {
            val ref = ref("top")
            val viewModel =
                viewModel(
                    catalogs = FakeCatalogRepository(listOf(ref), mapOf(ref.key to listOf(preview("tt1")))),
                    snapshot = MemoryHomeSnapshotStore(),
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

            val featured = viewModel.state.value.featured
            assertEquals(listOf("tt1"), featured.map(MetaPreview::id))
        }

    @Test
    fun changingTheSourceRePicksTheBillboardAtOnce() =
        runTest(dispatcher) {
            val picked = ref("picked")
            val other = ref("other")
            val settings = InMemorySettingsRepository()
            val viewModel =
                viewModel(
                    catalogs =
                        FakeCatalogRepository(
                            refs = listOf(picked, other),
                            items =
                                mapOf(
                                    picked.key to listOf(preview("tt-picked")),
                                    other.key to listOf(preview("tt-other")),
                                ),
                        ),
                    snapshot = MemoryHomeSnapshotStore(),
                    settings = settings,
                )

            viewModel.load()
            advanceUntilIdle()
            val smart = viewModel.state.value.featured
            assertTrue(smart.any { it.id == "tt-other" })

            settings.setHeroSource(HeroSource.MANUAL)
            settings.setHeroCatalogKeys(listOf(picked.key))
            advanceUntilIdle()

            // No reload and no wait for the rotation: the change is applied.
            val manual = viewModel.state.value.featured
            assertEquals(listOf("tt-picked"), manual.map(MetaPreview::id))
        }

    @Test
    fun keepsEveryStoredRowWhileTheLoadFinishes() =
        runTest(dispatcher) {
            val refs = (1..5).map { ref("c$it") }
            val gate = CompletableDeferred<Unit>()
            val stored =
                HomeSnapshot(
                    rows = refs.map { HomeSnapshotRow(it, listOf(preview("tt-${it.id}"))) },
                    featured = listOf(preview("tt-c1")),
                    featuredAt = Clock.System.now(),
                )
            val viewModel =
                viewModel(
                    catalogs =
                        FakeCatalogRepository(
                            refs = refs,
                            items = refs.associate { it.key to listOf(preview("tt-new-${it.id}")) },
                            // Batch 2 is still in flight when batch 1 answers.
                            gates = mapOf(refs.last().key to gate),
                        ),
                    snapshot = MemoryHomeSnapshotStore(mapOf("local" to stored)),
                )

            viewModel.load()
            advanceUntilIdle()

            // The first batch answered and the screen did not shrink to it: the
            // stored rows, and the stored billboard, are still all on screen.
            assertEquals(5, viewModel.state.value.rows.size)
            val featured = viewModel.state.value.featured
            assertEquals(listOf("tt-c1"), featured.map(MetaPreview::id))

            gate.complete(Unit)
            advanceUntilIdle()

            val loadedRows = viewModel.state.value.rows
            assertEquals(5, loadedRows.size)
            val lastRow = loadedRows.last()
            val loadedItem = lastRow.items.first()
            assertEquals("tt-new-c5", loadedItem.id)
        }

    private class MemoryHomeSnapshotStore(
        initial: Map<String, HomeSnapshot> = emptyMap(),
    ) : HomeSnapshotStore {
        val snapshots = initial.toMutableMap()

        override suspend fun read(profileKey: String): HomeSnapshot? = snapshots[profileKey]

        override suspend fun write(
            profileKey: String,
            snapshot: HomeSnapshot,
        ) {
            snapshots[profileKey] = snapshot
        }
    }
}
