package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoQualityTest {
    private fun options(settings: VideoQualitySettings) = settings.toMpvOptions().toMap()

    @Test
    fun ultraUsesTheSharpestScalerAndCorrectDownscaling() {
        val o = options(VideoQualitySettings(upscale = UpscaleQuality.ULTRA))
        assertEquals("ewa_lanczossharp", o["scale"])
        assertEquals("ewa_lanczossharp", o["cscale"])
        assertEquals("yes", o["correct-downscaling"])
    }

    @Test
    fun standardIsTheCheapestPath() {
        val o = options(VideoQualitySettings(upscale = UpscaleQuality.STANDARD))
        assertEquals("bilinear", o["scale"])
        assertEquals("bilinear", o["dscale"])
        assertEquals("no", o["correct-downscaling"])
    }

    @Test
    fun smoothMotionSwitchesTheSyncModeAndBack() {
        val on = options(VideoQualitySettings(smoothMotion = true))
        assertEquals("yes", on["interpolation"])
        assertEquals("display-resample", on["video-sync"])
        val off = options(VideoQualitySettings(smoothMotion = false))
        assertEquals("no", off["interpolation"])
        assertEquals("audio", off["video-sync"])
    }

    @Test
    fun toneMappingAndExclusiveAudioMap() {
        val o = options(VideoQualitySettings(toneMapping = ToneMapping.BT2390, exclusiveAudio = true, deband = true))
        assertEquals("bt.2390", o["tone-mapping"])
        assertEquals("yes", o["audio-exclusive"])
        assertEquals("yes", o["deband"])
    }

    @Test
    fun everySettingProducesAValueForEveryOption() {
        for (u in UpscaleQuality.entries) for (t in ToneMapping.entries) {
            val list = VideoQualitySettings(upscale = u, toneMapping = t).toMpvOptions()
            assertEquals(list.map { it.first }.toSet().size, list.size, "no option is set twice")
            assertTrue(list.all { it.second.isNotBlank() })
        }
    }
}
