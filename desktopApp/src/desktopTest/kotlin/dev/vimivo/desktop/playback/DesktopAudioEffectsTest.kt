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
    fun boostAboveFullVolumeIsLimitedAndComesLastInTheChain() {
        val filter = DesktopAudioEffects.filter(AudioEnhancementSettings(volumeNormalization = true), boostGain = 1.5f)
        assertTrue(filter.orEmpty().endsWith("volume=1.5:precision=float,alimiter=limit=0.97:level=0]"), "$filter")
        assertTrue(filter.orEmpty().indexOf("dynaudnorm") < filter.orEmpty().indexOf("alimiter"))
        assertNull(DesktopAudioEffects.filter(AudioEnhancementSettings(), boostGain = 1f))
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

    @Test
    fun compressorFilterIncludedWhenEnabled() {
        val filter = DesktopAudioEffects.filter(AudioEnhancementSettings(compressorEnabled = true)).orEmpty()
        assertTrue(filter.contains(DesktopAudioEffects.COMPRESSOR_FILTER))
    }

    @Test
    fun spatializerFilterIncludedWhenEnabled() {
        val filter = DesktopAudioEffects.filter(AudioEnhancementSettings(spatializerEnabled = true)).orEmpty()
        assertTrue(filter.contains(DesktopAudioEffects.SPATIALIZER_FILTER))
    }

    @Test
    fun dialogueClarityFilterIncludedWhenEnabled() {
        val filter = DesktopAudioEffects.filter(AudioEnhancementSettings(dialogueClarityEnabled = true)).orEmpty()
        assertTrue(filter.contains(DesktopAudioEffects.DIALOGUE_CLARITY_FILTER))
    }

    @Test
    fun stereoWidenerGeneratesValidFilter() {
        val filter = DesktopAudioEffects.filter(AudioEnhancementSettings(stereoWidenerStrength = 0.5f)).orEmpty()
        assertTrue(filter.contains("extrastereo=m=1.8"), "Filter should contain extrastereo=m=1.8, was $filter")
        assertNull(DesktopAudioEffects.filter(AudioEnhancementSettings(stereoWidenerStrength = 0.02f)))
    }

    @Test
    fun monoDownmixGeneratesPanFilter() {
        val filter = DesktopAudioEffects.filter(AudioEnhancementSettings(monoDownmix = true)).orEmpty()
        assertTrue(filter.contains("pan=1c|c0=0.5*c0+0.5*c1"))
    }

    @Test
    fun allAdvancedDspFiltersCanBeChainedSimultaneously() {
        val filter = DesktopAudioEffects.filter(
            AudioEnhancementSettings(
                volumeNormalization = true,
                compressorEnabled = true,
                dialogueClarityEnabled = true,
                stereoWidenerStrength = 0.8f,
                spatializerEnabled = true,
                monoDownmix = true,
            ),
            boostGain = 1.25f,
        ).orEmpty()
        assertTrue(filter.startsWith("lavfi=["))
        assertTrue(filter.contains(DesktopAudioEffects.NORMALIZER_FILTER))
        assertTrue(filter.contains(DesktopAudioEffects.COMPRESSOR_FILTER))
        assertTrue(filter.contains(DesktopAudioEffects.DIALOGUE_CLARITY_FILTER))
        assertTrue(filter.contains("extrastereo=m=2.2"))
        assertTrue(filter.contains(DesktopAudioEffects.SPATIALIZER_FILTER))
        assertTrue(filter.contains("pan=1c|c0=0.5*c0+0.5*c1"))
        assertTrue(filter.endsWith("alimiter=limit=0.97:level=0]"))
    }
}
