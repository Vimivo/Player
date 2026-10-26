package dev.vimivo.desktop.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvTrackListWatcherTest {
    @Test
    fun theFirstCountTriggersAReadAndAnUnchangedCompleteReadDoesNot() {
        val watcher = MpvTrackListWatcher()
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 2, requested = false))
        watcher.recordRead(currentCount = 3, readTracks = 3)
        assertFalse(watcher.shouldRead(currentCount = 3, iteration = 3, requested = false))
    }

    @Test
    fun anIncompleteReadIsRepeatedOnTheNextPassNotAfterTheTwentyPassRefresh() {
        val watcher = MpvTrackListWatcher()
        watcher.shouldRead(currentCount = 3, iteration = 2, requested = false)
        watcher.recordRead(currentCount = 3, readTracks = 2) // a video entry was missed
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 3, requested = false))
        watcher.recordRead(currentCount = 3, readTracks = 3)
        assertFalse(watcher.shouldRead(currentCount = 3, iteration = 4, requested = false))
    }

    @Test
    fun aListThatStaysIncompleteIsReadAFewTimesThenLeftAloneUntilTheRefresh() {
        val watcher = MpvTrackListWatcher()
        var reads = 0
        for (iteration in 2L..30L) {
            if (watcher.shouldRead(currentCount = 4, iteration = iteration, requested = false)) {
                reads++
                watcher.recordRead(currentCount = 4, readTracks = 3) // one entry never shows
            }
        }
        // 1 first read + 5 retries, then only the periodic refresh at iteration 21.
        assertTrue(reads in 6..8, "read $reads times")
    }

    @Test
    fun theCountChangingOrARequestAlwaysReads() {
        val watcher = MpvTrackListWatcher()
        watcher.shouldRead(2, 2, false)
        watcher.recordRead(2, 2)
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 3, requested = false))
        watcher.recordRead(3, 3)
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 4, requested = true))
    }
}
