package com.misgus.pengu.core.catalog

import com.misgus.pengu.core.model.MetaPreview
import com.misgus.pengu.core.repository.CatalogRef
import kotlin.time.Instant

/** One catalog row as it was last painted. */
data class HomeSnapshotRow(
    val ref: CatalogRef,
    val items: List<MetaPreview>,
)

/**
 * The last Home paint: the rows and the billboard, in their on-screen order.
 *
 * Kept so a cold start can show the previous session's catalogs while the
 * network answer is on its way, instead of a spinner. The featured items are
 * stored enriched, so the billboard does not swap artwork a moment after it
 * appears.
 */
data class HomeSnapshot(
    val rows: List<HomeSnapshotRow>,
    val featured: List<MetaPreview> = emptyList(),
    /**
     * When the billboard set was picked, or `null` in a snapshot written before
     * the field existed. Home treats both the same way: an aged set is re-picked
     * on a cold start, so the billboard does not stay frozen for weeks.
     */
    val featuredAt: Instant? = null,
)

/**
 * Stores the last Home paint, per profile.
 *
 * Behind an interface because the UI owns what a row means (`HomeRow`) while
 * only the data layer knows how to write JSON: the ViewModel speaks snapshots,
 * the implementation speaks files.
 */
interface HomeSnapshotStore {
    /** The snapshot last stored for [profileKey], or `null` when there is none. */
    suspend fun read(profileKey: String): HomeSnapshot?

    /** Replaces the snapshot for [profileKey]. */
    suspend fun write(
        profileKey: String,
        snapshot: HomeSnapshot,
    )
}

/** Store that remembers nothing: every Home load starts from the network. */
class NoopHomeSnapshotStore : HomeSnapshotStore {
    override suspend fun read(profileKey: String): HomeSnapshot? = null

    override suspend fun write(
        profileKey: String,
        snapshot: HomeSnapshot,
    ) = Unit
}
