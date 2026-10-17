@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop.playback

import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.ReplayGainMode
import dev.vimivo.playback.SubtitleStyle
import dev.vimivo.playback.VideoAdjustments
import dev.vimivo.playback.VideoAspectRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MpvAdvancedPlaybackTest {

    @Test
    fun testAdvancedCapabilitiesReportedCorrectly() {
        val engine = MpvPlayerEngine()
        try {
            val caps = engine.capabilities
            assertTrue(caps.chapters)
            assertTrue(caps.abRepeat)
            assertTrue(caps.videoAdjustments)
            assertTrue(caps.screenshots)
            assertTrue(caps.audioDelay)
            assertTrue(caps.subtitleDelay)
            assertTrue(caps.subtitleAppearance)

            val audioCaps = caps.audioEnhancements
            assertTrue(audioCaps.graphicEqualizer)
            assertTrue(audioCaps.bassEnhancement)
            assertTrue(audioCaps.trebleAdjustment)
            assertTrue(audioCaps.attenuatingPreamp)
            assertTrue(audioCaps.replayGain)
            assertTrue(audioCaps.gapless)
            assertTrue(audioCaps.pitchPreservation)
            assertTrue(audioCaps.outputSelection)
            assertTrue(audioCaps.stereoBalance)
        } finally {
            engine.close()
        }
    }

    @Test
    fun testNewEqualizerPresetsGainsAndSafetyHeadroom() {
        val trebleSettings = AudioEnhancementSettings(
            enabled = true,
            preset = EqualizerPreset.TREBLE_BOOST,
        )
        val trebleFilter = DesktopAudioEffects.filter(trebleSettings)
        assertNotNull(trebleFilter)
        assertTrue(trebleFilter.contains("equalizer=f=16000"))
        assertTrue(trebleFilter.contains("volume="))

        val acousticSettings = AudioEnhancementSettings(
            enabled = true,
            preset = EqualizerPreset.ACOUSTIC,
        )
        val acousticFilter = DesktopAudioEffects.filter(acousticSettings)
        assertNotNull(acousticFilter)
        assertTrue(acousticFilter.contains("equalizer=f=1000"))

        val electronicSettings = AudioEnhancementSettings(
            enabled = true,
            preset = EqualizerPreset.ELECTRONIC,
        )
        val electronicFilter = DesktopAudioEffects.filter(electronicSettings)
        assertNotNull(electronicFilter)
        assertTrue(electronicFilter.contains("equalizer=f=31:t=q:w=1:g=5.0"))
        assertTrue(electronicFilter.contains("equalizer=f=16000:t=q:w=1:g=5.0"))

        val podcastSettings = AudioEnhancementSettings(
            enabled = true,
            preset = EqualizerPreset.PODCAST,
        )
        val podcastFilter = DesktopAudioEffects.filter(podcastSettings)
        assertNotNull(podcastFilter)
        assertTrue(podcastFilter.contains("equalizer=f=500:t=q:w=1:g=4.0"))
    }

    @Test
    fun testReplayGainAndABRepeatStateTransitions() {
        val engine = MpvPlayerEngine()
        try {
            // Invoking action before opening media should safely return failure without crashing
            val rgResult = engine.setReplayGainMode(ReplayGainMode.TRACK)
            assertTrue(rgResult.isFailure)

            val abRange = ABRepeatRange(pointA = 5_000L, pointB = 15_000L, enabled = true)
            val abResult = engine.setABRepeat(abRange)
            assertTrue(abResult.isFailure)

            val adj = VideoAdjustments(brightness = 10, contrast = 5, aspectRatio = VideoAspectRatio.RATIO_16_9)
            val adjResult = engine.applyVideoAdjustments(adj)
            assertTrue(adjResult.isFailure)

            val subStyle = SubtitleStyle(fontSizeSp = 28, textColorHex = "#FFFF00")
            val subResult = engine.applySubtitleStyle(subStyle)
            assertTrue(subResult.isFailure)

            // Verify chapters and audio outputs return empty list safely when not initialized
            assertEquals(emptyList(), engine.getChapters())
            assertEquals(emptyList(), engine.getAudioOutputDevices())
        } finally {
            engine.close()
        }
    }
}
