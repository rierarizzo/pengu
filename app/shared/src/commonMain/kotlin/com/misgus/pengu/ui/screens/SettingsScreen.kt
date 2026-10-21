package com.misgus.pengu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_back
import com.misgus.pengu.app.shared.generated.resources.action_open
import com.misgus.pengu.app.shared.generated.resources.action_save
import com.misgus.pengu.app.shared.generated.resources.action_sign_out
import com.misgus.pengu.app.shared.generated.resources.action_try_again
import com.misgus.pengu.app.shared.generated.resources.error_open_file
import com.misgus.pengu.app.shared.generated.resources.poster_size_default
import com.misgus.pengu.app.shared.generated.resources.poster_size_large
import com.misgus.pengu.app.shared.generated.resources.settings_about_credits
import com.misgus.pengu.app.shared.generated.resources.settings_about_description
import com.misgus.pengu.app.shared.generated.resources.settings_about_license
import com.misgus.pengu.app.shared.generated.resources.settings_about_license_text
import com.misgus.pengu.app.shared.generated.resources.settings_about_mediamp
import com.misgus.pengu.app.shared.generated.resources.settings_about_mediamp_name
import com.misgus.pengu.app.shared.generated.resources.settings_about_pengu
import com.misgus.pengu.app.shared.generated.resources.settings_about_read_license
import com.misgus.pengu.app.shared.generated.resources.settings_about_stremio
import com.misgus.pengu.app.shared.generated.resources.settings_about_stremio_name
import com.misgus.pengu.app.shared.generated.resources.settings_about_tmdb_name
import com.misgus.pengu.app.shared.generated.resources.settings_account
import com.misgus.pengu.app.shared.generated.resources.settings_animations
import com.misgus.pengu.app.shared.generated.resources.settings_animations_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_autoplay
import com.misgus.pengu.app.shared.generated.resources.settings_autoplay_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_config_file
import com.misgus.pengu.app.shared.generated.resources.settings_config_file_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_deband
import com.misgus.pengu.app.shared.generated.resources.settings_deband_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_hardware_decoding
import com.misgus.pengu.app.shared.generated.resources.settings_hardware_decoding_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_hero
import com.misgus.pengu.app.shared.generated.resources.settings_hero_catalogs
import com.misgus.pengu.app.shared.generated.resources.settings_hero_catalogs_empty
import com.misgus.pengu.app.shared.generated.resources.settings_hero_catalogs_error
import com.misgus.pengu.app.shared.generated.resources.settings_hero_catalogs_loading
import com.misgus.pengu.app.shared.generated.resources.settings_hero_catalogs_selected
import com.misgus.pengu.app.shared.generated.resources.settings_hero_manual
import com.misgus.pengu.app.shared.generated.resources.settings_hero_smart
import com.misgus.pengu.app.shared.generated.resources.settings_hero_source
import com.misgus.pengu.app.shared.generated.resources.settings_hero_source_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_hero_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_hide_labels
import com.misgus.pengu.app.shared.generated.resources.settings_hide_labels_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_open_file
import com.misgus.pengu.app.shared.generated.resources.settings_open_local
import com.misgus.pengu.app.shared.generated.resources.settings_open_video
import com.misgus.pengu.app.shared.generated.resources.settings_open_video_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_play_url
import com.misgus.pengu.app.shared.generated.resources.settings_player_engine
import com.misgus.pengu.app.shared.generated.resources.settings_player_engine_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_poster_size
import com.misgus.pengu.app.shared.generated.resources.settings_poster_size_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_posters
import com.misgus.pengu.app.shared.generated.resources.settings_posters_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_reload
import com.misgus.pengu.app.shared.generated.resources.settings_resume
import com.misgus.pengu.app.shared.generated.resources.settings_resume_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_show_catalog_type
import com.misgus.pengu.app.shared.generated.resources.settings_show_catalog_type_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_sign_in_other_method
import com.misgus.pengu.app.shared.generated.resources.settings_sign_in_to_load
import com.misgus.pengu.app.shared.generated.resources.settings_signed_in_account
import com.misgus.pengu.app.shared.generated.resources.settings_signed_in_as
import com.misgus.pengu.app.shared.generated.resources.settings_stream_cache
import com.misgus.pengu.app.shared.generated.resources.settings_stream_cache_1d
import com.misgus.pengu.app.shared.generated.resources.settings_stream_cache_1h
import com.misgus.pengu.app.shared.generated.resources.settings_stream_cache_6h
import com.misgus.pengu.app.shared.generated.resources.settings_stream_cache_off
import com.misgus.pengu.app.shared.generated.resources.settings_stream_cache_subtitle
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
import com.misgus.pengu.app.shared.generated.resources.settings_tone_mapping
import com.misgus.pengu.app.shared.generated.resources.settings_tone_mapping_auto
import com.misgus.pengu.app.shared.generated.resources.settings_tone_mapping_bt2390
import com.misgus.pengu.app.shared.generated.resources.settings_tone_mapping_hable
import com.misgus.pengu.app.shared.generated.resources.settings_tone_mapping_mobius
import com.misgus.pengu.app.shared.generated.resources.settings_tone_mapping_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_typeface
import com.misgus.pengu.app.shared.generated.resources.settings_typeface_ibm_plex
import com.misgus.pengu.app.shared.generated.resources.settings_typeface_inter
import com.misgus.pengu.app.shared.generated.resources.settings_typeface_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_video_url
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.AppTypeface
import com.misgus.pengu.core.repository.CatalogRef
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.MAX_HERO_CATALOGS
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.SignInMethod
import com.misgus.pengu.core.repository.StreamUrlCache
import com.misgus.pengu.core.repository.TmdbLanguage
import com.misgus.pengu.core.repository.TmdbVerifyResult
import com.misgus.pengu.core.repository.ToneMapping
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.AppTextField
import com.misgus.pengu.ui.components.contentTypeLabel
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.onboarding.SignInForm
import com.misgus.pengu.ui.platform.isDesktop
import com.misgus.pengu.ui.platform.openInDefaultApp
import com.misgus.pengu.ui.platform.pickVideoFile
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.WindowSize
import com.misgus.pengu.ui.theme.appFontFamily
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Runs a settings write, routing a failure to the page's error line. */
private typealias Save = (suspend () -> Unit) -> Unit

/**
 * Settings.
 *
 * A wide window shows the categories in a sidebar next to the selected page; a
 * compact window shows them as an index and the caller pushes the page, so a
 * phone walks into one category at a time. Both read the same pages, so a
 * setting is written and labelled in one place.
 *
 * [onOpenCategory] is only used on a compact window; the wide one keeps the
 * selection in place.
 */
@Composable
fun SettingsScreen(
    onOpenPlayer: (PlaybackTarget) -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
) {
    if (LocalWindowSize.current == WindowSize.EXPANDED) {
        WideSettings(onOpenPlayer)
    } else {
        CompactSettings(onOpenCategory)
    }
}

/** Sidebar plus the selected page, for a window wide enough to hold both. */
@Composable
private fun WideSettings(onOpenPlayer: (PlaybackTarget) -> Unit) {
    var selectedName by rememberSaveable { mutableStateOf(SettingsCategory.PLAYBACK.name) }
    val selected = SettingsCategory.valueOf(selectedName)
    val padding = LocalWindowSize.current.screenPadding
    val contentScroll = rememberScrollState()
    // A new page opens at its top instead of inheriting the previous scroll.
    LaunchedEffect(selected) { contentScroll.scrollTo(0) }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .width(AppSizes.settingsSidebarWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = padding, end = AppSpacing.md, top = padding, bottom = padding),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = AppType.screenTitle(),
                modifier = Modifier.sectionHeading(),
            )
            Spacer(Modifier.height(AppSpacing.md))
            SettingsSidebar(selected = selected, onSelect = { selectedName = it.name })
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(contentScroll)
                    .padding(start = AppSpacing.lg, end = padding, top = padding, bottom = padding),
        ) {
            SettingsCategoryContent(selected, onOpenPlayer)
        }
    }
}

/** Index of every category, for a window too narrow for the sidebar. */
@Composable
private fun CompactSettings(onOpenCategory: (SettingsCategory) -> Unit) {
    val padding = LocalWindowSize.current.screenPadding
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
    ) {
        Text(
            stringResource(Res.string.settings_title),
            style = AppType.screenTitle(),
            modifier = Modifier.sectionHeading(),
        )
        Spacer(Modifier.height(AppSpacing.md))
        SettingsIndex(onOpen = onOpenCategory)
    }
}

/**
 * One category as a pushed route, for a compact window.
 *
 * A pushed route hides the shell chrome, so it paints its own back affordance
 * and the "Settings" title, then the page below.
 */
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    onOpenPlayer: (PlaybackTarget) -> Unit,
) {
    val padding = LocalWindowSize.current.screenPadding
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(padding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                icon = MaterialIcons.ArrowBack,
                contentDescription = stringResource(Res.string.action_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                stringResource(Res.string.settings_title),
                style = AppType.screenTitle(),
                modifier = Modifier.sectionHeading(),
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        SettingsCategoryContent(category, onOpenPlayer)
    }
}

/**
 * The selected page: a header, the rows of the category and the error line of
 * the last failed write.
 */
@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    onOpenPlayer: (PlaybackTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppServices.current.settings
    val state by settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<StringResource?>(null) }
    // The wide window swaps the page in place, so a stale error would follow the
    // user into the next category.
    LaunchedEffect(category) { error = null }
    val save: Save = { action ->
        scope.launch {
            runCatching { action() }.onFailure { error = it.toUserMessage() }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        SettingsCategoryHeader(category)
        when (category) {
            SettingsCategory.PLAYBACK -> PlaybackSection(state, settings, save)
            SettingsCategory.APPEARANCE -> AppearanceSection(state, settings, save)
            SettingsCategory.HOME -> HomeSection(state, settings, save)
            SettingsCategory.ADDONS -> AddonsSection()
            SettingsCategory.METADATA -> MetadataSection(state, settings, save)
            SettingsCategory.ACCOUNT -> AccountSection()
            SettingsCategory.ABOUT -> AboutSection()
            SettingsCategory.ADVANCED ->
                AdvancedSection(
                    settings = settings,
                    save = save,
                    onError = { error = it },
                    onOpenPlayer = onOpenPlayer,
                )
        }
        error?.let {
            Text(
                stringResource(it),
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PlaybackSection(
    state: AppSettings,
    settings: SettingsRepository,
    save: Save,
) {
    SettingsPanel {
        SettingsToggleRow(
            title = stringResource(Res.string.settings_resume),
            description = stringResource(Res.string.settings_resume_subtitle),
            checked = state.resumePlayback,
            onCheckedChange = { value -> save { settings.setResumePlayback(value) } },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(Res.string.settings_autoplay),
            description = stringResource(Res.string.settings_autoplay_subtitle),
            checked = state.autoPlayNext,
            onCheckedChange = { value -> save { settings.setAutoPlayNext(value) } },
        )
        SettingsDivider()
        val streamCacheLabels =
            mapOf(
                StreamUrlCache.ONE_HOUR to stringResource(Res.string.settings_stream_cache_1h),
                StreamUrlCache.SIX_HOURS to stringResource(Res.string.settings_stream_cache_6h),
                StreamUrlCache.ONE_DAY to stringResource(Res.string.settings_stream_cache_1d),
                StreamUrlCache.OFF to stringResource(Res.string.settings_stream_cache_off),
            )
        SettingsChipRow(
            title = stringResource(Res.string.settings_stream_cache),
            description = stringResource(Res.string.settings_stream_cache_subtitle),
            options = StreamUrlCache.entries,
            selected = state.streamUrlCache,
            label = { streamCacheLabels.getValue(it) },
            onSelect = { value -> save { settings.setStreamUrlCache(value) } },
        )
    }
    // The engine options are mpv's: Android has no equivalent knob, so the panel
    // is not shown there instead of offering switches that do nothing.
    if (isDesktop) DesktopPlayerPanel(state, settings, save)
}

/**
 * Options handed straight to the mpv engine.
 *
 * They are the levers that matter when a title looks wrong (HDR mapped too dark,
 * banding, a source that only decodes in software), and every one of them takes
 * effect on the next video: the player re-applies them when it opens a target.
 */
@Composable
private fun DesktopPlayerPanel(
    state: AppSettings,
    settings: SettingsRepository,
    save: Save,
) {
    val toneMappingLabels =
        mapOf(
            ToneMapping.AUTO to stringResource(Res.string.settings_tone_mapping_auto),
            ToneMapping.BT_2390 to stringResource(Res.string.settings_tone_mapping_bt2390),
            ToneMapping.HABLE to stringResource(Res.string.settings_tone_mapping_hable),
            ToneMapping.MOBIUS to stringResource(Res.string.settings_tone_mapping_mobius),
        )
    SettingsSubsection(
        title = stringResource(Res.string.settings_player_engine),
        subtitle = stringResource(Res.string.settings_player_engine_subtitle),
    )
    SettingsPanel {
        SettingsChipRow(
            title = stringResource(Res.string.settings_tone_mapping),
            description = stringResource(Res.string.settings_tone_mapping_subtitle),
            options = ToneMapping.entries,
            selected = state.toneMapping,
            label = { toneMappingLabels.getValue(it) },
            onSelect = { value -> save { settings.setToneMapping(value) } },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(Res.string.settings_deband),
            description = stringResource(Res.string.settings_deband_subtitle),
            checked = state.deband,
            onCheckedChange = { value -> save { settings.setDeband(value) } },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(Res.string.settings_hardware_decoding),
            description = stringResource(Res.string.settings_hardware_decoding_subtitle),
            checked = state.hardwareDecoding,
            onCheckedChange = { value -> save { settings.setHardwareDecoding(value) } },
        )
    }
}

@Composable
private fun AppearanceSection(
    state: AppSettings,
    settings: SettingsRepository,
    save: Save,
) {
    val typefaceLabels =
        mapOf(
            AppTypeface.IBM_PLEX_SANS to stringResource(Res.string.settings_typeface_ibm_plex),
            AppTypeface.INTER to stringResource(Res.string.settings_typeface_inter),
        )
    SettingsPanel {
        SettingsChipRow(
            title = stringResource(Res.string.settings_typeface),
            description = stringResource(Res.string.settings_typeface_subtitle),
            options = AppTypeface.entries,
            selected = state.typeface,
            label = { typefaceLabels.getValue(it) },
            onSelect = { value -> save { settings.setTypeface(value) } },
            // Each option is set in its own family, so the picker is its preview.
            labelFamily = { appFontFamily(it) },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(Res.string.settings_animations),
            description = stringResource(Res.string.settings_animations_subtitle),
            checked = state.animationsEnabled,
            onCheckedChange = { value -> save { settings.setAnimationsEnabled(value) } },
        )
    }
}

/**
 * The Home page: the billboard and the poster rows.
 *
 * Both decide how the feed looks, so they share a page instead of living under a
 * generic "interface" entry: the billboard first, then the poster rows under
 * their own heading.
 */
@Composable
private fun HomeSection(
    state: AppSettings,
    settings: SettingsRepository,
    save: Save,
) {
    val posterSizeLabels =
        mapOf(
            PosterSize.DEFAULT to stringResource(Res.string.poster_size_default),
            PosterSize.LARGE to stringResource(Res.string.poster_size_large),
        )

    SettingsSubsection(
        title = stringResource(Res.string.settings_hero),
        subtitle = stringResource(Res.string.settings_hero_subtitle),
    )
    BillboardPanel(state, settings, save)

    SettingsSubsection(
        title = stringResource(Res.string.settings_posters),
        subtitle = stringResource(Res.string.settings_posters_subtitle),
    )
    SettingsPanel {
        SettingsChipRow(
            title = stringResource(Res.string.settings_poster_size),
            description = stringResource(Res.string.settings_poster_size_subtitle),
            options = PosterSize.entries,
            selected = state.posterSize,
            label = { posterSizeLabels.getValue(it) },
            onSelect = { value -> save { settings.setPosterSize(value) } },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(Res.string.settings_hide_labels),
            description = stringResource(Res.string.settings_hide_labels_subtitle),
            checked = state.hideLabels,
            onCheckedChange = { value -> save { settings.setHideLabels(value) } },
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(Res.string.settings_show_catalog_type),
            description = stringResource(Res.string.settings_show_catalog_type_subtitle),
            checked = state.showCatalogType,
            onCheckedChange = { value -> save { settings.setShowCatalogType(value) } },
        )
    }
}

/**
 * Source of the featured titles on Home.
 *
 * Smart is the default: the app mixes every loaded row. Manual lets the user pin
 * up to [MAX_HERO_CATALOGS] catalogs, which is what Nuvio offers; the catalog
 * list is read here instead of in a ViewModel because it is the only page that
 * needs the names, not just the keys.
 */
@Composable
private fun BillboardPanel(
    state: AppSettings,
    settings: SettingsRepository,
    save: Save,
) {
    val catalogs = LocalAppServices.current.catalogs
    val sourceLabels =
        mapOf(
            HeroSource.SMART to stringResource(Res.string.settings_hero_smart),
            HeroSource.MANUAL to stringResource(Res.string.settings_hero_manual),
        )
    var options by remember { mutableStateOf<List<CatalogRef>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    // Only a manual billboard needs the list, and the addons can change while the
    // app is open, so it is read when the mode is picked and on every retry.
    LaunchedEffect(state.heroSource, attempt) {
        if (state.heroSource != HeroSource.MANUAL || (options != null && attempt == 0)) return@LaunchedEffect
        val loaded = runCatchingCancellable { catalogs.catalogs().filter { it.browsable }.distinctBy { it.key } }
        options = loaded.getOrNull()
        failed = loaded.isFailure
    }

    SettingsPanel {
        SettingsChipRow(
            title = stringResource(Res.string.settings_hero_source),
            description = stringResource(Res.string.settings_hero_source_subtitle),
            options = HeroSource.entries,
            selected = state.heroSource,
            label = { sourceLabels.getValue(it) },
            onSelect = { value -> save { settings.setHeroSource(value) } },
        )
    }

    // Smart draws from every row, so only Manual has anything to pick.
    if (state.heroSource != HeroSource.MANUAL) return

    SettingsSubsection(
        title = stringResource(Res.string.settings_hero_catalogs),
        subtitle = stringResource(Res.string.settings_hero_catalogs_selected, state.heroCatalogKeys.size, MAX_HERO_CATALOGS),
    )
    SettingsPanel {
        when {
            failed ->
                SettingsRow(
                    title = stringResource(Res.string.settings_hero_catalogs_error),
                    trailing = {
                        ActionButton(
                            text = stringResource(Res.string.action_try_again),
                            onClick = { attempt++ },
                            style = ActionButtonStyle.Text,
                        )
                    },
                )
            options == null ->
                SettingsRow(
                    title = stringResource(Res.string.settings_hero_catalogs_loading),
                    trailing = { CircularProgressIndicator(Modifier.size(AppIconSize.sm)) },
                )
            options.orEmpty().isEmpty() ->
                SettingsRow(title = stringResource(Res.string.settings_hero_catalogs_empty))
            else ->
                options.orEmpty().forEachIndexed { index, ref ->
                    if (index > 0) SettingsDivider()
                    HeroCatalogRow(
                        ref = ref,
                        selected = ref.key in state.heroCatalogKeys,
                        enabled = ref.key in state.heroCatalogKeys || state.heroCatalogKeys.size < MAX_HERO_CATALOGS,
                        onToggle = { checked ->
                            save {
                                settings.setHeroCatalogKeys(
                                    if (checked) state.heroCatalogKeys + ref.key else state.heroCatalogKeys - ref.key,
                                )
                            }
                        },
                    )
                }
        }
    }
}

/** One catalog a manual billboard can draw from. */
@Composable
private fun HeroCatalogRow(
    ref: CatalogRef,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val typeLabel = contentTypeLabel(ref.type)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSizes.cardPadding, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = onToggle, enabled = enabled)
        Spacer(Modifier.width(AppSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                ref.name,
                style = AppType.itemTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val subtitle = listOfNotNull(ref.addonName.takeIf { it.isNotBlank() }, typeLabel).joinToString("  ·  ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = AppType.caption(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * Optional TMDB enrichment: the API key, the answer language, a connection test
 * and the attribution the TMDB terms require.
 *
 * The key lives in `settings.json` like the rest of the preferences, so it stays
 * hand-editable; it is masked here and never logged.
 */
@Composable
private fun MetadataSection(
    state: AppSettings,
    settings: SettingsRepository,
    save: Save,
) {
    val tmdb = LocalAppServices.current.tmdb
    val scope = rememberCoroutineScope()
    var draft by remember(state.tmdbApiKey) { mutableStateOf(state.tmdbApiKey) }
    var visible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember(state.tmdbApiKey, draft) { mutableStateOf<TmdbVerifyResult?>(null) }
    val languageLabels =
        mapOf(
            TmdbLanguage.SYSTEM to stringResource(Res.string.settings_tmdb_language_system),
            TmdbLanguage.ENGLISH to stringResource(Res.string.settings_tmdb_language_english),
            TmdbLanguage.SPANISH to stringResource(Res.string.settings_tmdb_language_spanish),
        )
    SettingsPanel {
        Column(Modifier.fillMaxWidth().padding(AppSizes.cardPadding)) {
            Text(stringResource(Res.string.settings_tmdb_api_key), style = AppType.itemTitle())
            Text(
                stringResource(Res.string.settings_tmdb_api_key_subtitle),
                style = AppType.bodySecondary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            AppTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    result = null
                },
                placeholder = stringResource(Res.string.settings_tmdb_api_key),
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    AppIconButton(
                        icon = if (visible) MaterialIcons.VisibilityOff else MaterialIcons.Visibility,
                        contentDescription =
                            stringResource(
                                if (visible) Res.string.settings_tmdb_api_key_hide else Res.string.settings_tmdb_api_key_show,
                            ),
                        onClick = { visible = !visible },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ActionButton(
                    text = stringResource(Res.string.action_save),
                    onClick = { save { settings.setTmdbApiKey(draft.trim()) } },
                    enabled = draft.trim() != state.tmdbApiKey,
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
        }
        SettingsDivider()
        SettingsSelectRow(
            title = stringResource(Res.string.settings_tmdb_language),
            description = stringResource(Res.string.settings_tmdb_language_subtitle),
            options = TmdbLanguage.entries,
            selected = state.tmdbLanguage,
            label = { languageLabels.getValue(it) },
            onSelect = { value -> save { settings.setTmdbLanguage(value) } },
        )
    }
}

@Composable
private fun AccountSection() {
    val account = LocalAppServices.current.account
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    val unknownAccount = stringResource(Res.string.settings_signed_in_account)

    SettingsPanel {
        when {
            state.isSignedIn ->
                SettingsRow(
                    title = stringResource(Res.string.settings_account),
                    description = stringResource(Res.string.settings_signed_in_as, state.email ?: unknownAccount),
                    trailing = {
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
                            icon = MaterialIcons.Logout,
                            style = ActionButtonStyle.Tonal,
                            enabled = !signingOut,
                            loading = signingOut,
                        )
                    },
                )
            SignInMethod.EmailPassword in account.signInMethods ->
                Column(Modifier.fillMaxWidth().padding(AppSizes.cardPadding)) {
                    Text(
                        stringResource(Res.string.settings_sign_in_to_load),
                        style = AppType.bodySecondary(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    SignInForm()
                }
            else ->
                SettingsRow(title = stringResource(Res.string.settings_sign_in_other_method))
        }
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    SettingsSubsection(
        title = stringResource(Res.string.settings_about_pengu),
        subtitle = stringResource(Res.string.settings_about_description),
    )
    SettingsSubsection(
        title = stringResource(Res.string.settings_about_license),
        subtitle = stringResource(Res.string.settings_about_license_text),
    )
    SettingsPanel {
        SettingsRow(
            title = stringResource(Res.string.settings_about_read_license),
            trailing = {
                ActionButton(
                    text = stringResource(Res.string.action_open),
                    onClick = { uriHandler.openUri(LICENSE_URL) },
                    style = ActionButtonStyle.Text,
                )
            },
        )
    }
    SettingsSubsection(title = stringResource(Res.string.settings_about_credits))
    SettingsPanel {
        AboutLinkRow(
            title = Res.string.settings_about_mediamp_name,
            description = Res.string.settings_about_mediamp,
            url = MEDIAMP_URL,
        )
        SettingsDivider()
        AboutLinkRow(
            title = Res.string.settings_about_stremio_name,
            description = Res.string.settings_about_stremio,
            url = STREMIO_PROTOCOL_URL,
        )
        SettingsDivider()
        AboutLinkRow(
            title = Res.string.settings_about_tmdb_name,
            description = Res.string.settings_tmdb_attribution,
            url = TMDB_URL,
        )
    }
}

/** One credited project: what it does, and a link to it. */
@Composable
private fun AboutLinkRow(
    title: StringResource,
    description: StringResource,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    SettingsRow(
        title = stringResource(title),
        description = stringResource(description),
        trailing = {
            ActionButton(
                text = stringResource(Res.string.action_open),
                onClick = { uriHandler.openUri(url) },
                style = ActionButtonStyle.Text,
            )
        },
    )
}

private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val MEDIAMP_URL = "https://github.com/open-ani/mediamp"
private const val STREMIO_PROTOCOL_URL = "https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md"
private const val TMDB_URL = "https://www.themoviedb.org"

@Composable
private fun AdvancedSection(
    settings: SettingsRepository,
    save: Save,
    onError: (StringResource) -> Unit,
    onOpenPlayer: (PlaybackTarget) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    SettingsSubsection(
        title = stringResource(Res.string.settings_config_file),
        subtitle = stringResource(Res.string.settings_config_file_subtitle),
    )
    SettingsPanel {
        Column(Modifier.fillMaxWidth().padding(AppSizes.cardPadding)) {
            Text(
                settings.filePath,
                style = AppType.caption(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ActionButton(
                    text = stringResource(Res.string.settings_open_file),
                    onClick = {
                        save {
                            if (!openInDefaultApp(settings.filePath)) {
                                onError(Res.string.error_open_file)
                            }
                        }
                    },
                    style = ActionButtonStyle.Tonal,
                )
                ActionButton(
                    text = stringResource(Res.string.settings_reload),
                    onClick = { save { settings.reload() } },
                    style = ActionButtonStyle.Tonal,
                )
            }
        }
    }

    SettingsSubsection(
        title = stringResource(Res.string.settings_open_video),
        subtitle = stringResource(Res.string.settings_open_video_subtitle),
    )
    SettingsPanel {
        Column(Modifier.fillMaxWidth().padding(AppSizes.cardPadding)) {
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
                    icon = MaterialIcons.Play,
                    enabled = url.isNotBlank(),
                )
                ActionButton(
                    text = stringResource(Res.string.settings_open_local),
                    onClick = {
                        scope.launch {
                            pickVideoFile()?.let { onOpenPlayer(PlaybackTarget.raw(it)) }
                        }
                    },
                    icon = MaterialIcons.FolderOpen,
                    style = ActionButtonStyle.Tonal,
                )
            }
        }
    }
}
