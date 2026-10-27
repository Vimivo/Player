package dev.vimivo.desktop.playback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** V1 #12: black-bar detection with FFmpeg's bbox filter (cropdetect is GPL-only and absent from the LGPL build). */
class MpvBlackBarDetectionTest {
    @Test
    fun aLetterboxedClipYieldsThePictureAreaBetweenTheBars() {
        val clip = File("../testmedia/letterbox_tiny.mp4").absoluteFile
        if (!clip.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        // 320x180 picture padded to 320x240: 30 black rows above and below.
        val bounds = assertNotNull(MpvMediaInspector().detectContentBounds(clip, durationSeconds = 3.0))
        assertEquals(
            MpvMediaInspector.ContentBounds(
                x = 0,
                y = 30,
                width = 320,
                height = 180,
                frameWidth = 320,
                frameHeight = 240,
            ),
            bounds,
        )
    }

    @Test
    fun aClipWithoutBarsYieldsItsWholeFrame() {
        val clip = File("../testmedia/video_landscape.mp4").absoluteFile
        if (!clip.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val bounds = assertNotNull(MpvMediaInspector().detectContentBounds(clip, durationSeconds = 5.0))
        assertEquals(0, bounds.x)
        assertEquals(0, bounds.y)
    }
}
