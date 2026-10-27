package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeekControllerTest {
    private val seeks = mutableListOf<Pair<Long, Boolean>>()
    private val controller = SeekController({ position, exact -> seeks += position to exact }).apply {
        onEngineReport(0, DURATION, isSeekable = true, isSeeking = false, nowMillis = 0)
    }

    @Test
    fun aClickIssuesExactlyOneExactSeekAndHoldsTheTarget() {
        controller.seekTo(25_000, nowMillis = 10)
        assertEquals(listOf(25_000L to true), seeks)
        assertEquals(25_000L, controller.current.overrideMillis)
    }

    @Test
    fun reportsFromBeforeTheSeekLandedNeverPullTheThumbBack() {
        controller.seekTo(90_000, nowMillis = 0)
        // The poll sampled the old position, or the engine is still seeking near the old one.
        assertFalse(controller.onEngineReport(3_000, DURATION, true, isSeeking = false, nowMillis = 100))
        assertFalse(controller.onEngineReport(3_200, DURATION, true, isSeeking = true, nowMillis = 200))
        assertFalse(controller.onEngineReport(90_000, DURATION, true, isSeeking = true, nowMillis = 300))
        assertEquals(90_000L, controller.current.overrideMillis)

        assertTrue(controller.onEngineReport(90_010, DURATION, true, isSeeking = false, nowMillis = 400))
        assertNull(controller.current.overrideMillis)
    }

    @Test
    fun aClampedSeekStopsHoldingAfterTheTimeout() {
        controller.seekTo(DURATION, nowMillis = 0)
        assertFalse(controller.onEngineReport(95_000, DURATION, true, false, nowMillis = 1_000))
        assertTrue(controller.onEngineReport(95_000, DURATION, true, false, nowMillis = SeekController.CONFIRM_TIMEOUT_MILLIS))
    }

    @Test
    fun dragPreviewsWithThrottledKeyframeSeeksThenOneExactSeekOnRelease() {
        controller.dragStart(20_000, nowMillis = 0)
        for (step in 1..50) controller.dragTo(20_000L + step * 1_000, nowMillis = step * 10L)
        controller.dragEnd(nowMillis = 600)

        val fast = seeks.filter { !it.second }
        val exact = seeks.filter { it.second }
        assertEquals(listOf(70_000L to true), exact)
        assertTrue(fast.size in 2..6, "throttled to ~120 ms over 500 ms, got ${fast.size}")
        assertEquals(20_000L, fast.first().first)
        assertEquals(seeks.last(), 70_000L to true)
        assertEquals(70_000L, controller.current.pendingMillis)
        assertNull(controller.current.scrubMillis)
    }

    @Test
    fun rapidClicksEndAtTheLastTarget() {
        listOf(10_000L, 50_000L, 90_000L, 30_000L, 60_000L).forEachIndexed { i, t -> controller.seekTo(t, i * 20L) }
        assertEquals(5, seeks.size)
        assertTrue(seeks.all { it.second })
        assertFalse(controller.onEngineReport(30_000, DURATION, true, false, nowMillis = 150))
        assertTrue(controller.onEngineReport(60_000, DURATION, true, false, nowMillis = 200))
    }

    @Test
    fun relativeSeeksStartFromThePendingTarget() {
        repeat(5) { controller.seekBy(5_000, shownMillis = 1_000, nowMillis = it.toLong()) }
        assertEquals(26_000L, seeks.last().first)
        controller.seekBy(-60_000, shownMillis = 1_000, nowMillis = 10)
        assertEquals(0L, seeks.last().first)
    }

    @Test
    fun unknownDurationOrUnseekableSourcesIgnoreSeeks() {
        controller.onEngineReport(0, null, true, false, 0)
        controller.seekTo(1_000, 0)
        controller.onEngineReport(0, DURATION, isSeekable = false, isSeeking = false, nowMillis = 0)
        controller.dragStart(1_000, 0)
        assertTrue(seeks.isEmpty())
        assertFalse(controller.canSeek)
    }

    @Test
    fun aCancelledDragHoldsWhereThePreviewLeftTheEngine() {
        controller.dragStart(40_000, 0)
        controller.dragCancel(50)
        assertEquals(listOf(40_000L to false, 40_000L to true), seeks)
    }

    @Test
    fun mapsAgainstTheTrackBoundsNotTheHitArea() {
        // A 400 px track inset 8 px (thumb radius) in a 416 px touch target.
        assertEquals(0.25f, TimelineGeometry.fractionAt(108f, 8f, 400f))
        assertEquals(25_000L, TimelineGeometry.millisAt(108f, 8f, 400f, DURATION))
        assertEquals(0f, TimelineGeometry.fractionAt(2f, 8f, 400f))
        assertEquals(1f, TimelineGeometry.fractionAt(415f, 8f, 400f))
        assertEquals(0.75f, TimelineGeometry.fractionAt(108f, 8f, 400f, rightToLeft = true))
        assertEquals(108f, TimelineGeometry.xAt(25_000.0, 8f, 400f, DURATION))
    }

    @Test
    fun snapRestartsTheClockAtTheConfirmedPosition() {
        val clock = PlaybackClock()
        clock.report(10_000, nowNanos = 0, running = true, speed = 1f, durationMillis = DURATION)
        clock.report(10_800, nowNanos = 0, running = true, speed = 1f, durationMillis = DURATION, snap = true)
        assertEquals(10_800.0, clock.positionAt(0))
    }

    private companion object {
        const val DURATION = 100_000L
    }
}
