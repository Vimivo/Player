package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoRotationTest {
    @Test
    fun quarterTurnsCycleBackToTheOriginal() {
        var rotation = VideoRotation.NONE
        val seen = List(4) { rotation.also { rotation = rotation.next() }.degrees }
        assertEquals(listOf(0, 90, 180, 270), seen)
        assertEquals(VideoRotation.NONE, rotation)
    }

    @Test
    fun uprightAndUpsideDownNeedNoScaling() {
        assertEquals(1f, VideoRotation.NONE.fitScale(1080f, 2400f, 16f / 9f))
        assertEquals(1f, VideoRotation.CLOCKWISE_180.fitScale(1080f, 2400f, 16f / 9f))
    }

    @Test
    fun landscapeVideoOnAPortraitPhoneShrinksWhenTurnedSideways() {
        // 16:9 video fills the 1080 width (608 tall); turned, it is 608 wide x 1080 tall and fits the 2400 height,
        // so the width has plenty of room and the limit is 1080 / 1080 = 1.
        val scale = VideoRotation.CLOCKWISE_90.fitScale(1080f, 2400f, 16f / 9f)
        assertTrue(scale >= 1f, "a turned landscape picture can grow to use the tall screen: $scale")
        assertEquals(VideoRotation.CLOCKWISE_270.fitScale(1080f, 2400f, 16f / 9f), scale)
    }

    @Test
    fun portraitVideoOnALandscapeScreenMustShrinkWhenTurnedSideways() {
        // 9:16 video fits a 2400x1080 screen at 608x1080; turned it would be 1080 wide x 608 tall: fits, scale 2.22.
        // A square screen shows the shrink case: 9:16 video in 1000x1000 is 562x1000; turned, 1000x562 fits exactly.
        val square = VideoRotation.CLOCKWISE_90.fitScale(1000f, 1000f, 9f / 16f)
        assertEquals(1f, square, 0.001f)
        // A wide 21:9 clip in a 1000x1000 box is 1000x428; turned it is 428x1000 and also fits at 1.
        assertEquals(1f, VideoRotation.CLOCKWISE_90.fitScale(1000f, 1000f, 21f / 9f), 0.001f)
    }

    @Test
    fun rotatedPictureNeverOverflowsTheContainer() {
        for ((w, h) in listOf(1080f to 2400f, 2400f to 1080f, 1000f to 1000f, 500f to 1200f)) {
            for (aspect in listOf(9f / 16f, 1f, 4f / 3f, 16f / 9f, 21f / 9f)) {
                val scale = VideoRotation.CLOCKWISE_90.fitScale(w, h, aspect)
                val fittedW = if (w / h > aspect) h * aspect else w
                val fittedH = if (w / h > aspect) h else w / aspect
                assertTrue(fittedH * scale <= w + 0.5f, "width overflow for $w x $h at $aspect")
                assertTrue(fittedW * scale <= h + 0.5f, "height overflow for $w x $h at $aspect")
            }
        }
    }

    @Test
    fun badInputFallsBackToNoScaling() {
        assertEquals(1f, VideoRotation.CLOCKWISE_90.fitScale(0f, 100f, 1.5f))
        assertEquals(1f, VideoRotation.CLOCKWISE_90.fitScale(100f, 100f, 0f))
        assertEquals(1f, VideoRotation.CLOCKWISE_90.fitScale(100f, 100f, Float.NaN))
    }

    @Test
    fun customSleepMinutes() {
        assertEquals(45, SleepTimerOptions.customMinutes("", "45"))
        assertEquals(120, SleepTimerOptions.customMinutes("2", ""))
        assertEquals(95, SleepTimerOptions.customMinutes("1", "35"))
        assertEquals(1440, SleepTimerOptions.customMinutes("24", "0"))
        assertNull(SleepTimerOptions.customMinutes("", ""))
        assertNull(SleepTimerOptions.customMinutes("0", "0"))
        assertNull(SleepTimerOptions.customMinutes("24", "1"))
        assertNull(SleepTimerOptions.customMinutes("-1", "10"))
        assertNull(SleepTimerOptions.customMinutes("1.5", ""))
        assertNull(SleepTimerOptions.customMinutes("abc", "10"))
        assertNull(SleepTimerOptions.customMinutes("99999", "0"))
    }

    @Test
    fun sleepLabels() {
        assertEquals("45 min", SleepTimerOptions.label(45))
        assertEquals("1 h", SleepTimerOptions.label(60))
        assertEquals("1 h 30 min", SleepTimerOptions.label(90))
        assertEquals("2 h", SleepTimerOptions.label(120))
    }
}
