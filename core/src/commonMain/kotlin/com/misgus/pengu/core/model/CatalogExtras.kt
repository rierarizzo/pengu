package com.misgus.pengu.core.model

/**
 * Names of the `extra` parameters a Stremio catalog can declare.
 *
 * They are protocol vocabulary, not UI labels, so they live with the models
 * instead of being spelled out at every call site.
 */
object CatalogExtras {
    /** Free-text search parameter. */
    const val SEARCH = "search"

    /** Pagination offset. */
    const val SKIP = "skip"

    /** Genre filter. */
    const val GENRE = "genre"
}
