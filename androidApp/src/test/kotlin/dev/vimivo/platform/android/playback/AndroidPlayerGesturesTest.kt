package dev.vimivo.platform.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Unit tests for Android video player gesture calculations, accumulating seek, and lock modes. */
class AndroidPlayerGesturesTest {

    @Test
    fun accumulatingDoubleTapCalculatesCumulativeSeek() {
        var doubleTapSide = 0
        var doubleTapTotalSeconds = 0
        val interval = 10

        fun onDoubleTap(side: Int) {
            doubleTapTotalSeconds = if (doubleTapSide == side) doubleTapTotalSeconds + interval else interval
            doubleTapSide = side
        }

        // Tap right once (+10s)
        onDoubleTap(1)
        assertEquals(1, doubleTapSide)
        assertEquals(10, doubleTapTotalSeconds)

        // Tap right again (+20s accumulated)
        onDoubleTap(1)
        assertEquals(1, doubleTapSide)
        assertEquals(20, doubleTapTotalSeconds)

        // Tap right third time (+30s accumulated)
        onDoubleTap(1)
        assertEquals(30, doubleTapTotalSeconds)

        // Switch to left side (resets to -10s)
        onDoubleTap(-1)
        assertEquals(-1, doubleTapSide)
        assertEquals(10, doubleTapTotalSeconds)

        // Tap left again (-20s accumulated)
        onDoubleTap(-1)
        assertEquals(20, doubleTapTotalSeconds)
    }

    @Test
    fun holdSpeedBoostAppliesTwoXAndRestoresOnRelease() {
        var playerSpeed = 1.25f
        var holdBoostFrom: Float? = null

        fun onLongPress() {
            if (holdBoostFrom == null) {
                holdBoostFrom = playerSpeed
                playerSpeed = 2.0f
            }
        }

        fun onRelease() {
            holdBoostFrom?.let { previous ->
                playerSpeed = previous
                holdBoostFrom = null
            }
        }

        // Trigger long press
        onLongPress()
        assertEquals(2.0f, playerSpeed, 0.001f)
        assertEquals(1.25f, holdBoostFrom)

        // Release
        onRelease()
        assertEquals(1.25f, playerSpeed, 0.001f)
        assertEquals(null, holdBoostFrom)
    }

    @Test
    fun dragGesturePartitioningDeterminesCorrectInteraction() {
        val screenWidth = 1080f
        val touchSlop = 16f

        fun determineDragMode(downX: Float, dx: Float, dy: Float, pointerCount: Int): String {
            if (pointerCount >= 2) return "ZOOM"
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= touchSlop) return "NONE"
            return if (abs(dx) > abs(dy)) {
                "SEEK"
            } else if (downX < screenWidth / 2f) {
                "BRIGHTNESS"
            } else {
                "VOLUME"
            }
        }

        // Two fingers -> pinch zoom
        assertEquals("ZOOM", determineDragMode(500f, 0f, 0f, 2))

        // Left half vertical drag -> Brightness
        assertEquals("BRIGHTNESS", determineDragMode(200f, 0f, -50f, 1))

        // Right half vertical drag -> Volume
        assertEquals("VOLUME", determineDragMode(800f, 0f, -50f, 1))

        // Horizontal drag across screen -> Seek
        assertEquals("SEEK", determineDragMode(540f, 60f, 5f, 1))

        // Below touch slop threshold -> None
        assertEquals("NONE", determineDragMode(540f, 2f, 2f, 1))
    }

    @Test
    fun zoomClampLimitsScaleFactor() {
        fun clampZoom(current: Float, factor: Float): Float =
            (current * factor).coerceIn(1.0f, 4.0f)

        assertEquals(1.0f, clampZoom(1.0f, 0.5f), 0.001f) // cannot shrink below 1.0x
        assertEquals(2.5f, clampZoom(1.0f, 2.5f), 0.001f) // smooth 2.5x
        assertEquals(4.0f, clampZoom(3.0f, 2.0f), 0.001f) // clamped to 4.0x max
    }

    @Test
    fun lockModesCorrectlyGuardGestures() {
        var kidsLocked = true
        var playbackActionTriggered = false

        fun handleTap() {
            if (kidsLocked) {
                // Emits kid particle, skips playback controls
                return
            }
            playbackActionTriggered = true
        }

        handleTap()
        assertFalse(playbackActionTriggered)

        kidsLocked = false
        handleTap()
        assertTrue(playbackActionTriggered)
    }
}
