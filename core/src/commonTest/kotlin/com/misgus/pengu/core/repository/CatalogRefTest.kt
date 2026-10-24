package com.misgus.pengu.core.repository

import com.misgus.pengu.core.model.AddonCatalogExtra
import com.misgus.pengu.core.model.CatalogExtras
import com.misgus.pengu.core.model.ContentType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which catalogs Discover may browse and which Search may query.
 *
 * The rules mirror Nuvio's `supportsDiscover()` / `supportsSearch()`: `skip` and
 * `search` never block browsing, a required `search` makes the catalog
 * search-only, and any other required extra does unless it is a `genre` with
 * options to pick from.
 */
class CatalogRefTest {
    private fun ref(vararg extras: AddonCatalogExtra) =
        CatalogRef(
            addonName = "Addon",
            addonBaseUrl = "https://addon",
            type = ContentType("movie"),
            id = "top",
            name = "Top",
            extras = extras.toList(),
        )

    private fun extra(
        name: String,
        required: Boolean = false,
        options: List<String> = emptyList(),
    ) = AddonCatalogExtra(name = name, isRequired = required, options = options)

    @Test
    fun aCatalogWithoutExtrasIsBrowsableButNotSearchable() {
        val plain = ref()

        assertTrue(plain.browsable)
        assertFalse(plain.searches)
    }

    @Test
    fun aDiscoverCatalogWithGenresAndPagingStaysBrowsable() {
        val catalog = ref(extra(CatalogExtras.GENRE, options = listOf("Action")), extra(CatalogExtras.SKIP, required = true))

        assertTrue(catalog.browsable)
        assertFalse(catalog.searches)
    }

    @Test
    fun aRequiredGenreWithOptionsIsStillBrowsable() {
        val catalog = ref(extra(CatalogExtras.GENRE, required = true, options = listOf("Action")))

        assertTrue(catalog.browsable)
    }

    @Test
    fun aRequiredGenreWithNoOptionsCannotBeBrowsed() {
        assertFalse(ref(extra(CatalogExtras.GENRE, required = true)).browsable)
    }

    @Test
    fun aRequiredSearchMakesTheCatalogSearchOnly() {
        val catalog = ref(extra(CatalogExtras.SEARCH, required = true), extra(CatalogExtras.SKIP))

        assertTrue(catalog.searches)
        assertFalse(catalog.browsable)
    }

    @Test
    fun anOptionalSearchIsBothSearchableAndBrowsable() {
        val catalog = ref(extra(CatalogExtras.SEARCH), extra(CatalogExtras.SKIP))

        assertTrue(catalog.searches)
        assertTrue(catalog.browsable)
    }

    @Test
    fun anotherRequiredExtraBlocksBoth() {
        val catalog = ref(extra(CatalogExtras.SEARCH), extra("calendarVideosIds", required = true))

        assertFalse(catalog.searches)
        assertFalse(catalog.browsable)
    }
}
