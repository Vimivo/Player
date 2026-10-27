package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BlackBarDetectorTest {
    /** A frame of [width]×[height] that is [bar] everywhere except [picture], where it is [content]. */
    private fun frame(width: Int, height: Int, picture: CropRect, content: Int = 180, bar: Int = 16): ByteArray =
        ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val inside = x in picture.x until picture.x + picture.width &&
                y in picture.y until picture.y + picture.height
            (if (inside) content else bar).toByte()
        }

    @Test
    fun findsALetterboxedPicture() {
        val picture = CropRect(0, 30, 320, 180)
        assertEquals(picture, BlackBarDetector.contentBounds(320, 240, frame(320, 240, picture)))
    }

    @Test
    fun findsAPillarboxedPicture() {
        val picture = CropRect(40, 0, 240, 240)
        assertEquals(picture, BlackBarDetector.contentBounds(320, 240, frame(320, 240, picture)))
    }

    @Test
    fun findsBarsOnAllFourSides() {
        val picture = CropRect(24, 18, 200, 120)
        assertEquals(picture, BlackBarDetector.contentBounds(256, 160, frame(256, 160, picture)))
    }

    @Test
    fun aFrameWithoutBarsIsTheWholeFrame() {
        val whole = CropRect(0, 0, 64, 48)
        assertEquals(whole, BlackBarDetector.contentBounds(64, 48, frame(64, 48, whole)))
    }

    @Test
    fun aWhollyBlackFrameHasNoContent() {
        assertNull(BlackBarDetector.contentBounds(64, 48, ByteArray(64 * 48) { 16 }))
    }

    @Test
    fun aDarkSceneInsideTheBarsIsStillContentOnceItIsBrighterThanBlack() {
        val picture = CropRect(0, 20, 100, 60)
        assertEquals(picture, BlackBarDetector.contentBounds(100, 100, frame(100, 100, picture, content = 60)))
        val tooDark = frame(100, 100, picture, content = 30)
        assertNull(BlackBarDetector.contentBounds(100, 100, tooDark), "indistinguishable from the bars")
    }

    @Test
    fun aLogoOrNoiseInABarDoesNotMoveTheEdge() {
        val width = 200
        val height = 120
        val picture = CropRect(0, 20, width, 80)
        val pixels = frame(width, height, picture)
        pixels[5 * width + 100] = 200.toByte() // one bright pixel in the top bar
        assertEquals(picture, BlackBarDetector.contentBounds(width, height, pixels))
    }

    @Test
    fun theUnionKeepsEverythingAnySceneShows() {
        val union = BlackBarDetector.union(
            listOf(CropRect(0, 30, 320, 180), CropRect(0, 20, 320, 200), CropRect(10, 30, 300, 180)),
        )
        assertEquals(CropRect(0, 20, 320, 200), union)
        assertNull(BlackBarDetector.union(emptyList()))
    }

    @Test
    fun theDetectedBoundsFeedTheCropMath() {
        val bounds = BlackBarDetector.contentBounds(320, 240, frame(320, 240, CropRect(0, 31, 320, 178)))!!
        assertEquals(CropRect(0, 30, 320, 178), VideoCropMath.fromContent(320, 240, bounds), "snapped to even values")
    }

    @Test
    fun aPlaneThatDoesNotMatchItsSizeIsRefused() {
        assertFailsWith<IllegalArgumentException> { BlackBarDetector.contentBounds(10, 10, ByteArray(5)) }
    }
}
