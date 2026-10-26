package dev.vimivo.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AbRegionWatcherTest {
    private class Clock {
        var position = 0L
        var paused = false
        val seeks = mutableListOf<Long>()
    }

    private val region = ABRepeatRange(pointA = 1_000, pointB = 2_000, enabled = true)

    @Test
    fun seeksBackToAWhenPositionReachesB() = runTest {
        val clock = Clock()
        val watcher = AbRegionWatcher(backgroundScope, { clock.position }, { clock.paused }, { clock.seeks += it })
        watcher.watch(region)
        clock.position = 1_500
        advanceTimeBy(100)
        assertTrue(clock.seeks.isEmpty(), "inside the region nothing happens")
        clock.position = 2_000
        advanceTimeBy(100)
        assertEquals(listOf(1_000L), clock.seeks.distinct())
    }

    @Test
    fun doesNothingWhilePausedOrDisabledOrIncomplete() = runTest {
        val clock = Clock().apply {
            position = 5_000
            paused = true
        }
        val watcher = AbRegionWatcher(backgroundScope, { clock.position }, { clock.paused }, { clock.seeks += it })
        watcher.watch(region)
        advanceTimeBy(200)
        assertTrue(clock.seeks.isEmpty(), "paused")
        clock.paused = false
        watcher.watch(region.copy(enabled = false))
        advanceTimeBy(200)
        assertTrue(clock.seeks.isEmpty(), "disabled")
        watcher.watch(ABRepeatRange(pointA = 1_000))
        advanceTimeBy(200)
        assertTrue(clock.seeks.isEmpty(), "no B")
        watcher.watch(ABRepeatRange(pointA = 2_000, pointB = 1_000, enabled = true))
        advanceTimeBy(200)
        assertTrue(clock.seeks.isEmpty(), "B not after A")
    }

    @Test
    fun watchingNewRangeReplacesTheOldOneAndStopEndsIt() = runTest {
        val clock = Clock().apply { position = 3_000 }
        val watcher = AbRegionWatcher(backgroundScope, { clock.position }, { clock.paused }, { clock.seeks += it })
        watcher.watch(region)
        runCurrent()
        assertTrue(clock.seeks.contains(1_000L))
        clock.seeks.clear()
        watcher.watch(ABRepeatRange(pointA = 2_500, pointB = 4_000, enabled = true))
        advanceTimeBy(100)
        assertTrue(clock.seeks.isEmpty(), "the first region no longer applies")
        clock.position = 4_100
        advanceTimeBy(100)
        assertTrue(clock.seeks.contains(2_500L))
        watcher.stop()
        clock.seeks.clear()
        advanceTimeBy(500)
        assertTrue(clock.seeks.isEmpty(), "stopped")
    }
}
