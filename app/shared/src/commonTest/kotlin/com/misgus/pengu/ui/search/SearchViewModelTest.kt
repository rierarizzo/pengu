package com.misgus.pengu.ui.search

import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.ui.support.FakeAccountRepository
import com.misgus.pengu.ui.support.FakeCatalogRepository
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

class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun ref(
        id: String,
        searchable: Boolean = true,
        type: String = "movie",
    ) = CatalogRef(
        addonName = "Addon $id",
        addonBaseUrl = "https://addon-$id",
        type = ContentType(type),
        id = id,
        name = id,
        extras = if (searchable) listOf(AddonCatalogExtra("search", isRequired = true)) else emptyList(),
    )

    private fun preview(id: String) = MetaPreview(id = id, type = ContentType("movie"), name = id)

    @Test
    fun keepsOneSectionPerSearchableCatalogWithoutMerging() =
        runTest(dispatcher) {
            val first = ref("first")
            val second = ref("second")
            val plain = ref("top", searchable = false)
            val catalogs =
                FakeCatalogRepository(
                    refs = listOf(first, second, plain),
                    items =
                        mapOf(
                            first.key to listOf(preview("tt1"), preview("tt2")),
                            // The same title found by a second catalog stays in its own
                            // section instead of being dropped as a duplicate.
                            second.key to listOf(preview("tt1"), preview("tt3")),
                            plain.key to listOf(preview("tt9")),
                        ),
                )
            val viewModel = SearchViewModel(FakeAccountRepository(), catalogs)
            advanceUntilIdle()

            viewModel.onQueryChange("dune")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.hasSearched)
            assertFalse(state.isSearching)
            assertEquals(listOf("first", "second"), state.sections.map { it.title })
            assertEquals(
                listOf(listOf("tt1", "tt2"), listOf("tt1", "tt3")),
                state.sections.map { section -> section.items.map { it.id } },
            )
        }

    @Test
    fun dropsTheSectionsOfACatalogThatAnsweredNothing() =
        runTest(dispatcher) {
            val answered = ref("answered")
            val empty = ref("empty")
            val catalogs =
                FakeCatalogRepository(
                    refs = listOf(answered, empty),
                    items = mapOf(answered.key to listOf(preview("tt1"))),
                )
            val viewModel = SearchViewModel(FakeAccountRepository(), catalogs)
            advanceUntilIdle()

            viewModel.onQueryChange("dune")
            advanceUntilIdle()

            assertEquals(
                listOf("answered"),
                viewModel.state.value.sections
                    .map { it.title },
            )
        }

    @Test
    fun clearingTheQueryResetsTheSections() =
        runTest(dispatcher) {
            val searchable = ref("first")
            val catalogs =
                FakeCatalogRepository(
                    refs = listOf(searchable),
                    items = mapOf(searchable.key to listOf(preview("tt1"))),
                )
            val viewModel = SearchViewModel(FakeAccountRepository(), catalogs)
            advanceUntilIdle()

            viewModel.onQueryChange("dune")
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.sections.size)

            viewModel.onQueryChange("")
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.sections
                    .isEmpty(),
            )
            assertFalse(viewModel.state.value.hasSearched)
        }
}
