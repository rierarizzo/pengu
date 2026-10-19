package com.misgus.pengu.ui.streams

import com.misgus.pengu.core.model.ContentType

/** What the stream picker should look up. */
data class StreamsRequest(
    val type: ContentType,
    val id: String,
    val title: String,
    val year: String? = null,
)
