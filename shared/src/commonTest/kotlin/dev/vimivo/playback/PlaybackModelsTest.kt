package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackModelsTest {
    @Test
    fun defaultStateIsSafeAndIdle() {
        val state = PlaybackState()
        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(1f, state.speed)
        assertTrue(state.audioTracks.isEmpty())
    }

    @Test
    fun equalizerPresetCoversRequiredMilestonePresets() {
        val names = EqualizerPreset.entries.map { it.name }
        assertTrue(names.contains("FLAT"))
        assertTrue(names.contains("BASS_BOOST"))
        assertTrue(names.contains("TREBLE_BOOST"))
        assertTrue(names.contains("VOCAL"))
        assertTrue(names.contains("CLASSICAL"))
        assertTrue(names.contains("JAZZ"))
        assertTrue(names.contains("ROCK"))
        assertTrue(names.contains("ELECTRONIC"))
        assertTrue(names.contains("PODCAST"))
        assertTrue(names.contains("CUSTOM"))
        assertEquals(12, EqualizerPreset.entries.size)
    }

    @Test
    fun audioEnhancementSettingsMaintainsSafetyDefaultsAndCustomPresets() {
        val settings = AudioEnhancementSettings()
        assertEquals(false, settings.enabled)
        assertEquals(EqualizerPreset.FLAT, settings.preset)
        assertEquals(0f, settings.balance)
        assertEquals(0f, settings.bassStrength)
        assertEquals(0f, settings.preampDb)
        assertTrue(settings.customPresets.isEmpty())

        val customPreset = CustomEqualizerPreset(
            name = "Studio Acoustic",
            bands = listOf(EqualizerBand(31, 2f), EqualizerBand(1000, 3f)),
        )
        val customized = settings.copy(
            enabled = true,
            preset = EqualizerPreset.CUSTOM,
            customPresets = listOf(customPreset),
            customPresetName = "Studio Acoustic",
            balance = -0.5f,
        )
        assertEquals(1, customized.customPresets.size)
        assertEquals("Studio Acoustic", customized.customPresetName)
        assertEquals(-0.5f, customized.balance)
    }
}
