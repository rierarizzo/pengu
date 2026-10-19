package com.misgus.pengu.data.store

import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.AppTheme
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.repository.SettingsRepository
import com.misgus.pengu.core.repository.TmdbLanguage
import com.misgus.pengu.data.boolOrNull
import com.misgus.pengu.data.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [SettingsRepository] backed by a flat JSON file, like VS Code's
 * `settings.json`:
 *
 * ```json
 * {
 *   "appearance.theme": "coffee",
 *   "animations.enabled": true,
 *   "player.resumePlayback": true,
 *   "player.autoPlayNext": true,
 *   "labels.hidden": false,
 *   "catalog.showType": true,
 *   "metadata.tmdbApiKey": "",
 *   "metadata.tmdbLanguage": "system"
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

    override suspend fun setTheme(theme: AppTheme) = update { it.copy(theme = theme) }

    override suspend fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    override suspend fun setResumePlayback(enabled: Boolean) = update { it.copy(resumePlayback = enabled) }

    override suspend fun setAutoPlayNext(enabled: Boolean) = update { it.copy(autoPlayNext = enabled) }

    override suspend fun setPosterSize(size: PosterSize) = update { it.copy(posterSize = size) }

    override suspend fun setHideLabels(enabled: Boolean) = update { it.copy(hideLabels = enabled) }

    override suspend fun setShowCatalogType(enabled: Boolean) = update { it.copy(showCatalogType = enabled) }

    override suspend fun setTmdbApiKey(apiKey: String) = update { it.copy(tmdbApiKey = apiKey.trim()) }

    override suspend fun setTmdbLanguage(language: TmdbLanguage) = update { it.copy(tmdbLanguage = language) }

    /** Writes first, then publishes, so a failed save leaves the UI unchanged. */
    private suspend fun update(transform: (AppSettings) -> AppSettings) =
        mutex.withLock {
            val next = transform(_state.value)
            write(next)
            _state.value = next
        }

    private suspend fun write(settings: AppSettings) {
        val data = store.read().toMutableMap()
        data[KEY_THEME] = JsonPrimitive(settings.theme.name.lowercase())
        data[KEY_ANIMATIONS] = JsonPrimitive(settings.animationsEnabled)
        data[KEY_RESUME] = JsonPrimitive(settings.resumePlayback)
        data[KEY_AUTOPLAY] = JsonPrimitive(settings.autoPlayNext)
        data[KEY_POSTER_SIZE] = JsonPrimitive(settings.posterSize.name.lowercase())
        data[KEY_HIDE_LABELS] = JsonPrimitive(settings.hideLabels)
        data[KEY_SHOW_CATALOG_TYPE] = JsonPrimitive(settings.showCatalogType)
        data[KEY_TMDB_API_KEY] = JsonPrimitive(settings.tmdbApiKey)
        data[KEY_TMDB_LANGUAGE] = JsonPrimitive(settings.tmdbLanguage.name.lowercase())
        store.write(JsonObject(data))
    }

    private fun JsonObject.toSettings() =
        AppSettings(
            theme = this[KEY_THEME].stringOrNull().toAppTheme() ?: AppTheme.COFFEE,
            animationsEnabled = this[KEY_ANIMATIONS].boolOrNull() ?: true,
            resumePlayback = this[KEY_RESUME].boolOrNull() ?: true,
            autoPlayNext = this[KEY_AUTOPLAY].boolOrNull() ?: true,
            posterSize = this[KEY_POSTER_SIZE].stringOrNull().toPosterSize() ?: PosterSize.DEFAULT,
            hideLabels = this[KEY_HIDE_LABELS].boolOrNull() ?: false,
            showCatalogType = this[KEY_SHOW_CATALOG_TYPE].boolOrNull() ?: true,
            tmdbApiKey = this[KEY_TMDB_API_KEY].stringOrNull() ?: "",
            tmdbLanguage = this[KEY_TMDB_LANGUAGE].stringOrNull().toTmdbLanguage() ?: TmdbLanguage.SYSTEM,
        )

    private fun String?.toPosterSize(): PosterSize? =
        when (this?.lowercase()) {
            // "small" was the old smallest option; the new default reuses its
            // width, so it maps to DEFAULT for existing settings files.
            "small", "default" -> PosterSize.DEFAULT
            "large" -> PosterSize.LARGE
            else -> null
        }

    private fun String?.toAppTheme(): AppTheme? = AppTheme.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    private fun String?.toTmdbLanguage(): TmdbLanguage? = TmdbLanguage.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    companion object {
        const val KEY_THEME = "appearance.theme"
        const val KEY_ANIMATIONS = "animations.enabled"
        const val KEY_RESUME = "player.resumePlayback"
        const val KEY_AUTOPLAY = "player.autoPlayNext"
        const val KEY_POSTER_SIZE = "poster.size"
        const val KEY_HIDE_LABELS = "labels.hidden"
        const val KEY_SHOW_CATALOG_TYPE = "catalog.showType"
        const val KEY_TMDB_API_KEY = "metadata.tmdbApiKey"
        const val KEY_TMDB_LANGUAGE = "metadata.tmdbLanguage"
    }
}
