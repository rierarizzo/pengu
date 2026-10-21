package com.misgus.pengu.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Nominal width of a poster, so grids and rows can be consistent. */
enum class PosterSize {
    DEFAULT,
    LARGE,
}

/** Typeface every screen is set in. */
enum class AppTypeface {
    /** IBM Plex Sans: humanist and slightly warm, the app default. */
    IBM_PLEX_SANS,

    /** Inter: neutral and compact, tighter at small sizes. */
    INTER,
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

/**
 * How long a resolved stream URL is trusted before the picker is asked again.
 *
 * Debrid links expire and their URLs rotate, so a resume that plays a remembered
 * URL directly is a bet that gets worse with time. [OFF] never takes it.
 */
enum class StreamUrlCache(
    val ttl: Duration,
) {
    ONE_HOUR(1.hours),
    SIX_HOURS(6.hours),
    ONE_DAY(1.days),

    /** Always ask the picker; nothing is reused. */
    OFF(Duration.ZERO),
}

/**
 * HDR tone-mapping curve for the desktop player.
 *
 * The values are the ones mpv's legacy renderer accepts; `spline` and the
 * dynamic HDR10+ curves are `gpu-next`-only and are deliberately absent, because
 * the desktop surface renders through the render API. [AUTO] is mpv's own default
 * (`bt.2390` on this renderer), so it is safe to send back when the user returns
 * to it from a curve they tried.
 */
enum class ToneMapping {
    AUTO,
    BT_2390,
    HABLE,
    MOBIUS,
}

/** How many catalogs a manual billboard may draw from. */
const val MAX_HERO_CATALOGS = 2

/** Locally stored preferences. */
data class AppSettings(
    /** Play the app's motion (transitions, hover, skeletons). */
    val animationsEnabled: Boolean = true,
    /** Typeface every screen is set in. */
    val typeface: AppTypeface = AppTypeface.IBM_PLEX_SANS,
    /** Resume a title from its saved position instead of the start. */
    val resumePlayback: Boolean = true,
    /** Start the next episode automatically when one finishes. */
    val autoPlayNext: Boolean = true,
    /**
     * How long a resolved stream URL is reused when "Continue watching" resumes
     * a title without the picker. Past it the picker is shown again.
     */
    val streamUrlCache: StreamUrlCache = StreamUrlCache.SIX_HOURS,
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
    /**
     * HDR tone-mapping curve the desktop player uses to bring HDR into the SDR
     * window. Desktop only: the Android player has no equivalent knob.
     */
    val toneMapping: ToneMapping = ToneMapping.AUTO,
    /**
     * The desktop player's debanding filter.
     *
     * Off by default, which is mpv's own default: it smooths gradient banding at
     * a GPU cost, so it is an opt-in rather than something the app turns on.
     */
    val deband: Boolean = false,
    /**
     * Decode with the GPU in the desktop player.
     *
     * On by default (mpv `hwdec=auto`). Turning it off forces software decoding,
     * which is the first thing to try when a source plays with the wrong colours
     * or not at all.
     */
    val hardwareDecoding: Boolean = true,
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

    suspend fun setTypeface(typeface: AppTypeface)

    suspend fun setAnimationsEnabled(enabled: Boolean)

    suspend fun setResumePlayback(enabled: Boolean)

    suspend fun setAutoPlayNext(enabled: Boolean)

    suspend fun setStreamUrlCache(cache: StreamUrlCache)

    suspend fun setPosterSize(size: PosterSize)

    suspend fun setHideLabels(enabled: Boolean)

    suspend fun setShowCatalogType(enabled: Boolean)

    suspend fun setHeroSource(source: HeroSource)

    /** Replaces the picked catalogs; only the first [MAX_HERO_CATALOGS] are kept. */
    suspend fun setHeroCatalogKeys(keys: List<String>)

    suspend fun setTmdbApiKey(apiKey: String)

    suspend fun setTmdbLanguage(language: TmdbLanguage)

    suspend fun setToneMapping(mapping: ToneMapping)

    suspend fun setDeband(enabled: Boolean)

    suspend fun setHardwareDecoding(enabled: Boolean)
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

    override suspend fun setTypeface(typeface: AppTypeface) = update { it.copy(typeface = typeface) }

    override suspend fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    override suspend fun setResumePlayback(enabled: Boolean) = update { it.copy(resumePlayback = enabled) }

    override suspend fun setAutoPlayNext(enabled: Boolean) = update { it.copy(autoPlayNext = enabled) }

    override suspend fun setStreamUrlCache(cache: StreamUrlCache) = update { it.copy(streamUrlCache = cache) }

    override suspend fun setPosterSize(size: PosterSize) = update { it.copy(posterSize = size) }

    override suspend fun setHideLabels(enabled: Boolean) = update { it.copy(hideLabels = enabled) }

    override suspend fun setShowCatalogType(enabled: Boolean) = update { it.copy(showCatalogType = enabled) }

    override suspend fun setHeroSource(source: HeroSource) = update { it.copy(heroSource = source) }

    override suspend fun setHeroCatalogKeys(keys: List<String>) =
        update { it.copy(heroCatalogKeys = keys.distinct().take(MAX_HERO_CATALOGS)) }

    override suspend fun setTmdbApiKey(apiKey: String) = update { it.copy(tmdbApiKey = apiKey.trim()) }

    override suspend fun setTmdbLanguage(language: TmdbLanguage) = update { it.copy(tmdbLanguage = language) }

    override suspend fun setToneMapping(mapping: ToneMapping) = update { it.copy(toneMapping = mapping) }

    override suspend fun setDeband(enabled: Boolean) = update { it.copy(deband = enabled) }

    override suspend fun setHardwareDecoding(enabled: Boolean) = update { it.copy(hardwareDecoding = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        _state.value = transform(_state.value)
    }
}
