package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackClockTest {
    private val ms = 1_000_000L

    @Test
    fun advancesBetweenReportsAtPlaybackSpeed() {
        val clock = PlaybackClock()
        clock.report(10_000, nowNanos = 0, running = true, speed = 1f, durationMillis = 60_000)
        assertEquals(10_100.0, clock.positionAt(100 * ms), 0.5)

        clock.report(10_000, nowNanos = 1_000 * ms, running = true, speed = 2f, durationMillis = 60_000)
        // Speed changes take effect from the report (10 000 + 400 ms × 2); the 1 s drift has blended out by then.
        assertEquals(10_800.0, clock.positionAt(1_400 * ms), 0.5)
    }

    @Test
    fun holdsStillWhenPausedOrBuffering() {
        val clock = PlaybackClock()
        clock.report(5_000, nowNanos = 0, running = false, speed = 1f, durationMillis = 60_000)
        assertEquals(5_000.0, clock.positionAt(2_000 * ms), 0.0)
    }

    @Test
    fun blendsSmallCorrectionsInsteadOfJumpingBack() {
        val clock = PlaybackClock()
        clock.report(0, nowNanos = 0, running = true, speed = 1f, durationMillis = 60_000)
        // Shows 1000 at t=1s, but the engine reports 900: the playhead must not jump back 100 ms at once.
        clock.report(900, nowNanos = 1_000 * ms, running = true, speed = 1f, durationMillis = 60_000)
        assertEquals(1_000.0, clock.positionAt(1_000 * ms), 0.5)
        val midway = clock.positionAt(1_200 * ms)
        assertTrue(midway in 1_100.0..1_150.0, "midway $midway")
        assertEquals(1_300.0, clock.positionAt(1_400 * ms), 0.5)
    }

    @Test
    fun snapsOnSeeks() {
        val clock = PlaybackClock()
        clock.report(0, nowNanos = 0, running = true, speed = 1f, durationMillis = 60_000)
        clock.report(30_000, nowNanos = 1_000 * ms, running = true, speed = 1f, durationMillis = 60_000)
        assertEquals(30_000.0, clock.positionAt(1_000 * ms), 0.5)
    }

    @Test
    fun staysWithinTheMedia() {
        val clock = PlaybackClock()
        clock.report(59_900, nowNanos = 0, running = true, speed = 1f, durationMillis = 60_000)
        assertEquals(60_000.0, clock.positionAt(5_000 * ms), 0.0)
    }
}
