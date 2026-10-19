package com.misgus.pengu.core.repository

import com.misgus.pengu.core.model.Addon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Reads the addons installed in the account.
 *
 * Metadata for a title is not tied to one addon: like Nuvio does, it is looked
 * up across the enabled addons, so the UI needs the list to know where to ask.
 */
interface AddonRepository {
    /** Enabled addons of the active profile, in the user's order. */
    suspend fun all(): List<Addon>

    /**
     * Emits when the list [all] serves has changed behind the caller's back.
     *
     * A repository that answers from a local snapshot refreshes it in the
     * background; a screen that already loaded content listens here to pick up an
     * addon the user installed elsewhere. Repositories that always read the
     * source directly emit nothing.
     */
    val changes: Flow<Unit> get() = emptyFlow()
}

/** Addon repository with no addons, for a composition that has none to offer. */
class NoopAddonRepository : AddonRepository {
    override suspend fun all(): List<Addon> = emptyList()
}
