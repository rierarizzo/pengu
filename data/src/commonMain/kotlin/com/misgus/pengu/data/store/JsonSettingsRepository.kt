package com.misgus.pengu.data.store

import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.AppTypeface
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.MAX_HERO_CATALOGS
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.StreamUrlCache
import com.misgus.pengu.core.repository.TmdbLanguage
import com.misgus.pengu.core.repository.ToneMapping
import com.misgus.pengu.data.boolOrNull
import com.misgus.pengu.data.stringList
import com.misgus.pengu.data.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [SettingsRepository] backed by a flat JSON file, like VS Code's
 * `settings.json`:
 *
 * ```json
 * {
 *   "appearance.typeface": "ibm_plex_sans",
 *   "animations.enabled": true,
 *   "player.resumePlayback": true,
 *   "player.autoPlayNext": true,
 *   "labels.hidden": false,
 *   "catalog.showType": true,
 *   "hero.source": "smart",
 *   "hero.catalogs": ["https://addon|movie|top"],
 *   "metadata.tmdbApiKey": "",
 *   "metadata.tmdbLanguage": "system",
 *   "player.toneMapping": "auto",
 *   "player.deband": false,
 *   "player.hardwareDecoding": true
 * }
 * ```
 *
 * Reads are defensive (a missing or corrupt file means defaults) and writes
 * keep any key the app does not know, so the file can be edited by hand.
 */
class JsonSettingsRepository(
    private val store: JsonMapStore,
    override val filePath: String = AppPaths.settingsFile,
) : SettingsRepository {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppSettings())
    override val state: StateFlow<AppSettings> = _state.asStateFlow()

    override suspend fun load() =
        mutex.withLock {
            _state.value = store.read().toSettings()
        }

    override suspend fun reload() = load()

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

    /** Writes first, then publishes, so a failed save leaves the UI unchanged. */
    private suspend fun update(transform: (AppSettings) -> AppSettings) =
        mutex.withLock {
            val next = transform(_state.value)
            write(next)
            _state.value = next
        }

    private suspend fun write(settings: AppSettings) {
        val data = store.read().toMutableMap()
        data[KEY_TYPEFACE] = JsonPrimitive(settings.typeface.name.lowercase())
        data[KEY_ANIMATIONS] = JsonPrimitive(settings.animationsEnabled)
        data[KEY_RESUME] = JsonPrimitive(settings.resumePlayback)
        data[KEY_AUTOPLAY] = JsonPrimitive(settings.autoPlayNext)
        data[KEY_STREAM_CACHE] = JsonPrimitive(settings.streamUrlCache.name.lowercase())
        data[KEY_POSTER_SIZE] = JsonPrimitive(settings.posterSize.name.lowercase())
        data[KEY_HIDE_LABELS] = JsonPrimitive(settings.hideLabels)
        data[KEY_SHOW_CATALOG_TYPE] = JsonPrimitive(settings.showCatalogType)
        data[KEY_HERO_SOURCE] = JsonPrimitive(settings.heroSource.name.lowercase())
        data[KEY_HERO_CATALOGS] = JsonArray(settings.heroCatalogKeys.take(MAX_HERO_CATALOGS).map(::JsonPrimitive))
        data[KEY_TMDB_API_KEY] = JsonPrimitive(settings.tmdbApiKey)
        data[KEY_TMDB_LANGUAGE] = JsonPrimitive(settings.tmdbLanguage.name.lowercase())
        data[KEY_TONE_MAPPING] = JsonPrimitive(settings.toneMapping.name.lowercase())
        data[KEY_DEBAND] = JsonPrimitive(settings.deband)
        data[KEY_HARDWARE_DECODING] = JsonPrimitive(settings.hardwareDecoding)
        store.write(JsonObject(data))
    }

    private fun JsonObject.toSettings() =
        AppSettings(
            typeface = this[KEY_TYPEFACE].stringOrNull().toAppTypeface() ?: AppTypeface.IBM_PLEX_SANS,
            animationsEnabled = this[KEY_ANIMATIONS].boolOrNull() ?: true,
            resumePlayback = this[KEY_RESUME].boolOrNull() ?: true,
            autoPlayNext = this[KEY_AUTOPLAY].boolOrNull() ?: true,
            streamUrlCache = this[KEY_STREAM_CACHE].stringOrNull().toStreamUrlCache() ?: StreamUrlCache.SIX_HOURS,
            posterSize = this[KEY_POSTER_SIZE].stringOrNull().toPosterSize() ?: PosterSize.DEFAULT,
            hideLabels = this[KEY_HIDE_LABELS].boolOrNull() ?: false,
            showCatalogType = this[KEY_SHOW_CATALOG_TYPE].boolOrNull() ?: true,
            heroSource = this[KEY_HERO_SOURCE].stringOrNull().toHeroSource() ?: HeroSource.SMART,
            // A file edited by hand can carry more keys than the UI allows, and
            // an array of non-strings is simply ignored.
            heroCatalogKeys = this[KEY_HERO_CATALOGS].stringList().distinct().take(MAX_HERO_CATALOGS),
            tmdbApiKey = this[KEY_TMDB_API_KEY].stringOrNull() ?: "",
            tmdbLanguage = this[KEY_TMDB_LANGUAGE].stringOrNull().toTmdbLanguage() ?: TmdbLanguage.SYSTEM,
            toneMapping = this[KEY_TONE_MAPPING].stringOrNull().toToneMapping() ?: ToneMapping.AUTO,
            deband = this[KEY_DEBAND].boolOrNull() ?: false,
            hardwareDecoding = this[KEY_HARDWARE_DECODING].boolOrNull() ?: true,
        )

    private fun String?.toPosterSize(): PosterSize? =
        when (this?.lowercase()) {
            // "small" was the old smallest option; the new default reuses its
            // width, so it maps to DEFAULT for existing settings files.
            "small", "default" -> PosterSize.DEFAULT
            "large" -> PosterSize.LARGE
            else -> null
        }

    private fun String?.toAppTypeface(): AppTypeface? =
        when (this?.lowercase()?.replace('-', '_')?.replace(' ', '_')) {
            // The file is hand-editable, so accept the spellings a person writes.
            "plex", "ibm_plex" -> AppTypeface.IBM_PLEX_SANS
            else -> AppTypeface.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        }

    private fun String?.toTmdbLanguage(): TmdbLanguage? = TmdbLanguage.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    private fun String?.toHeroSource(): HeroSource? = HeroSource.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    private fun String?.toStreamUrlCache(): StreamUrlCache? = StreamUrlCache.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    private fun String?.toToneMapping(): ToneMapping? =
        when (this?.lowercase()) {
            // The file is hand-editable, so accept the spelling mpv uses too.
            "bt.2390", "bt2390" -> ToneMapping.BT_2390
            else -> ToneMapping.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        }

    companion object {
        const val KEY_TYPEFACE = "appearance.typeface"
        const val KEY_ANIMATIONS = "animations.enabled"
        const val KEY_RESUME = "player.resumePlayback"
        const val KEY_AUTOPLAY = "player.autoPlayNext"
        const val KEY_STREAM_CACHE = "player.streamUrlCache"
        const val KEY_POSTER_SIZE = "poster.size"
        const val KEY_HIDE_LABELS = "labels.hidden"
        const val KEY_SHOW_CATALOG_TYPE = "catalog.showType"
        const val KEY_HERO_SOURCE = "hero.source"
        const val KEY_HERO_CATALOGS = "hero.catalogs"
        const val KEY_TMDB_API_KEY = "metadata.tmdbApiKey"
        const val KEY_TMDB_LANGUAGE = "metadata.tmdbLanguage"
        const val KEY_TONE_MAPPING = "player.toneMapping"
        const val KEY_DEBAND = "player.deband"
        const val KEY_HARDWARE_DECODING = "player.hardwareDecoding"
    }
}
