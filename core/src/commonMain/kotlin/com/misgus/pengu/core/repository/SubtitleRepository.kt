package com.misgus.pengu.core.repository

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Subtitle

/** Reads subtitle tracks from the account's addons. */
interface SubtitleRepository {
    /**
     * Subtitle tracks for [id] (`tt123` or `tt123:1:2`), where [type] is
     * `movie`, `series`, ...
     */
    suspend fun all(
        type: ContentType,
        id: String,
    ): List<Subtitle>
}
