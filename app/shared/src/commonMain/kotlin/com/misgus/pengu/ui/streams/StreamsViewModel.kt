package com.misgus.pengu.ui.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Stream
import com.misgus.pengu.core.repository.StreamGroup
import com.misgus.pengu.core.repository.StreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state of the stream picker. */
data class StreamsUiState(
    val groups: List<StreamGroup> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
) {
    val totalCount: Int get() = groups.sumOf { it.streams.size }
}

/** Loads the streams for one title or episode. */
class StreamsViewModel(
    private val streams: StreamRepository,
    private val type: ContentType,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        if (_state.value.isLoading || (_state.value.hasLoaded && !force)) return
        viewModelScope.launch {
            // A refresh keeps the sources already on screen: clearing them would
            // replace the list with an empty state for the length of the request.
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = dedupe(streams.all(type, id))
                _state.update { it.copy(isLoading = false, hasLoaded = true, groups = groups) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    /**
     * Drops the copies of a source that several addons return, keeping the first
     * one, so the list does not offer the same release twice. An addon whose
     * sources were all copies disappears from the sections.
     */
    private fun dedupe(groups: List<StreamGroup>): List<StreamGroup> {
        val seen = mutableSetOf<String>()
        return groups.mapNotNull { group ->
            val streams = group.streams.filter { seen.add(it.identity()) }
            if (streams.isEmpty()) null else group.copy(streams = streams)
        }
    }
}

/**
 * Identity of a stream, so the same release from two addons collapses.
 *
 * The playable location identifies a source; a stream with none falls back to
 * its own content, which also collides only when the two entries really are the
 * same.
 */
private fun Stream.identity(): String =
    url
        ?: infoHash?.let { hash -> if (fileIdx == null) hash else "$hash#$fileIdx" }
        ?: externalUrl
        ?: hashCode().toString()
