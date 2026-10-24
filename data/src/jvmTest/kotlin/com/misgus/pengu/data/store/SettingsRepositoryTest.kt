package com.misgus.pengu.data.store

import com.misgus.pengu.core.repository.AppTypeface
import com.misgus.pengu.core.repository.HeroSource
import com.misgus.pengu.core.repository.PosterSize
import com.misgus.pengu.core.repository.TmdbLanguage
import com.misgus.pengu.core.repository.ToneMapping
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
            assertEquals(AppTypeface.ROBOTO, loaded.typeface)
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
    fun persistsTheTypeface() =
        runTest {
            val path = File(tempDir(), "settings.json").path
            val settings = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            settings.load()

            settings.setTypeface(AppTypeface.INTER)

            val reloaded = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            reloaded.load()

            assertEquals(AppTypeface.INTER, reloaded.state.value.typeface)
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
            settings.setPosterSize(PosterSize.LARGE)
            settings.setHideLabels(true)
            settings.setShowCatalogType(false)

            val reloaded = JsonSettingsRepository(store)
            reloaded.load()
            assertFalse(reloaded.state.value.animationsEnabled)
            assertFalse(reloaded.state.value.autoPlayNext)
            assertEquals(PosterSize.LARGE, reloaded.state.value.posterSize)
            assertTrue(reloaded.state.value.hideLabels)
            assertFalse(reloaded.state.value.showCatalogType)
            assertEquals("value", (store.read()["custom.key"] as JsonPrimitive).content)
            // The file is written indented, so it can be read and edited by hand.
            assertTrue(File(path).readText().contains('\n'))
        }

    @Test
    fun persistsTheDesktopPlayerOptions() =
        runTest {
            val path = File(tempDir(), "settings.json").path
            val settings = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            settings.load()
            // Off by default: mpv's own defaults, so an untouched install does not
            // pay for debanding and keeps hardware decoding.
            assertEquals(ToneMapping.AUTO, settings.state.value.toneMapping)
            assertFalse(settings.state.value.deband)
            assertTrue(settings.state.value.hardwareDecoding)

            settings.setToneMapping(ToneMapping.BT_2390)
            settings.setDeband(true)
            settings.setHardwareDecoding(false)

            val reloaded = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            reloaded.load()
            assertEquals(ToneMapping.BT_2390, reloaded.state.value.toneMapping)
            assertTrue(reloaded.state.value.deband)
            assertFalse(reloaded.state.value.hardwareDecoding)
        }

    @Test
    fun persistsTheWindowGeometry() =
        runTest {
            val path = File(tempDir(), "settings.json").path
            val settings = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            settings.load()
            // Nothing is remembered until a window has been closed, so the first
            // start keeps its own default size.
            assertTrue(settings.state.value.rememberWindowSize)
            assertEquals(null, settings.state.value.windowWidth)
            assertFalse(settings.state.value.windowMaximized)

            settings.setRememberWindowSize(false)
            settings.setWindowGeometry(width = 1600, height = 900, maximized = true)

            val reloaded = JsonSettingsRepository(JsonMapStore(path, pretty = true))
            reloaded.load()
            assertFalse(reloaded.state.value.rememberWindowSize)
            assertEquals(1600, reloaded.state.value.windowWidth)
            assertEquals(900, reloaded.state.value.windowHeight)
            assertTrue(reloaded.state.value.windowMaximized)
        }

    @Test
    fun readsTheToneMappingSpellingMpvUses() =
        runTest {
            val store = JsonMapStore(File(tempDir(), "settings.json").path, pretty = true)
            store.write(
                JsonObject(
                    mapOf(JsonSettingsRepository.KEY_TONE_MAPPING to JsonPrimitive("bt.2390")),
                ),
            )

            val settings = JsonSettingsRepository(store)
            settings.load()

            assertEquals(ToneMapping.BT_2390, settings.state.value.toneMapping)
        }
}
