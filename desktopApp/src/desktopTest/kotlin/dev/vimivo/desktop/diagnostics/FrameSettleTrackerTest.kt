package dev.vimivo.desktop.diagnostics

import dev.vimivo.desktop.playback.PresentedFrame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameSettleTrackerTest {
    private val windowed = PresentedFrame(count = 100, width = 960, height = 520)

    @Test
    fun framesAtTheOldSizeNeverCount() {
        val tracker = FrameSettleTracker(windowed)
        assertFalse(tracker.isSettled(windowed.copy(count = 101)))
        assertFalse(tracker.isSettled(windowed.copy(count = 150)))
    }

    @Test
    fun aNewSizeCountsOnlyOnceAnotherFrameArrivesAtIt() {
        val tracker = FrameSettleTracker(windowed)
        assertFalse(tracker.isSettled(PresentedFrame(101, 1920, 1080)))
        // The same frame seen again (a slow software renderer) is not enough.
        assertFalse(tracker.isSettled(PresentedFrame(101, 1920, 1080)))
        assertTrue(tracker.isSettled(PresentedFrame(102, 1920, 1080)))
    }

    @Test
    fun intermediateSizesDuringAnAnimationRestartTheWait() {
        val tracker = FrameSettleTracker(windowed)
        assertFalse(tracker.isSettled(PresentedFrame(101, 1200, 700)))
        assertFalse(tracker.isSettled(PresentedFrame(102, 1920, 1080)))
        assertTrue(tracker.isSettled(PresentedFrame(103, 1920, 1080)))
    }

    @Test
    fun rendererWithoutFrameCountsFallsBackToTheFixedSettleTime() = runBlocking {
        val started = System.nanoTime()
        DesktopSmokeTest.awaitFrameAfterWindowChange(before = null) { null }
        assertTrue(System.nanoTime() - started >= 1_400_000_000L)
    }

    @Test
    fun aRendererThatStopsReportingEndsTheWait() = runBlocking {
        var calls = 0
        DesktopSmokeTest.awaitFrameAfterWindowChange(windowed) { if (calls++ < 2) windowed else null }
        assertTrue(calls in 3..4)
    }
}
