package dev.vimivo.desktop.playback

import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvTrackListWatcherTest {
    @Test
    fun theFirstCountTriggersAReadAndAnUnchangedCompleteReadDoesNot() {
        val watcher = MpvTrackListWatcher()
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 2, requested = false))
        watcher.recordRead(currentCount = 3, complete = true)
        assertFalse(watcher.shouldRead(currentCount = 3, iteration = 3, requested = false))
    }

    @Test
    fun anIncompleteReadIsRepeatedOnTheNextPassNotAfterTheTwentyPassRefresh() {
        val watcher = MpvTrackListWatcher()
        watcher.shouldRead(currentCount = 3, iteration = 2, requested = false)
        watcher.recordRead(currentCount = 3, complete = false) // a video entry was missed
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 3, requested = false))
        watcher.recordRead(currentCount = 3, complete = true)
        assertFalse(watcher.shouldRead(currentCount = 3, iteration = 4, requested = false))
    }

    @Test
    fun aListThatStaysIncompleteIsReadAFewTimesThenLeftAloneUntilTheRefresh() {
        val watcher = MpvTrackListWatcher()
        var reads = 0
        for (iteration in 2L..30L) {
            if (watcher.shouldRead(currentCount = 4, iteration = iteration, requested = false)) {
                reads++
                watcher.recordRead(currentCount = 4, complete = false) // one entry never shows
            }
        }
        // 1 first read + 5 retries, then only the periodic refresh at iteration 21.
        assertTrue(reads in 6..8, "read $reads times")
    }

    @Test
    fun theCountChangingOrARequestAlwaysReads() {
        val watcher = MpvTrackListWatcher()
        watcher.shouldRead(2, 2, false)
        watcher.recordRead(2, true)
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 3, requested = false))
        watcher.recordRead(3, true)
        assertTrue(watcher.shouldRead(currentCount = 3, iteration = 4, requested = true))
    }

    private fun track(type: TrackType, id: Int, selected: Boolean) =
        TrackInfo(id = id.toString(), type = type, label = "t$id", isSelected = selected)

    @Test
    fun aListWhereNothingIsSelectedYetIsIncompleteUntilTheSelectionAppears() {
        val before = MpvTrackLists(
            audio = listOf(track(TrackType.AUDIO, 1, false), track(TrackType.AUDIO, 2, false)),
            video = listOf(track(TrackType.VIDEO, 1, false)),
            subtitle = emptyList(),
        )
        assertFalse(before.isComplete(listedCount = 3), "libmpv has not resolved the automatic selection yet")
        val after = MpvTrackLists(
            audio = listOf(track(TrackType.AUDIO, 1, true), track(TrackType.AUDIO, 2, false)),
            video = listOf(track(TrackType.VIDEO, 1, true)),
            subtitle = emptyList(),
        )
        assertTrue(after.isComplete(listedCount = 3))
        assertFalse(after.isComplete(listedCount = 4), "a track libmpv counts is missing from the read")
    }

    @Test
    fun aFileWithoutAudioOrVideoNeedsNoSelection() {
        assertTrue(MpvTrackLists(emptyList(), emptyList(), emptyList()).isComplete(0))
        val audioOnly = MpvTrackLists(listOf(track(TrackType.AUDIO, 1, true)), emptyList(), emptyList())
        assertTrue(audioOnly.isComplete(1))
    }
}
