package com.misgus.pengu.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** Where the Home billboard draws its featured titles from. */
enum class HeroSource {
    /** Every loaded catalog row, mixed and ranked by the app. */
    SMART,

    /** Only the catalogs the user picked. */
    MANUAL,
}

/** How many catalogs a manual billboard may draw from. */
const val MAX_HERO_CATALOGS = 2

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
    /** Where the Home billboard draws its featured titles from. */
    val heroSource: HeroSource = HeroSource.SMART,
    /**
     * Catalogs a [HeroSource.MANUAL] billboard draws from, as `CatalogRef.key`s.
     *
     * At most [MAX_HERO_CATALOGS]; empty means the same as [HeroSource.SMART].
     */
    val heroCatalogKeys: List<String> = emptyList(),
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

    suspend fun setHeroSource(source: HeroSource)

    /** Replaces the picked catalogs; only the first [MAX_HERO_CATALOGS] are kept. */
    suspend fun setHeroCatalogKeys(keys: List<String>)

    suspend fun setTmdbApiKey(apiKey: String)

    suspend fun setTmdbLanguage(language: TmdbLanguage)
}

/**
 * [SettingsRepository] that lives in memory.
 *
 * What a ViewModel falls back to before the real store is wired, and what tests
 * drive: nothing touches a file, and every setter publishes immediately.
 */
class InMemorySettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<AppSettings> = _state.asStateFlow()

    override val filePath: String = ""

    override suspend fun load() = Unit

    override suspend fun reload() = Unit

    override suspend fun setTheme(theme: AppTheme) = update { it.copy(theme = theme) }

    override suspend fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    override suspend fun setResumePlayback(enabled: Boolean) = update { it.copy(resumePlayback = enabled) }

    override suspend fun setAutoPlayNext(enabled: Boolean) = update { it.copy(autoPlayNext = enabled) }

    override suspend fun setPosterSize(size: PosterSize) = update { it.copy(posterSize = size) }

    override suspend fun setHideLabels(enabled: Boolean) = update { it.copy(hideLabels = enabled) }

    override suspend fun setShowCatalogType(enabled: Boolean) = update { it.copy(showCatalogType = enabled) }

    override suspend fun setHeroSource(source: HeroSource) = update { it.copy(heroSource = source) }

    override suspend fun setHeroCatalogKeys(keys: List<String>) =
        update { it.copy(heroCatalogKeys = keys.distinct().take(MAX_HERO_CATALOGS)) }

    override suspend fun setTmdbApiKey(apiKey: String) = update { it.copy(tmdbApiKey = apiKey.trim()) }

    override suspend fun setTmdbLanguage(language: TmdbLanguage) = update { it.copy(tmdbLanguage = language) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        _state.value = transform(_state.value)
    }
}
