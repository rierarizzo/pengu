package com.misgus.pengu.core.repository

import kotlinx.coroutines.flow.StateFlow

/** Nominal width of a poster, so grids and rows can be consistent. */
enum class PosterSize {
    DEFAULT,
    LARGE,
}

/** Colour palette the whole UI is painted with. */
enum class AppTheme {
    /** Warm coffee/amber palette: the app default. */
    COFFEE,
}

/** Language TMDB returns titles, synopses and logos in. */
enum class TmdbLanguage {
    /** Follow the operating system's locale. */
    SYSTEM,
    ENGLISH,
    SPANISH,
}

/** Locally stored preferences. */
data class AppSettings(
    /** Play the app's motion (transitions, hover, skeletons). */
    val animationsEnabled: Boolean = true,
    /** Colour palette for every surface, control and scrim. */
    val theme: AppTheme = AppTheme.COFFEE,
    /** Resume a title from its saved position instead of the start. */
    val resumePlayback: Boolean = true,
    /** Start the next episode automatically when one finishes. */
    val autoPlayNext: Boolean = true,
    /**
     * Card width of the horizontal poster rows: the Home catalogs, the search
     * results and "More like this". Grids keep a fixed layout instead.
     */
    val posterSize: PosterSize = PosterSize.DEFAULT,
    /** Hide the title and year captions under every poster. */
    val hideLabels: Boolean = false,
    /**
     * Show each Home catalog's content type ("Movies"/"Series") after its name,
     * separated by a pipe (`Popular | Movies`).
     */
    val showCatalogType: Boolean = true,
    /** TMDB API key (v3) or read access token (v4); empty disables the enrichment. */
    val tmdbApiKey: String = "",
    /** Language TMDB answers in; [TmdbLanguage.SYSTEM] follows the OS locale. */
    val tmdbLanguage: TmdbLanguage = TmdbLanguage.SYSTEM,
)

/**
 * Local preferences, backed by a JSON file (`settings.json`).
 *
 * The file is the source of truth and can be edited by hand, like VS Code's
 * `settings.json`; [reload] re-reads it. Unknown keys in the file are preserved
 * on write, so manual additions are not clobbered. [filePath] is exposed so the
 * settings screen can show or open it.
 */
interface SettingsRepository {
    val state: StateFlow<AppSettings>

    /** Path of the backing file, for display or opening in an editor. */
    val filePath: String

    /** Loads the file into [state]; call once at startup. */
    suspend fun load()

    /** Re-reads the file, picking up manual edits. */
    suspend fun reload()

    suspend fun setTheme(theme: AppTheme)

    suspend fun setAnimationsEnabled(enabled: Boolean)

    suspend fun setResumePlayback(enabled: Boolean)

    suspend fun setAutoPlayNext(enabled: Boolean)

    suspend fun setPosterSize(size: PosterSize)

    suspend fun setHideLabels(enabled: Boolean)

    suspend fun setShowCatalogType(enabled: Boolean)

    suspend fun setTmdbApiKey(apiKey: String)

    suspend fun setTmdbLanguage(language: TmdbLanguage)
}
