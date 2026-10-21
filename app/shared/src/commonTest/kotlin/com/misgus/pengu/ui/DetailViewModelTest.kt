package com.misgus.pengu.ui

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.model.TmdbEpisode
import com.misgus.pengu.core.model.TmdbMediaType
import com.misgus.pengu.core.model.TmdbTitle
import com.misgus.pengu.ui.detail.DetailViewModel
import com.misgus.pengu.ui.support.FakeMetadataRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun item() =
        LibraryItem(
            id = "tt1",
            contentId = "tt1",
            contentType = ContentTypes.MOVIE,
            name = "Movie",
        )

    private fun addonDetail() =
        MetaDetail(
            id = "tt1",
            type = ContentTypes.MOVIE,
            name = "Movie",
            description = "From the addon",
        )

    @Test
    fun previewsTheAddonMetadataWhileThePageLoads() =
        runTest(dispatcher) {
            val viewModel = DetailViewModel(FakeMetadataRepository(), FakeTmdbRepository(), item())

            assertEquals("Movie", viewModel.state.value.name)
        }

    @Test
    fun takesTmdbArtworkAndCopyOverTheAddonOnes() =
        runTest(dispatcher) {
            val tmdb =
                TmdbTitle(
                    tmdbId = 1,
                    mediaType = TmdbMediaType.MOVIE,
                    title = "Movie",
                    poster = "https://img/poster.jpg",
                    backdrop = "https://img/backdrop.jpg",
                    textlessPoster = "https://img/textless.jpg",
                    overview = "From TMDB",
                    runtimeMinutes = 136,
                )
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(detail = addonDetail()),
                    tmdb = FakeTmdbRepository(titles = mapOf("tt1" to tmdb)),
                    item = item(),
                )
            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("https://img/backdrop.jpg", state.background)
            assertEquals("https://img/textless.jpg", state.textlessPoster)
            assertEquals(136, state.runtimeMinutes)
            // TMDB answers the fields it has, even when the addon did too; the
            // page shows one source instead of swapping a moment after it paints.
            assertEquals("From TMDB", state.description)
            assertEquals("https://img/poster.jpg", state.poster)
            // The id streams are addressed with is still the addon's.
            assertEquals("tt1", state.contentId)
        }

    @Test
    fun waitsForTmdbBeforeSettlingTheArtwork() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val tmdb =
                TmdbTitle(
                    tmdbId = 1,
                    mediaType = TmdbMediaType.MOVIE,
                    title = "Movie",
                    textlessPoster = "https://img/textless.jpg",
                )
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(detail = addonDetail()),
                    tmdb = FakeTmdbRepository(titles = mapOf("tt1" to tmdb), gate = gate),
                    item = item(),
                )

            viewModel.load()
            advanceUntilIdle()

            // A TMDB answer with priority is still on its way, so the page holds
            // the whole state back instead of painting the addon's.
            val waiting = viewModel.state.value
            assertFalse(waiting.hasLoaded)

            gate.complete(Unit)
            advanceUntilIdle()

            val settled = viewModel.state.value
            assertTrue(settled.hasLoaded)
            assertEquals("https://img/textless.jpg", settled.textlessPoster)
        }

    @Test
    fun settlesOnTheAddonAnswerWhenTmdbIsOff() =
        runTest(dispatcher) {
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(detail = addonDetail()),
                    tmdb = FakeTmdbRepository(isConfigured = false),
                    item = item(),
                )

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.hasLoaded)
            assertEquals("From the addon", state.description)
        }

    @Test
    fun buildsFromTmdbWhenNoAddonHasTheTitle() =
        runTest(dispatcher) {
            val tmdb =
                TmdbTitle(
                    tmdbId = 603,
                    mediaType = TmdbMediaType.MOVIE,
                    title = "The Matrix",
                    backdrop = "https://img/backdrop.jpg",
                )
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(),
                    tmdb = FakeTmdbRepository(titles = mapOf("tt1" to tmdb)),
                    item = item(),
                )
            viewModel.load()
            advanceUntilIdle()

            assertEquals("The Matrix", viewModel.state.value.name)
            assertEquals("https://img/backdrop.jpg", viewModel.state.value.background)
        }

    @Test
    fun loadsTheEpisodeCopyOfTheSeasonAsked() =
        runTest(dispatcher) {
            val episodes = listOf(TmdbEpisode(seasonNumber = 1, episodeNumber = 1, overview = "From TMDB"))
            val tmdb = FakeTmdbRepository(seasons = mapOf(1 to episodes))
            val viewModel = DetailViewModel(FakeMetadataRepository(), tmdb, item())

            viewModel.loadSeason(1)
            // The picker is told the season is in flight before the answer lands,
            // so it reserves the cards instead of painting them twice.
            assertTrue(1 in viewModel.state.value.seasonsLoading)
            // A second visit to the same season is not another request.
            viewModel.loadSeason(1)
            advanceUntilIdle()

            val settled = viewModel.state.value
            assertEquals(mapOf(1 to episodes), settled.episodeDetails)
            assertTrue(settled.seasonsLoading.isEmpty())
            assertEquals(listOf(1), tmdb.seasonsLoaded)
        }

    @Test
    fun asksForNoEpisodeCopyWithoutAKey() =
        runTest(dispatcher) {
            val tmdb = FakeTmdbRepository(isConfigured = false)
            val viewModel = DetailViewModel(FakeMetadataRepository(), tmdb, item())

            viewModel.loadSeason(1)
            advanceUntilIdle()

            assertEquals(emptyMap(), viewModel.state.value.episodeDetails)
            assertEquals(emptyList(), tmdb.seasonsLoaded)
        }

    @Test
    fun takesTheSimilarRowFromTmdb() =
        runTest(dispatcher) {
            val recommended = listOf(MetaPreview(id = "tmdb:2", type = ContentTypes.MOVIE, name = "Other"))
            val viewModel =
                DetailViewModel(
                    metadata =
                        FakeMetadataRepository(
                            detail = addonDetail(),
                            similar = listOf(MetaPreview(id = "addon:1", type = ContentTypes.MOVIE, name = "Addon")),
                        ),
                    tmdb = FakeTmdbRepository(recommended = recommended),
                    item = item(),
                )
            viewModel.load()
            advanceUntilIdle()

            assertEquals(recommended, viewModel.state.value.similar)
        }

    @Test
    fun keepsTheAddonSimilarRowWhenTmdbHasNothing() =
        runTest(dispatcher) {
            val fromAddon = listOf(MetaPreview(id = "addon:1", type = ContentTypes.MOVIE, name = "Addon"))
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(detail = addonDetail(), similar = fromAddon),
                    tmdb = FakeTmdbRepository(recommended = emptyList()),
                    item = item(),
                )
            viewModel.load()
            advanceUntilIdle()

            assertEquals(fromAddon, viewModel.state.value.similar)
        }

    @Test
    fun resolvesARecommendationThroughItsImdbId() =
        runTest(dispatcher) {
            val addonMeta = MetaDetail(id = "tt0133093", type = ContentTypes.MOVIE, name = "From the addon")
            val tmdb =
                TmdbTitle(
                    tmdbId = 603,
                    mediaType = TmdbMediaType.MOVIE,
                    title = "The Matrix",
                    imdbId = "tt0133093",
                )
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(details = mapOf("tt0133093" to addonMeta)),
                    tmdb = FakeTmdbRepository(titles = mapOf("tmdb:603" to tmdb)),
                    item =
                        LibraryItem(
                            id = "tmdb:603",
                            contentId = "tmdb:603",
                            contentType = ContentTypes.MOVIE,
                            name = "The Matrix",
                        ),
                )
            viewModel.load()
            advanceUntilIdle()

            // Streams use the id every addon understands, not the tmdb: one.
            assertEquals("tt0133093", viewModel.state.value.contentId)
            // The page names the title the way TMDB does, like its artwork.
            assertEquals("The Matrix", viewModel.state.value.name)
        }
}
