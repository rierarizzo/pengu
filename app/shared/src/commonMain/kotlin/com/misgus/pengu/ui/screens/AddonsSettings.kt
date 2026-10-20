package com.misgus.pengu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_cancel
import com.misgus.pengu.app.shared.generated.resources.action_remove
import com.misgus.pengu.app.shared.generated.resources.action_try_again
import com.misgus.pengu.app.shared.generated.resources.settings_addons_add
import com.misgus.pengu.app.shared.generated.resources.settings_addons_already_installed
import com.misgus.pengu.app.shared.generated.resources.settings_addons_disable
import com.misgus.pengu.app.shared.generated.resources.settings_addons_drag
import com.misgus.pengu.app.shared.generated.resources.settings_addons_empty
import com.misgus.pengu.app.shared.generated.resources.settings_addons_enable
import com.misgus.pengu.app.shared.generated.resources.settings_addons_error
import com.misgus.pengu.app.shared.generated.resources.settings_addons_install
import com.misgus.pengu.app.shared.generated.resources.settings_addons_install_action
import com.misgus.pengu.app.shared.generated.resources.settings_addons_install_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_addons_installed
import com.misgus.pengu.app.shared.generated.resources.settings_addons_installed_count
import com.misgus.pengu.app.shared.generated.resources.settings_addons_invalid_url
import com.misgus.pengu.app.shared.generated.resources.settings_addons_loading
import com.misgus.pengu.app.shared.generated.resources.settings_addons_move_down
import com.misgus.pengu.app.shared.generated.resources.settings_addons_move_up
import com.misgus.pengu.app.shared.generated.resources.settings_addons_no_profile
import com.misgus.pengu.app.shared.generated.resources.settings_addons_read_only
import com.misgus.pengu.app.shared.generated.resources.settings_addons_remove_message
import com.misgus.pengu.app.shared.generated.resources.settings_addons_remove_title
import com.misgus.pengu.app.shared.generated.resources.settings_addons_signed_out
import com.misgus.pengu.app.shared.generated.resources.settings_addons_unreachable
import com.misgus.pengu.app.shared.generated.resources.settings_addons_url
import com.misgus.pengu.app.shared.generated.resources.settings_addons_version
import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AddonInstallException
import com.misgus.pengu.core.repository.AddonInstallFailure
import com.misgus.pengu.core.repository.AddonRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.common.AccountScopedViewModel
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.AppSwitch
import com.misgus.pengu.ui.components.AppTextField
import com.misgus.pengu.ui.components.RemoteImage
import com.misgus.pengu.ui.components.ReorderableCardColumn
import com.misgus.pengu.ui.components.ReorderableCardScope
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Immutable state of the addon settings page. */
data class AddonsSettingsUiState(
    val installed: List<Addon> = emptyList(),
    /** Manifest per addon id, for the row logo and description. */
    val manifests: Map<String, AddonManifest> = emptyMap(),
    /** Text of the install field, owned here so a successful install can clear it. */
    val url: String = "",
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val isInstalling: Boolean = false,
    /** Set once an install finishes, so the dialog can close itself. */
    val installSucceeded: Boolean = false,
    /** Addon whose toggle/reorder/remove is in flight. */
    val busyId: String? = null,
    /** Failure of an install/management write, shown below the page. */
    val error: StringResource? = null,
    /** Failure of the initial list read, shown in place of the list. */
    val loadError: StringResource? = null,
)

/**
 * Addons installed in the active profile, and the writes that manage them.
 *
 * The repository is the source of truth: every write is followed by a re-read,
 * so the row that was just toggled or moved reflects what the backend stored
 * instead of an optimistic guess.
 */
class AddonsSettingsViewModel(
    account: AccountRepository,
    private val addons: AddonRepository,
) : AccountScopedViewModel(account) {
    private val _state = MutableStateFlow(AddonsSettingsUiState())
    val state: StateFlow<AddonsSettingsUiState> = _state.asStateFlow()

    init {
        // The repository may refresh its snapshot behind the first read; pick
        // that answer up without reloading when this page made the change.
        viewModelScope.launch {
            addons.changes.collect {
                val installed = runCatchingCancellable { addons.installed() }.getOrNull() ?: return@collect
                applyInstalled(installed)
            }
        }
    }

    override fun onAccountReady() = load(force = true)

    override fun onAccountCleared() {
        _state.value = AddonsSettingsUiState()
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, error = null, installSucceeded = false) }
    }

    /** Clears the install form after the dialog closes. */
    fun clearInstall() {
        _state.update { it.copy(url = "", error = null, installSucceeded = false) }
    }

    /** Loads the installed addons. Does nothing when already loaded, unless [force]. */
    fun load(force: Boolean = false) {
        if (_state.value.isLoading || (_state.value.hasLoaded && !force)) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val installed = addons.installed()
                applyInstalled(installed)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        loadError = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun install() {
        val url = _state.value.url.trim()
        val current = _state.value
        if (url.isEmpty() || current.isInstalling || current.busyId != null) return
        viewModelScope.launch {
            _state.update { it.copy(isInstalling = true, error = null) }
            try {
                addons.install(url)
                // The install already refreshed the repository's cache; a failed
                // re-read must not turn a successful install into an error.
                val installed = runCatchingCancellable { addons.installed() }.getOrNull()
                _state.update { it.copy(url = "", isInstalling = false, installSucceeded = true, loadError = null) }
                installed?.let(::applyInstalled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: AddonInstallException) {
                _state.update { it.copy(isInstalling = false, error = error.reason.message()) }
            } catch (error: Throwable) {
                _state.update { it.copy(isInstalling = false, error = error.toUserMessage()) }
            }
        }
    }

    fun setEnabled(
        addon: Addon,
        enabled: Boolean,
    ) = write(addon.id) { addons.setEnabled(addon.id, enabled) }

    fun remove(addon: Addon) = write(addon.id) { addons.remove(addon.id) }

    /**
     * Moves one card to a new index and saves the order at once.
     *
     * The list is reordered before the request so the card the user dropped
     * stays where it landed; a failed write restores the previous order.
     */
    fun reorder(
        from: Int,
        to: Int,
    ) {
        val current = _state.value
        val installed = current.installed
        if (current.busyId != null || current.isInstalling) return
        if (from !in installed.indices || to !in installed.indices || from == to) return
        val reordered = installed.toMutableList().apply { add(to, removeAt(from)) }
        _state.update {
            it.copy(
                installed = reordered,
                busyId = installed[from].id,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                addons.reorder(reordered.map(Addon::id))
                val fresh = runCatchingCancellable { addons.installed() }.getOrNull()
                _state.update { it.copy(busyId = null, loadError = null) }
                fresh?.let(::applyInstalled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Keep what the backend still has instead of leaving the list
                // in an order it never accepted.
                _state.update { it.copy(installed = installed, busyId = null, error = error.toUserMessage()) }
            }
        }
    }

    /** Accessibility fallback for the drag gesture: moves a card by one step. */
    fun move(
        addon: Addon,
        offset: Int,
    ) {
        val installed = _state.value.installed
        val from = installed.indexOfFirst { it.id == addon.id }
        if (from < 0) return
        reorder(from, from + offset)
    }

    /**
     * Replaces the list and keeps only the manifest details of the addons that
     * are still installed, then loads the missing ones.
     */
    private fun applyInstalled(installed: List<Addon>) {
        val ids = installed.map(Addon::id).toSet()
        _state.update {
            it.copy(
                installed = installed,
                manifests = it.manifests.filterKeys { id -> id in ids },
                isLoading = false,
                hasLoaded = true,
                loadError = null,
            )
        }
        loadManifests(installed.filter { it.id !in _state.value.manifests })
    }

    /**
     * Reads the manifests in parallel; a failed one only leaves the row without
     * a logo and description, never without the addon itself.
     */
    private fun loadManifests(targets: List<Addon>) {
        targets.forEach { addon ->
            viewModelScope.launch {
                val manifest = addons.manifest(addon.url) ?: return@launch
                if (_state.value.installed.none { it.id == addon.id }) return@launch
                _state.update { it.copy(manifests = it.manifests + (addon.id to manifest)) }
            }
        }
    }

    /** Runs one management write, then replaces the list with the stored one. */
    private fun write(
        id: String,
        action: suspend () -> Unit,
    ) {
        val current = _state.value
        if (current.busyId != null || current.isInstalling) return
        viewModelScope.launch {
            _state.update { it.copy(busyId = id, error = null) }
            try {
                action()
                val installed = runCatchingCancellable { addons.installed() }.getOrNull()
                _state.update { it.copy(busyId = null, loadError = null) }
                installed?.let(::applyInstalled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.update { it.copy(busyId = null, error = error.toUserMessage()) }
            }
        }
    }
}

private fun AddonInstallFailure.message(): StringResource =
    when (this) {
        AddonInstallFailure.INVALID_URL -> Res.string.settings_addons_invalid_url
        AddonInstallFailure.ALREADY_INSTALLED -> Res.string.settings_addons_already_installed
        AddonInstallFailure.MANIFEST_UNREACHABLE -> Res.string.settings_addons_unreachable
        AddonInstallFailure.UNSUPPORTED -> Res.string.settings_addons_read_only
    }

/**
 * Settings page for the account's addons.
 *
 * Each card shows the addon's identity, version, description, enable switch,
 * remove action and drag handle. Only the handle starts a drag, so the card
 * itself stays inert and the page scroll keeps its own gesture.
 */
@Composable
fun AddonsSection() {
    val services = LocalAppServices.current
    val viewModel: AddonsSettingsViewModel =
        viewModel(key = "addons-settings") {
            AddonsSettingsViewModel(services.account, services.addons)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by services.account.state.collectAsStateWithLifecycle()
    val canManage = services.addons.canManage
    var showInstall by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Addon?>(null) }

    // A successful install closes the dialog and clears its field.
    LaunchedEffect(state.installSucceeded) {
        if (state.installSucceeded) {
            showInstall = false
            viewModel.clearInstall()
        }
    }

    if (!account.isSignedIn) {
        SettingsPanel {
            SettingsRow(title = stringResource(Res.string.settings_addons_signed_out))
        }
        return
    }

    if (account.requiresProfile && account.activeProfile == null) {
        SettingsPanel {
            SettingsRow(title = stringResource(Res.string.settings_addons_no_profile))
        }
        return
    }

    SettingsSubsection(
        title = stringResource(Res.string.settings_addons_installed),
        subtitle = stringResource(Res.string.settings_addons_installed_count, state.installed.size),
    )
    if (canManage) {
        ActionButton(
            text = stringResource(Res.string.settings_addons_add),
            onClick = { showInstall = true },
            icon = MaterialIcons.Plus,
            style = ActionButtonStyle.Tonal,
            enabled = !state.isInstalling && state.busyId == null,
        )
        Spacer(Modifier.height(AppSpacing.sm))
    }

    when {
        state.isLoading && !state.hasLoaded ->
            SettingsPanel {
                SettingsRow(
                    title = stringResource(Res.string.settings_addons_loading),
                    trailing = { CircularProgressIndicator(Modifier.size(AppIconSize.sm)) },
                )
            }
        state.loadError != null && state.installed.isEmpty() ->
            SettingsPanel {
                SettingsRow(
                    title = stringResource(Res.string.settings_addons_error),
                    trailing = {
                        ActionButton(
                            text = stringResource(Res.string.action_try_again),
                            onClick = { viewModel.load(force = true) },
                            style = ActionButtonStyle.Text,
                        )
                    },
                )
            }
        state.installed.isEmpty() ->
            SettingsPanel {
                SettingsRow(title = stringResource(Res.string.settings_addons_empty))
            }
        else ->
            ReorderableCardColumn(
                items = state.installed,
                key = Addon::id,
                onMove = viewModel::reorder,
                enabled = canManage && state.busyId == null && !state.isInstalling,
            ) { addon, index, scope ->
                AddonCard(
                    addon = addon,
                    manifest = state.manifests[addon.id],
                    canManage = canManage,
                    busy = state.busyId != null || state.isInstalling,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.installed.lastIndex,
                    scope = scope,
                    onToggle = { enabled -> viewModel.setEnabled(addon, enabled) },
                    onMove = { offset -> viewModel.move(addon, offset) },
                    onRemove = { pendingRemoval = addon },
                )
            }
    }

    if (!canManage) {
        SettingsPanel {
            SettingsRow(title = stringResource(Res.string.settings_addons_read_only))
        }
    }

    // An install error belongs to the dialog, not to the list behind it.
    if (!showInstall) {
        state.error?.let { error ->
            Text(
                stringResource(error),
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showInstall) {
        AddonInstallDialog(
            url = state.url,
            error = state.error,
            installing = state.isInstalling,
            onUrlChange = viewModel::onUrlChange,
            onDismiss = {
                showInstall = false
                viewModel.clearInstall()
            },
            onInstall = viewModel::install,
        )
    }

    pendingRemoval?.let { addon ->
        val name =
            addon.name?.takeIf { it.isNotBlank() }
                ?: state.manifests[addon.id]?.name?.takeIf { it.isNotBlank() }
                ?: addon.url
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(Res.string.settings_addons_remove_title)) },
            text = { Text(stringResource(Res.string.settings_addons_remove_message, name)) },
            confirmButton = {
                ActionButton(
                    text = stringResource(Res.string.action_remove),
                    onClick = {
                        pendingRemoval = null
                        viewModel.remove(addon)
                    },
                    style = ActionButtonStyle.Text,
                    enabled = !state.isInstalling && state.busyId == null,
                )
            },
            dismissButton = {
                ActionButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = { pendingRemoval = null },
                    style = ActionButtonStyle.Text,
                )
            },
        )
    }
}

/** One addon card: logo, copy, enable switch and the drag handle. */
@Composable
private fun AddonCard(
    addon: Addon,
    manifest: AddonManifest?,
    canManage: Boolean,
    busy: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    scope: ReorderableCardScope,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val name =
        addon.name?.takeIf { it.isNotBlank() }
            ?: manifest?.name?.takeIf { it.isNotBlank() }
            ?: addon.url
    val description = manifest?.description?.takeIf { it.isNotBlank() } ?: addon.url
    val version = manifest?.version?.takeIf { it.isNotBlank() }
    val toggleLabel =
        stringResource(
            if (addon.enabled) Res.string.settings_addons_disable else Res.string.settings_addons_enable,
        )
    val moveUpLabel = stringResource(Res.string.settings_addons_move_up)
    val moveDownLabel = stringResource(Res.string.settings_addons_move_down)
    val dragLabel = stringResource(Res.string.settings_addons_drag)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    customActions =
                        listOfNotNull(
                            if (canManage && canMoveUp) {
                                CustomAccessibilityAction(moveUpLabel) {
                                    onMove(-1)
                                    true
                                }
                            } else {
                                null
                            },
                            if (canManage && canMoveDown) {
                                CustomAccessibilityAction(moveDownLabel) {
                                    onMove(1)
                                    true
                                }
                            } else {
                                null
                            },
                        )
                }.padding(AppSizes.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddonLogo(logo = manifest?.logo, name = name)
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = AppType.itemTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (version != null) {
                Text(
                    stringResource(Res.string.settings_addons_version, version),
                    style = AppType.caption(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                description,
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            Spacer(Modifier.width(AppSpacing.sm))
            AppSwitch(
                checked = addon.enabled,
                onCheckedChange = onToggle,
                enabled = !busy,
                description = toggleLabel,
            )
            AppIconButton(
                icon = MaterialIcons.Delete,
                contentDescription = stringResource(Res.string.action_remove),
                onClick = onRemove,
                enabled = !busy,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier =
                    Modifier
                        .size(AppSizes.minTouchTarget)
                        .then(scope.dragHandle)
                        .semantics { contentDescription = dragLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MaterialIcons.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppIconSize.md),
                )
            }
        }
    }
}

/** Addon logo, or the name's initial while the manifest has none. */
@Composable
internal fun AddonLogo(
    logo: String?,
    name: String,
    size: Dp = AppSizes.addonLogoSize,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.small)
                .background(AppColors.surfaceSelected),
        contentAlignment = Alignment.Center,
    ) {
        if (logo.isNullOrBlank()) {
            Text(
                name.take(1).uppercase(),
                style = AppType.itemTitle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RemoteImage(
                url = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(AppSpacing.xs),
            )
        }
    }
}

/** URL form for installing an addon, opened from the "Add addon" action. */
@Composable
private fun AddonInstallDialog(
    url: String,
    error: StringResource?,
    installing: Boolean,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(Res.string.settings_addons_install)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.settings_addons_install_subtitle),
                    style = AppType.bodySecondary(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                AppTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = stringResource(Res.string.settings_addons_url),
                    singleLine = true,
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        stringResource(it),
                        style = AppType.bodySecondary(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            ActionButton(
                text = stringResource(Res.string.settings_addons_install_action),
                onClick = onInstall,
                enabled = url.isNotBlank() && !installing,
                loading = installing,
            )
        },
        dismissButton = {
            ActionButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
                style = ActionButtonStyle.Text,
            )
        },
    )
}
