package dev.vimivo.desktop.diagnostics

import dev.vimivo.desktop.playback.PresentedFrame
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceFrameTrackerTest {
    private val windowed = 1920 to 1040
    private fun frame(size: Pair<Int, Int>) = PresentedFrame(1, size.first, size.second)

    @Test
    fun aSizeThatIsNotTheStepsTargetNeverSettlesHoweverLongItStays() {
        val tracker = SurfaceFrameTracker()
        // Leaving fullscreen: the window sits at 2560x1392 on the way and the frame matches it, but that is not
        // where the step sends the window.
        repeat(5) { assertFalse(tracker.isSettled(false, 2560 to 1392, frame(2560 to 1392))) }
    }

    @Test
    fun theTargetReachedWithAMatchingFrameSettlesOnTheSecondLook() {
        val tracker = SurfaceFrameTracker()
        assertFalse(tracker.isSettled(true, windowed, frame(windowed)), "the first look only notes it")
        assertTrue(tracker.isSettled(true, windowed, frame(windowed)))
    }

    @Test
    fun aFrameAtTheOldSizeDoesNotCountAsTheRenderTargetForTheNewSurface() {
        val tracker = SurfaceFrameTracker()
        val resized = 1440 to 960
        assertFalse(tracker.isSettled(true, resized, frame(windowed)), "the frame is still the old size")
        assertFalse(tracker.isSettled(true, resized, frame(windowed)))
        assertFalse(tracker.isSettled(true, resized, frame(resized)), "first look at the right size")
        assertTrue(tracker.isSettled(true, resized, frame(resized)))
    }

    @Test
    fun theSurfaceChangingBetweenLooksRestartsTheWait() {
        val tracker = SurfaceFrameTracker()
        assertFalse(tracker.isSettled(true, windowed, frame(windowed)))
        val other = 1440 to 960
        assertFalse(tracker.isSettled(true, other, frame(other)), "a different surface: start again")
        assertTrue(tracker.isSettled(true, other, frame(other)))
    }

    @Test
    fun noSurfaceOrNoFrameIsNeverSettled() {
        val tracker = SurfaceFrameTracker()
        assertFalse(tracker.isSettled(true, null, frame(windowed)))
        assertFalse(tracker.isSettled(true, windowed, null))
    }
}
