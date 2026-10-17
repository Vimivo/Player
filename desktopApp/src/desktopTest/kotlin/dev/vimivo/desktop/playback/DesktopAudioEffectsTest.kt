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

    @Test
    fun electronicAndPodcastPresetsGenerateValidFilters() {
        val electronicFilter = DesktopAudioEffects.filter(
            AudioEnhancementSettings(enabled = true, preset = EqualizerPreset.ELECTRONIC),
        ).orEmpty()
        assertTrue(electronicFilter.contains("equalizer=f=31:t=q:w=1:g=5.0"))
        assertTrue(electronicFilter.contains("equalizer=f=16000:t=q:w=1:g=5.0"))
        assertTrue(electronicFilter.contains("volume=-5.0dB"))

        val podcastFilter = DesktopAudioEffects.filter(
            AudioEnhancementSettings(enabled = true, preset = EqualizerPreset.PODCAST),
        ).orEmpty()
        assertTrue(podcastFilter.contains("equalizer=f=500:t=q:w=1:g=4.0"))
        assertTrue(podcastFilter.contains("equalizer=f=1000:t=q:w=1:g=4.0"))
        assertTrue(podcastFilter.contains("volume=-4.0dB"))
    }

    @Test
    fun effectiveSettingsClampsBalanceAndGains() {
        val raw = AudioEnhancementSettings(
            bassStrength = 1.8f,
            trebleGainDb = 25f,
            preampDb = 10f,
            balance = -2.5f,
        )
        val clamped = DesktopAudioEffects.effectiveSettings(raw)
        assertEquals(1.0f, clamped.bassStrength)
        assertEquals(12.0f, clamped.trebleGainDb)
        assertEquals(0.0f, clamped.preampDb)
        assertEquals(-1.0f, clamped.balance)
    }
}
