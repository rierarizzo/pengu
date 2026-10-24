package com.misgus.pengu.ui.components

import androidx.compose.runtime.Composable
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.content_type_movie
import com.misgus.pengu.app.shared.generated.resources.content_type_series
import com.misgus.pengu.app.shared.generated.resources.search_type_anime
import com.misgus.pengu.app.shared.generated.resources.search_type_movies
import com.misgus.pengu.app.shared.generated.resources.search_type_series
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.ContentTypes
import org.jetbrains.compose.resources.stringResource

/**
 * The section label of a Stremio content type: "Movies", "Series", "Anime".
 *
 * Anime is checked before the series aliases on purpose: the protocol counts
 * `anime` as a series, but a MAL catalog is not a series catalog to the user.
 * Any other type (`sport`, `tv`, `events`, ...) is capitalized as the addon
 * spelled it, like Nuvio does: those are protocol values with no fixed set, and
 * they read better as data than as a missing label.
 */
@Composable
fun contentTypeLabel(type: ContentType): String =
    when {
        type == ContentTypes.MOVIE -> stringResource(Res.string.search_type_movies)
        type == ContentTypes.SERIES -> stringResource(Res.string.search_type_series)
        type.value.startsWith("anime", ignoreCase = true) -> stringResource(Res.string.search_type_anime)
        else ->
            type.value
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .ifBlank { type.value }
    }

/**
 * The singular "Movie" / "Series" name for a Stremio content type, or `null` for
 * a type the app does not name.
 *
 * Used inside a billboard's metadata line, where the plural section label would
 * read wrong ("Movies" next to a single title).
 */
@Composable
fun contentTypeName(type: ContentType): String? =
    when {
        type.value.equals(ContentTypes.MOVIE.value, ignoreCase = true) -> stringResource(Res.string.content_type_movie)
        ContentTypes.isSeries(type) -> stringResource(Res.string.content_type_series)
        else -> null
    }
