package com.misgus.pengu.player

import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.ToneMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvPlaybackOptionsTest {
    @Test
    fun mapsTheDefaultsToMpvDefaults() {
        val options = AppSettings().toMpvPlaybackOptions()

        // `auto` is a real mpv value, not "leave it unset": returning to it from
        // another curve has to reset the option on a running player.
        assertEquals("auto", options.toneMapping)
        assertEquals("no", options.debandValue)
        assertEquals("auto", options.hwdecValue)
    }

    @Test
    fun spellsEveryCurveTheWayMpvReadsIt() {
        assertEquals("auto", AppSettings(toneMapping = ToneMapping.AUTO).toMpvPlaybackOptions().toneMapping)
        assertEquals("bt.2390", AppSettings(toneMapping = ToneMapping.BT_2390).toMpvPlaybackOptions().toneMapping)
        assertEquals("hable", AppSettings(toneMapping = ToneMapping.HABLE).toMpvPlaybackOptions().toneMapping)
        assertEquals("mobius", AppSettings(toneMapping = ToneMapping.MOBIUS).toMpvPlaybackOptions().toneMapping)
    }

    @Test
    fun turnsTheTogglesIntoToggles() {
        val tuned = AppSettings(deband = true, hardwareDecoding = false).toMpvPlaybackOptions()

        assertTrue(tuned.deband)
        assertEquals("yes", tuned.debandValue)
        assertFalse(tuned.hardwareDecoding)
        assertEquals("no", tuned.hwdecValue)
    }
}
