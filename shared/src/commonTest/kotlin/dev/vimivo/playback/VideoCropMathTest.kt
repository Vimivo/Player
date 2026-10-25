package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VideoCropMathTest {
    @Test
    fun aWiderPresetCropsTopAndBottomEvenly() {
        // 4:3 picture, 16:9 preset: full width, 1440x1080 → 1440x810; the 270 spare rows split 134 + 136 (even offset).
        assertEquals(CropRect(0, 134, 1440, 810), VideoCropMath.centered(1440, 1080, 16.0 / 9.0))
    }

    @Test
    fun aNarrowerPresetCropsLeftAndRightEvenly() {
        // 16:9 picture, 4:3 preset: full height, 1920x1080 → 1440x1080, centred horizontally.
        assertEquals(CropRect(240, 0, 1440, 1080), VideoCropMath.centered(1920, 1080, 4.0 / 3.0))
    }

    @Test
    fun aPresetThatMatchesThePictureIsNoCrop() {
        assertNull(VideoCropMath.centered(1920, 1080, 16.0 / 9.0))
        assertNull(VideoCropMath.centered(1920, 1080, 1.78))
    }

    @Test
    fun cropsAreAlwaysEvenSized() {
        val rect = VideoCropMath.centered(1001, 601, 1.0)!!
        assertEquals(0, rect.width % 2)
        assertEquals(0, rect.height % 2)
        assertEquals(0, rect.x % 2)
        assertEquals(0, rect.y % 2)
    }

    @Test
    fun detectedBarsBecomeACropOfThePictureArea() {
        // 320x240 frame with 30 black rows above and below the 320x180 picture.
        assertEquals(CropRect(0, 30, 320, 180), VideoCropMath.fromContent(320, 240, CropRect(0, 30, 320, 180)))
    }

    @Test
    fun barsThinnerThanTheThresholdAreIgnored() {
        assertNull(VideoCropMath.fromContent(320, 240, CropRect(0, 1, 320, 238)))
        assertNull(VideoCropMath.fromContent(1920, 1080, CropRect(0, 0, 1920, 1080)))
    }

    @Test
    fun anOddDetectionIsSnappedToEvenValuesInsideThePicture() {
        val crop = VideoCropMath.fromContent(321, 241, CropRect(1, 31, 319, 179))!!
        assertEquals(CropRect(0, 30, 318, 178), crop)
    }

    @Test
    fun mpvValueUsesGeometryNotation() {
        assertEquals("320x180+0+30", CropRect(0, 30, 320, 180).toMpvValue())
    }

    @Test
    fun aCropNeedsAPositiveSize() {
        assertFailsWith<IllegalArgumentException> { CropRect(0, 0, 0, 10) }
        assertFailsWith<IllegalArgumentException> { CropRect(-1, 0, 10, 10) }
    }
}
