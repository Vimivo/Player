package dev.vimivo.desktop.playback

import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAudioEffectsTest {
    @Test
    fun disabledEffectsProduceNoFilter() {
        assertNull(DesktopAudioEffects.filter(AudioEnhancementSettings()))
    }

    @Test
    fun positiveGainAddsEquivalentClippingHeadroom() {
        val filter = DesktopAudioEffects.filter(
            AudioEnhancementSettings(enabled = true, preset = EqualizerPreset.ROCK),
        )
        assertTrue(filter.orEmpty().contains("volume=-4.0dB"))
        assertEquals(10, filter.orEmpty().windowed("equalizer".length).count { it == "equalizer" })
    }

    @Test
    fun customBandsAreClampedAndUnspecifiedBandsRemainFlat() {
        val filter = DesktopAudioEffects.filter(
            AudioEnhancementSettings(
                enabled = true,
                preset = EqualizerPreset.CUSTOM,
                bands = listOf(EqualizerBand(31, 20f), EqualizerBand(1_000, -20f)),
                preampDb = 3f,
            ),
        ).orEmpty()
        assertTrue(filter.contains("equalizer=f=31:t=q:w=1:g=12.0"))
        assertTrue(filter.contains("equalizer=f=1000:t=q:w=1:g=-12.0"))
        assertTrue(filter.contains("equalizer=f=62:t=q:w=1:g=0.0"))
        assertTrue(filter.contains("volume=-12.0dB"))
    }
}
