package com.misgus.pengu.data.store

import com.misgus.pengu.core.repository.AppTheme
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.repository.TmdbLanguage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    private fun tempDir(): File =
        File.createTempFile("pengu-settings", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    @Test
    fun fallsBackToDefaultsWhenTheFileIsMissing() =
        runTest {
            val settings =
                JsonSettingsRepository(JsonMapStore(File(tempDir(), "settings.json").path, pretty = true))

            settings.load()

            assertTrue(settings.state.value.animationsEnabled)
            assertTrue(settings.state.value.resumePlayback)
            assertTrue(settings.state.value.autoPlayNext)
            assertFalse(settings.state.value.hideLabels)
            assertTrue(settings.state.value.showCatalogType)
            val loaded = settings.state.value
            assertEquals(AppTheme.COFFEE, loaded.theme)
            assertEquals(HeroSource.SMART, loaded.heroSource)
            assertTrue(loaded.heroCatalogKeys.isEmpty())
            assertEquals("", loaded.tmdbApiKey)
            assertEquals(TmdbLanguage.SYSTEM, loaded.tmdbLanguage)
        }

    @Test
    fun persistsTheBillboardSourceAndItsCatalogs() =
        runTest {
            val path = File(tempDir(), "settings.json").path
            val settings = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            settings.load()

            settings.setHeroSource(HeroSource.MANUAL)
            settings.setHeroCatalogKeys(listOf("https://a|movie|top", "https://b|series|new"))

            val reloaded = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            reloaded.load()

            assertEquals(HeroSource.MANUAL, reloaded.state.value.heroSource)
            assertEquals(listOf("https://a|movie|top", "https://b|series|new"), reloaded.state.value.heroCatalogKeys)
        }

    @Test
    fun keepsOnlyTwoBillboardCatalogs() =
        runTest {
            val settings = JsonSettingsRepository(JsonMapStore(File(tempDir(), "settings.json").path))
            settings.load()

            settings.setHeroCatalogKeys(listOf("https://a|movie|top", "https://b|series|new", "https://c|movie|x"))

            assertEquals(listOf("https://a|movie|top", "https://b|series|new"), settings.state.value.heroCatalogKeys)
        }

    @Test
    fun persistsTheTmdbKeyAndLanguage() =
        runTest {
            val store = JsonMapStore(File(tempDir(), "settings.json").path, pretty = true)

            val settings = JsonSettingsRepository(store)
            settings.load()
            settings.setTmdbApiKey("  abc  ")
            settings.setTmdbLanguage(TmdbLanguage.SPANISH)

            val reloaded = JsonSettingsRepository(store)
            reloaded.load()
            assertEquals("abc", reloaded.state.value.tmdbApiKey)
            assertEquals(TmdbLanguage.SPANISH, reloaded.state.value.tmdbLanguage)
        }

    @Test
    fun mapsTheLegacySmallPosterSizeToDefault() =
        runTest {
            val store = JsonMapStore(File(tempDir(), "settings.json").path, pretty = true)
            store.write(
                JsonObject(
                    mapOf(JsonSettingsRepository.KEY_POSTER_SIZE to JsonPrimitive("small")),
                ),
            )

            val settings = JsonSettingsRepository(store)
            settings.load()

            assertEquals(PosterSize.DEFAULT, settings.state.value.posterSize)
        }

    @Test
    fun persistsTogglesAndKeepsUnknownKeys() =
        runTest {
            val path = File(tempDir(), "settings.json").path
            val store = JsonMapStore(path, pretty = true)
            store.write(JsonObject(mapOf("custom.key" to JsonPrimitive("value"))))

            val settings = JsonSettingsRepository(store)
            settings.load()
            settings.setAnimationsEnabled(false)
            settings.setAutoPlayNext(false)
            settings.setTheme(AppTheme.COFFEE)
            settings.setPosterSize(PosterSize.LARGE)
            settings.setHideLabels(true)
            settings.setShowCatalogType(false)

            val reloaded = JsonSettingsRepository(store)
            reloaded.load()
            assertFalse(reloaded.state.value.animationsEnabled)
            assertFalse(reloaded.state.value.autoPlayNext)
            assertEquals(AppTheme.COFFEE, reloaded.state.value.theme)
            assertEquals(PosterSize.LARGE, reloaded.state.value.posterSize)
            assertTrue(reloaded.state.value.hideLabels)
            assertFalse(reloaded.state.value.showCatalogType)
            assertEquals("value", (store.read()["custom.key"] as JsonPrimitive).content)
            // The file is written indented, so it can be read and edited by hand.
            assertTrue(File(path).readText().contains('\n'))
        }
}
