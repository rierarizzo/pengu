package com.misgus.pengu.core.watched

import com.misgus.pengu.core.model.ContentType
import com.misgus.pengu.core.model.Episode
import com.misgus.pengu.core.model.MetaDetail
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.Season
import com.misgus.pengu.core.model.WatchProgress
import com.misgus.pengu.core.model.WatchedEntry
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WatchedBadgeResolverTest {
    private val today = Instant.parse("2024-01-10T00:00:00Z")

    @Test
    fun movieIsWatchedFromATitleMarker() =
        runTest {
            val progress =
                FakeProgress(
                    entries = listOf(WatchedEntry("tt-movie", ContentType("movie"))),
                )
            val resolver = WatchedBadgeResolver(progress, FakeMetadata())
            val watched =
                resolver.resolve(
                    listOf(WatchedCandidate("tt-movie", ContentType("movie")), WatchedCandidate("tt-other", ContentType("movie"))),
                    today = today,
                )
            assertEquals(setOf("tt-movie"), watched)
        }

    @Test
    fun seriesIsWatchedWhenEveryAiredEpisodeIsCompleted() =
        runTest {
            val series = "tt-series"
            val meta =
                MetaDetail(
                    id = series,
                    type = ContentType("series"),
                    name = "Show",
                    videos =
                        listOf(
                            MetaVideo(id = "$series:1:1", season = Season(1), episode = Episode(1), released = "2024-01-01"),
                            MetaVideo(id = "$series:1:2", season = Season(1), episode = Episode(2), released = "2024-01-01"),
                        ),
                )
            val progress =
                FakeProgress(
                    entries = listOf(WatchedEntry(series, ContentType("series"), season = Season(1), episode = Episode(1))),
                    all =
                        listOf(
                            WatchProgress(
                                id = "1",
                                contentId = series,
                                contentType = ContentType("series"),
                                season = Season(1),
                                episode = Episode(1),
                                position = 50.minutes,
                                duration = 50.minutes,
                                progressKey = "${series}_s1e1",
                            ),
                            WatchProgress(
                                id = "2",
                                contentId = series,
                                contentType = ContentType("series"),
                                season = Season(1),
                                episode = Episode(2),
                                position = 50.minutes,
                                duration = 50.minutes,
                                progressKey = "${series}_s1e2",
                            ),
                        ),
                )
            val resolver = WatchedBadgeResolver(progress, FakeMetadata(meta))
            val watched = resolver.resolve(listOf(WatchedCandidate(series, ContentType("series"))), today = today)
            assertEquals(setOf(series), watched)
        }

    @Test
    fun seriesWithoutActivityIsNotLookedUp() =
        runTest {
            val progress = FakeProgress()
            val metadata = FakeMetadata()
            val resolver = WatchedBadgeResolver(progress, metadata)
            val watched = resolver.resolve(listOf(WatchedCandidate("tt-series", ContentType("series"))), today = today)
            assertEquals(emptySet(), watched)
            assertEquals(0, metadata.detailCalls)
        }

    private class FakeProgress(
        private val entries: List<WatchedEntry> = emptyList(),
        private val all: List<WatchProgress> = emptyList(),
    ) : ProgressRepository {
        override val changes: StateFlow<Long> = MutableStateFlow(0)

        override suspend fun all(): List<WatchProgress> = all

        override suspend fun save(progress: WatchProgress) = Unit

        override suspend fun remove(contentId: String) = Unit

        override suspend fun watchedEntries(candidateIds: Collection<String>): List<WatchedEntry> =
            entries.filter { it.contentId in candidateIds }

        override suspend fun markWatched(
            contentId: String,
            contentType: ContentType,
            season: Season?,
            episode: Episode?,
        ) = Unit

        override suspend fun unmarkWatched(
            contentId: String,
            season: Season?,
            episode: Episode?,
        ) = Unit
    }

    private class FakeMetadata(
        private val meta: MetaDetail? = null,
    ) : MetadataRepository {
        var detailCalls = 0

        override suspend fun detail(
            type: ContentType,
            id: String,
        ): MetaDetail? {
            detailCalls++
            return meta
        }

        override suspend fun similar(
            type: ContentType,
            id: String,
            genre: String?,
        ) = emptyList<Nothing>()
    }
}
