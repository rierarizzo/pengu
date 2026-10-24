package com.misgus.pengu.ui

import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.LibraryItem
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.ui.detail.DetailViewModel
import com.misgus.pengu.ui.support.FakeMetadataRepository
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
    fun previewsTheLibraryItemWhileThePageLoads() =
        runTest(dispatcher) {
            val viewModel = DetailViewModel(FakeMetadataRepository(), item())

            assertEquals("Movie", viewModel.state.value.name)
            assertEquals("tt1", viewModel.state.value.contentId)
        }

    @Test
    fun settlesOnTheAddonAnswer() =
        runTest(dispatcher) {
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(detail = addonDetail()),
                    item = item(),
                )

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.hasLoaded)
            assertEquals("From the addon", state.description)
            // The id streams are addressed with is the library item's.
            assertEquals("tt1", state.contentId)
        }

    @Test
    fun keepsTheLibraryItemWhenNothingAnswers() =
        runTest(dispatcher) {
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(),
                    item = item().copy(description = "From the library"),
                )

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.hasLoaded)
            assertEquals("Movie", state.name)
            assertEquals("From the library", state.description)
        }

    @Test
    fun keepsTheAddonSimilarRow() =
        runTest(dispatcher) {
            val fromAddon = listOf(MetaPreview(id = "addon:1", type = ContentTypes.MOVIE, name = "Addon"))
            val viewModel =
                DetailViewModel(
                    metadata = FakeMetadataRepository(detail = addonDetail(), similar = fromAddon),
                    item = item(),
                )

            viewModel.load()
            advanceUntilIdle()

            assertEquals(fromAddon, viewModel.state.value.similar)
        }
}
