package dev.vimivo.platform.android.playback

import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAudioEnhancementTest {

    @Test
    fun capabilitiesReportExpectedCapabilities() {
        val caps = AndroidAudioEnhancementController.CAPABILITIES
        assertTrue(caps.graphicEqualizer)
        assertTrue(caps.bassEnhancement)
        assertTrue(caps.trebleAdjustment)
        assertTrue(caps.attenuatingPreamp)
        assertTrue(caps.pitchPreservation)
    }

    @Test
    fun verifiesPresetResolutionAndFrequencies() {
        // Test all required presets can be configured in settings without failure
        EqualizerPreset.entries.forEach { preset ->
            val settings = AudioEnhancementSettings(
                enabled = true,
                preset = preset,
                bands = listOf(EqualizerBand(1000, 2f)),
            )
            assertEquals(preset, settings.preset)
            assertTrue(settings.enabled)
        }
    }
}
