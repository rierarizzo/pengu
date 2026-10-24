package com.misgus.pengu.core.repository

import com.misgus.pengu.core.BackendException
import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Why an addon could not be installed. */
enum class AddonInstallFailure {
    /** The text is not an `http`/`https` addon URL. */
    INVALID_URL,

    /** An addon with the same base URL is already installed. */
    ALREADY_INSTALLED,

    /** The manifest could not be fetched or was not a Stremio manifest. */
    MANIFEST_UNREACHABLE,

    /** The backend does not support managing addons. */
    UNSUPPORTED,
}

/** Thrown when [AddonRepository.install] rejects a URL. */
class AddonInstallException(
    val reason: AddonInstallFailure,
    override val cause: Throwable? = null,
) : Exception("Could not install addon: $reason", cause)

/**
 * Reads and manages the addons installed in the account.
 *
 * Metadata for a title is not tied to one addon: like Nuvio does, it is looked
 * up across the enabled addons, so the UI needs the list to know where to ask.
 * [all] is the content path and only returns enabled addons; [installed] is the
 * management path and also returns the disabled ones, so the settings page can
 * show and re-enable them.
 */
interface AddonRepository {
    /** Enabled addons of the active profile, in the user's order. */
    suspend fun all(): List<Addon>

    /**
     * Every addon of the active profile, enabled or not, in the user's order.
     *
     * Repositories that cannot manage addons simply return [all].
     */
    suspend fun installed(): List<Addon> = all()

    /** Whether this backend exposes install/enable/remove/reorder. */
    val canManage: Boolean get() = false

    /**
     * Key of the active profile, when the backend has profiles.
     *
     * Local, profile-scoped caches (the Home feed) key themselves by it, so a
     * backend without profiles simply does not cache.
     */
    val profileKey: String? get() = null

    /**
     * Fetches [url]'s manifest and appends the addon to the installed list.
     *
     * The manifest is validated before anything is written, so a typo does not
     * leave a broken row behind. Throws [AddonInstallException] when the URL is
     * invalid, the manifest cannot be read, or the addon is already installed.
     */
    suspend fun install(url: String): Addon = throw AddonInstallException(AddonInstallFailure.UNSUPPORTED)

    /** Enables or disables an installed addon. */
    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ): Unit = throw BackendException("Addon management is not supported")

    /** Removes an installed addon. */
    suspend fun remove(id: String): Unit = throw BackendException("Addon management is not supported")

    /** Rewrites the user's order from the ids of [installed]. */
    suspend fun reorder(ids: List<String>): Unit = throw BackendException("Addon management is not supported")

    /**
     * Manifest of the addon at [url], or `null` when it cannot be read.
     *
     * The settings page uses it for the logo and the description; the content
     * path reads manifests through the Stremio repositories instead.
     */
    suspend fun manifest(url: String): AddonManifest? = null

    /**
     * Emits when the list [all] serves has changed.
     *
     * A repository that answers from a local snapshot refreshes it in the
     * background; a screen that already loaded content listens here to pick up an
     * addon the user installed elsewhere. Management writes emit it too, so Home
     * reloads after an addon is toggled, reordered or removed. Repositories that
     * always read the source directly emit nothing.
     */
    val changes: Flow<Unit> get() = emptyFlow()
}

/** Addon repository with no addons, for a composition that has none to offer. */
class NoopAddonRepository : AddonRepository {
    override suspend fun all(): List<Addon> = emptyList()
}
