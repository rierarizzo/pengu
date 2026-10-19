package com.misgus.pengu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_save
import com.misgus.pengu.app.shared.generated.resources.action_sign_out
import com.misgus.pengu.app.shared.generated.resources.error_open_file
import com.misgus.pengu.app.shared.generated.resources.poster_size_default
import com.misgus.pengu.app.shared.generated.resources.poster_size_large
import com.misgus.pengu.app.shared.generated.resources.settings_account
import com.misgus.pengu.app.shared.generated.resources.settings_animations
import com.misgus.pengu.app.shared.generated.resources.settings_animations_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_appearance
import com.misgus.pengu.app.shared.generated.resources.settings_appearance_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_autoplay
import com.misgus.pengu.app.shared.generated.resources.settings_autoplay_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_config_file
import com.misgus.pengu.app.shared.generated.resources.settings_config_file_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_hide_labels
import com.misgus.pengu.app.shared.generated.resources.settings_hide_labels_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_metadata
import com.misgus.pengu.app.shared.generated.resources.settings_metadata_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_open_file
import com.misgus.pengu.app.shared.generated.resources.settings_open_local
import com.misgus.pengu.app.shared.generated.resources.settings_open_video
import com.misgus.pengu.app.shared.generated.resources.settings_open_video_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_play_url
import com.misgus.pengu.app.shared.generated.resources.settings_playback
import com.misgus.pengu.app.shared.generated.resources.settings_playback_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_poster_size
import com.misgus.pengu.app.shared.generated.resources.settings_poster_size_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_reload
import com.misgus.pengu.app.shared.generated.resources.settings_resume
import com.misgus.pengu.app.shared.generated.resources.settings_resume_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_show_catalog_type
import com.misgus.pengu.app.shared.generated.resources.settings_show_catalog_type_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_sign_in_other_method
import com.misgus.pengu.app.shared.generated.resources.settings_sign_in_to_load
import com.misgus.pengu.app.shared.generated.resources.settings_signed_in_account
import com.misgus.pengu.app.shared.generated.resources.settings_signed_in_as
import com.misgus.pengu.app.shared.generated.resources.settings_theme
import com.misgus.pengu.app.shared.generated.resources.settings_theme_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_title
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_api_key
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_api_key_hide
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_api_key_show
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_api_key_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_attribution
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_language
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_language_english
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_language_spanish
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_language_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_language_system
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_test
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_test_invalid
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_test_ok
import com.misgus.pengu.app.shared.generated.resources.settings_tmdb_test_unreachable
import com.misgus.pengu.app.shared.generated.resources.settings_video_url
import com.misgus.pengu.app.shared.generated.resources.theme_coffee
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.AppTheme
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.repository.SignInMethod
import com.misgus.pengu.core.repository.TmdbLanguage
import com.misgus.pengu.core.repository.TmdbVerifyResult
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.AppTextField
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.onboarding.SignInForm
import com.misgus.pengu.ui.platform.openInDefaultApp
import com.misgus.pengu.ui.platform.pickVideoFile
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings section.
 *
 * Groups the local preferences (appearance, playback) and the account card. The
 * sign-in form is shared with the onboarding screen.
 */
@Composable
fun SettingsScreen(onOpenPlayer: (PlaybackTarget) -> Unit) {
    val settings = LocalAppServices.current.settings
    val state by settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<StringResource?>(null) }
    val save: (suspend () -> Unit) -> Unit = { action ->
        scope.launch {
            runCatching { action() }.onFailure { error = it.toUserMessage() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A readable column instead of a card stretched across a wide window.
        Column(
            modifier = Modifier.widthIn(max = AppSizes.contentMaxWidth).fillMaxWidth().padding(LocalWindowSize.current.screenPadding),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = AppType.screenTitle(),
                modifier = Modifier.sectionHeading(),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            AccountCard()
            Spacer(Modifier.height(AppSpacing.lg))
            AppearanceCard(
                animationsEnabled = state.animationsEnabled,
                onAnimationsChange = { value -> save { settings.setAnimationsEnabled(value) } },
                theme = state.theme,
                onThemeChange = { value -> save { settings.setTheme(value) } },
                posterSize = state.posterSize,
                onPosterSizeChange = { value -> save { settings.setPosterSize(value) } },
                hideLabels = state.hideLabels,
                onHideLabelsChange = { value -> save { settings.setHideLabels(value) } },
                showCatalogType = state.showCatalogType,
                onShowCatalogTypeChange = { value -> save { settings.setShowCatalogType(value) } },
            )
            Spacer(Modifier.height(AppSpacing.lg))
            PlaybackSettingsCard(
                resumePlayback = state.resumePlayback,
                autoPlayNext = state.autoPlayNext,
                onResumeChange = { value -> save { settings.setResumePlayback(value) } },
                onAutoPlayChange = { value -> save { settings.setAutoPlayNext(value) } },
            )
            Spacer(Modifier.height(AppSpacing.lg))
            MetadataCard(
                apiKey = state.tmdbApiKey,
                language = state.tmdbLanguage,
                onSaveKey = { value -> save { settings.setTmdbApiKey(value) } },
                onLanguageChange = { value -> save { settings.setTmdbLanguage(value) } },
            )
            Spacer(Modifier.height(AppSpacing.lg))
            ConfigFileCard(
                filePath = settings.filePath,
                onOpen = {
                    save {
                        if (!openInDefaultApp(settings.filePath)) {
                            error = Res.string.error_open_file
                        }
                    }
                },
                onReload = { save { settings.reload() } },
            )
            error?.let {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(stringResource(it), style = AppType.bodySecondary(), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(AppSpacing.lg))
            PlaybackCard(onOpenPlayer)
        }
    }
}

@Composable
private fun AccountCard() {
    val account = LocalAppServices.current.account
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }

    val unknownAccount = stringResource(Res.string.settings_signed_in_account)
    SettingsCard(
        title = stringResource(Res.string.settings_account),
        subtitle =
            if (state.isSignedIn) {
                stringResource(Res.string.settings_signed_in_as, state.email ?: unknownAccount)
            } else {
                stringResource(Res.string.settings_sign_in_to_load)
            },
    ) {
        if (state.isSignedIn) {
            ActionButton(
                text = stringResource(Res.string.action_sign_out),
                onClick = {
                    signingOut = true
                    scope.launch {
                        try {
                            account.signOut()
                        } finally {
                            signingOut = false
                        }
                    }
                },
                icon = FluentIcons.Logout,
                style = ActionButtonStyle.Tonal,
                enabled = !signingOut,
                loading = signingOut,
            )
        } else if (SignInMethod.EmailPassword in account.signInMethods) {
            SignInForm()
        } else {
            Text(
                stringResource(Res.string.settings_sign_in_other_method),
                style = AppType.bodySecondary(),
            )
        }
    }
}

@Composable
private fun AppearanceCard(
    animationsEnabled: Boolean,
    onAnimationsChange: (Boolean) -> Unit,
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    posterSize: PosterSize,
    onPosterSizeChange: (PosterSize) -> Unit,
    hideLabels: Boolean,
    onHideLabelsChange: (Boolean) -> Unit,
    showCatalogType: Boolean,
    onShowCatalogTypeChange: (Boolean) -> Unit,
) {
    val themeLabels = mapOf(AppTheme.COFFEE to stringResource(Res.string.theme_coffee))
    val posterSizeLabels =
        mapOf(
            PosterSize.DEFAULT to stringResource(Res.string.poster_size_default),
            PosterSize.LARGE to stringResource(Res.string.poster_size_large),
        )
    SettingsCard(
        title = stringResource(Res.string.settings_appearance),
        subtitle = stringResource(Res.string.settings_appearance_subtitle),
    ) {
        Column {
            SettingsToggle(
                title = stringResource(Res.string.settings_animations),
                subtitle = stringResource(Res.string.settings_animations_subtitle),
                checked = animationsEnabled,
                onCheckedChange = onAnimationsChange,
            )
            SettingsChoice(
                title = stringResource(Res.string.settings_theme),
                subtitle = stringResource(Res.string.settings_theme_subtitle),
                options = AppTheme.entries,
                selected = theme,
                label = { themeLabels.getValue(it) },
                onSelect = onThemeChange,
            )
            SettingsChoice(
                title = stringResource(Res.string.settings_poster_size),
                subtitle = stringResource(Res.string.settings_poster_size_subtitle),
                options = PosterSize.entries,
                selected = posterSize,
                label = { posterSizeLabels.getValue(it) },
                onSelect = onPosterSizeChange,
            )
            SettingsToggle(
                title = stringResource(Res.string.settings_hide_labels),
                subtitle = stringResource(Res.string.settings_hide_labels_subtitle),
                checked = hideLabels,
                onCheckedChange = onHideLabelsChange,
            )
            SettingsToggle(
                title = stringResource(Res.string.settings_show_catalog_type),
                subtitle = stringResource(Res.string.settings_show_catalog_type_subtitle),
                checked = showCatalogType,
                onCheckedChange = onShowCatalogTypeChange,
            )
        }
    }
}

@Composable
private fun PlaybackSettingsCard(
    resumePlayback: Boolean,
    autoPlayNext: Boolean,
    onResumeChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
) {
    SettingsCard(
        title = stringResource(Res.string.settings_playback),
        subtitle = stringResource(Res.string.settings_playback_subtitle),
    ) {
        Column {
            SettingsToggle(
                title = stringResource(Res.string.settings_resume),
                subtitle = stringResource(Res.string.settings_resume_subtitle),
                checked = resumePlayback,
                onCheckedChange = onResumeChange,
            )
            SettingsToggle(
                title = stringResource(Res.string.settings_autoplay),
                subtitle = stringResource(Res.string.settings_autoplay_subtitle),
                checked = autoPlayNext,
                onCheckedChange = onAutoPlayChange,
            )
        }
    }
}

/**
 * Optional TMDB enrichment: the API key, the answer language, a connection
 * test and the attribution the TMDB terms require.
 *
 * The key lives in `settings.json` like the rest of the preferences, so it stays
 * hand-editable; it is masked here and never logged.
 */
@Composable
private fun MetadataCard(
    apiKey: String,
    language: TmdbLanguage,
    onSaveKey: (String) -> Unit,
    onLanguageChange: (TmdbLanguage) -> Unit,
) {
    val tmdb = LocalAppServices.current.tmdb
    val scope = rememberCoroutineScope()
    var draft by remember(apiKey) { mutableStateOf(apiKey) }
    var visible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember(apiKey, draft) { mutableStateOf<TmdbVerifyResult?>(null) }
    val languageLabels =
        mapOf(
            TmdbLanguage.SYSTEM to stringResource(Res.string.settings_tmdb_language_system),
            TmdbLanguage.ENGLISH to stringResource(Res.string.settings_tmdb_language_english),
            TmdbLanguage.SPANISH to stringResource(Res.string.settings_tmdb_language_spanish),
        )
    SettingsCard(
        title = stringResource(Res.string.settings_metadata),
        subtitle = stringResource(Res.string.settings_metadata_subtitle),
    ) {
        Column {
            AppTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    result = null
                },
                label = stringResource(Res.string.settings_tmdb_api_key),
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    AppIconButton(
                        icon = if (visible) FluentIcons.VisibilityOff else FluentIcons.Visibility,
                        contentDescription =
                            stringResource(
                                if (visible) Res.string.settings_tmdb_api_key_hide else Res.string.settings_tmdb_api_key_show,
                            ),
                        onClick = { visible = !visible },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                stringResource(Res.string.settings_tmdb_api_key_subtitle),
                style = AppType.caption(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsChoice(
                title = stringResource(Res.string.settings_tmdb_language),
                subtitle = stringResource(Res.string.settings_tmdb_language_subtitle),
                options = TmdbLanguage.entries,
                selected = language,
                label = { languageLabels.getValue(it) },
                onSelect = onLanguageChange,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ActionButton(
                    text = stringResource(Res.string.action_save),
                    onClick = { onSaveKey(draft.trim()) },
                    enabled = draft.trim() != apiKey,
                )
                ActionButton(
                    text = stringResource(Res.string.settings_tmdb_test),
                    onClick = {
                        testing = true
                        result = null
                        scope.launch {
                            result = tmdb.verify(draft.trim())
                            testing = false
                        }
                    },
                    style = ActionButtonStyle.Tonal,
                    loading = testing,
                    enabled = draft.isNotBlank() && !testing,
                )
            }
            result?.let { outcome ->
                Spacer(Modifier.height(AppSpacing.sm))
                val message =
                    when (outcome) {
                        TmdbVerifyResult.VALID -> Res.string.settings_tmdb_test_ok
                        TmdbVerifyResult.INVALID_KEY -> Res.string.settings_tmdb_test_invalid
                        TmdbVerifyResult.UNREACHABLE -> Res.string.settings_tmdb_test_unreachable
                    }
                Text(
                    stringResource(message),
                    style = AppType.bodySecondary(),
                    color = if (outcome == TmdbVerifyResult.VALID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                stringResource(Res.string.settings_tmdb_attribution),
                style = AppType.caption(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigFileCard(
    filePath: String,
    onOpen: () -> Unit,
    onReload: () -> Unit,
) {
    SettingsCard(
        title = stringResource(Res.string.settings_config_file),
        subtitle = stringResource(Res.string.settings_config_file_subtitle),
    ) {
        Column {
            Text(
                filePath,
                style = AppType.caption(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ActionButton(
                    text = stringResource(Res.string.settings_open_file),
                    onClick = onOpen,
                    style = ActionButtonStyle.Tonal,
                )
                ActionButton(
                    text = stringResource(Res.string.settings_reload),
                    onClick = onReload,
                    style = ActionButtonStyle.Tonal,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.itemTitle())
            Text(subtitle, style = AppType.bodySecondary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(AppSpacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PlaybackCard(onOpenPlayer: (PlaybackTarget) -> Unit) {
    var url by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    SettingsCard(
        title = stringResource(Res.string.settings_open_video),
        subtitle = stringResource(Res.string.settings_open_video_subtitle),
    ) {
        Column {
            AppTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(Res.string.settings_video_url),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ActionButton(
                    text = stringResource(Res.string.settings_play_url),
                    onClick = { onOpenPlayer(PlaybackTarget.raw(url.trim())) },
                    icon = FluentIcons.Play,
                    enabled = url.isNotBlank(),
                )
                ActionButton(
                    text = stringResource(Res.string.settings_open_local),
                    onClick = {
                        scope.launch {
                            pickVideoFile()?.let { onOpenPlayer(PlaybackTarget.raw(it)) }
                        }
                    },
                    icon = FluentIcons.FolderOpen,
                    style = ActionButtonStyle.Tonal,
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsChoice(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
        Text(title, style = AppType.itemTitle())
        Text(subtitle, style = AppType.bodySecondary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Text(title, style = AppType.cardTitle(), modifier = Modifier.sectionHeading())
            Spacer(Modifier.height(AppSpacing.xs))
            Text(subtitle, style = AppType.bodySecondary())
            Spacer(Modifier.height(AppSpacing.md))
            content()
        }
    }
}
