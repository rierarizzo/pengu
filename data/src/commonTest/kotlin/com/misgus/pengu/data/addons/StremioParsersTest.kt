package com.misgus.pengu.data.addons

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StremioParsersTest {
    @Test
    fun parsesManifestWithShortAndLongResources() {
        val json =
            buildJsonObject {
                put("id", "org.test")
                put("name", "Test")
                put("version", "1.0.0")
                put("types", buildJsonArray { add(JsonPrimitive("movie")) })
                put(
                    "resources",
                    buildJsonArray {
                        add(JsonPrimitive("catalog"))
                        add(
                            buildJsonObject {
                                put("name", "stream")
                                put("types", buildJsonArray { add(JsonPrimitive("movie")) })
                            },
                        )
                    },
                )
                put(
                    "catalogs",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "movie")
                                put("id", "top")
                                put("name", "Top")
                                put(
                                    "extra",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("name", "search")
                                                put("isRequired", true)
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }

        val manifest = addonManifestFromJson(json)
        assertEquals("org.test", manifest.id)
        assertEquals(2, manifest.resources.size)
        assertEquals("catalog", manifest.resources[0].name)
        assertEquals(listOf("movie"), manifest.resources[1].types)
        assertEquals("top", manifest.catalogs.single().id)
        assertEquals(
            true,
            manifest.catalogs
                .single()
                .extra
                .single()
                .isRequired,
        )
        assertEquals(true, manifest.supports("stream", "movie"))
        assertEquals(false, manifest.supports("meta", "movie"))
    }

    @Test
    fun mergesCastFromAppExtrasTopLevelAndLinksWithoutDuplicates() {
        val json =
            buildJsonObject {
                put("id", "tt1")
                put("type", "movie")
                put("name", "Movie")
                put(
                    "app_extras",
                    buildJsonObject {
                        put(
                            "cast",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("name", "Alice")
                                        put("character", "Hero")
                                        put("photo", "https://img/alice.jpg")
                                    },
                                )
                            },
                        )
                    },
                )
                // Top-level cast is a CSV string and repeats Alice.
                put("cast", "Alice, Bob")
                put(
                    "links",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("name", "Carol")
                                put("category", "Cast")
                            },
                        )
                    },
                )
            }

        val meta = metaDetailFromJson(json)
        assertEquals(listOf("Alice", "Bob", "Carol"), meta.cast.map { it.name })
        assertEquals("Hero", meta.cast.first().character)
        assertEquals("https://img/alice.jpg", meta.cast.first().photo)
    }

    @Test
    fun readsEpisodeTitleFromName() {
        val json =
            buildJsonObject {
                put("id", "tt1")
                put("type", "series")
                put("name", "Show")
                put(
                    "videos",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "tt1:1:1")
                                put("name", "Pilot")
                                put("season", 1)
                                put("episode", 1)
                            },
                        )
                    },
                )
            }
        val episode = metaDetailFromJson(json).videos.single()
        assertEquals("Pilot", episode.title)
        assertEquals(1, episode.season?.number)
        assertEquals(1, episode.episode?.number)
    }

    @Test
    fun parsesStreamDescriptionAndProxyHeaders() {
        val json =
            buildJsonObject {
                put("url", "https://example/stream.mkv")
                put("name", "1080p")
                put("description", "Line one\nLine two")
                put(
                    "behaviorHints",
                    buildJsonObject {
                        put("videoSize", 123456789)
                        put(
                            "proxyHeaders",
                            buildJsonObject {
                                put(
                                    "request",
                                    buildJsonObject {
                                        put(
                                            "headers",
                                            buildJsonObject {
                                                put("Referer", "https://example")
                                                put("User-Agent", "Pengu")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        val stream = streamFromJson(json)
        assertEquals("Line one\nLine two", stream.description)
        assertEquals(123456789L, stream.behaviorHints?.videoSize)
        assertEquals("https://example", stream.httpHeaders?.get("Referer"))
        assertEquals("Pengu", stream.httpHeaders?.get("User-Agent"))
        assertEquals(true, stream.isDirectPlayable)
    }

    @Test
    fun parsesSubtitle() {
        val json =
            buildJsonObject {
                put("id", "sub1")
                put("url", "https://example/sub.srt")
                put("lang", "spa")
                put("lang_code", "es")
                put("ai_translated", true)
            }
        val subtitle = subtitleFromJson(json)
        assertEquals("Spanish", subtitle.languageName)
        assertEquals(true, subtitle.aiTranslated)
    }

    @Test
    fun normalizesAddonBaseUrl() {
        assertEquals("https://addon.example", normalizeAddonBaseUrl("https://addon.example/manifest.json"))
        assertEquals("https://addon.example", normalizeAddonBaseUrl("https://addon.example/manifest.json?x=1"))
        assertEquals("https://addon.example", normalizeAddonBaseUrl("https://addon.example/"))
        assertEquals("https://addon.example", normalizeAddonBaseUrl("https://addon.example"))
        assertEquals("https://addon.example", normalizeAddonBaseUrl("stremio://addon.example/manifest.json"))
        assertEquals("https://v3-cinemeta.strem.io", normalizeAddonBaseUrl("stremio://v3-cinemeta.strem.io/manifest.json"))
    }

    @Test
    fun encodesOnlyExtraValues() {
        val encoded = encodeValue("Batman Begins")
        assertEquals("Batman%20Begins", encoded)
        assertNull(encoded.firstOrNull { it == '=' })
    }

    @Test
    fun percentEncodesNonAsciiBytes() {
        assertEquals("caf%C3%A9", encodeValue("café"))
        assertEquals("100%25", encodeValue("100%"))
    }
}
