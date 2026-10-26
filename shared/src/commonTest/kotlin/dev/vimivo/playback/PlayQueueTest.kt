package dev.vimivo.playback

import dev.vimivo.storage.StoredBookmark
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayQueueTest {
    private fun queue(vararg ids: String, start: Int = 0, seed: Int = 7) =
        PlayQueue(Random(seed)).apply { replace(ids.toList(), start) }

    @Test
    fun nextWalksTheListAndStopsAtTheEndUnlessRepeatAll() {
        val q = queue("a", "b", "c")
        assertEquals(1, q.next())
        assertEquals(2, q.next())
        assertNull(q.next(), "end of the queue stops")
        assertEquals(2, q.currentIndex, "a stop leaves the position alone")
        q.cycleRepeat() // ALL
        assertEquals(0, q.next(), "repeat all wraps")
    }

    @Test
    fun repeatOnePlaysTheSameItemWhenATrackEndsButNotWhenThePersonSkips() {
        val q = queue("a", "b", "c", start = 1)
        q.cycleRepeat()
        q.cycleRepeat() // ONE
        assertEquals(QueueRepeatMode.ONE, q.repeat)
        assertEquals(1, q.next(automatic = true))
        assertEquals(2, q.next(automatic = false), "the next button always moves on")
    }

    @Test
    fun previousRestartsAfterThreeSecondsElseMovesBack() {
        val q = queue("a", "b", "c", start = 1)
        assertEquals(PlayQueue.RESTART, q.previous(positionMillis = 5_000))
        assertEquals(1, q.currentIndex)
        assertEquals(0, q.previous(positionMillis = 1_000))
        assertEquals(PlayQueue.RESTART, q.previous(positionMillis = 0), "nothing before the first item restarts it")
        q.cycleRepeat()
        assertEquals(2, q.previous(positionMillis = 0), "repeat all goes to the last item")
    }

    @Test
    fun followingIndexIsWhatGaplessPlaybackQueuesBehindTheCurrentItem() {
        val q = queue("a", "b")
        assertEquals(1, q.followingIndex())
        q.next()
        assertNull(q.followingIndex(), "nothing follows the last item")
        q.cycleRepeat() // ALL
        assertEquals(0, q.followingIndex())
        q.cycleRepeat() // ONE
        assertNull(q.followingIndex())
        assertNull(queue("only").followingIndex())
    }

    @Test
    fun shuffleKeepsTheCurrentItemFirstAndTurningItOffRestoresTheOrder() {
        val q = queue("a", "b", "c", "d", "e", start = 2)
        q.toggleShuffle()
        assertEquals("c", q.currentId)
        assertEquals(0, q.currentIndex)
        assertEquals(setOf("a", "b", "c", "d", "e"), q.ids.toSet())
        assertNotEquals(listOf("c", "a", "b", "d", "e"), q.ids, "this seed does shuffle the rest")
        q.next()
        q.toggleShuffle()
        assertEquals(listOf("a", "b", "c", "d", "e"), q.ids)
        assertEquals(q.ids.indexOf(q.currentId), q.currentIndex)
    }

    @Test
    fun itemsAddedWhileShuffledSurviveTurningShuffleOffAtTheEnd() {
        val q = queue("a", "b", "c")
        q.toggleShuffle()
        q.append("x")
        q.toggleShuffle()
        assertEquals(listOf("a", "b", "c", "x"), q.ids)
    }

    @Test
    fun anItemRemovedWhileShuffledDoesNotComeBackWhenShuffleIsOff() {
        val q = queue("a", "b", "c")
        q.toggleShuffle()
        q.remove(q.ids.indexOf("b"))
        q.toggleShuffle()
        assertEquals(listOf("a", "c"), q.ids)
    }

    @Test
    fun replacingAShuffledQueueShufflesTheNewOne() {
        val q = queue("a", "b", "c")
        q.toggleShuffle()
        q.replace(listOf("1", "2", "3", "4", "5", "6"), startIndex = 3)
        assertTrue(q.shuffled)
        assertEquals("4", q.currentId, "the chosen item still plays first")
        assertEquals(0, q.currentIndex)
        assertEquals(6, q.size)
    }

    @Test
    fun removingKeepsTheCurrentItemCurrent() {
        val q = queue("a", "b", "c", "d", start = 2)
        assertFalse(q.remove(0), "an earlier item is not the current one")
        assertEquals("c", q.currentId)
        assertTrue(q.remove(q.currentIndex), "removing the current item says so")
        assertEquals("d", q.currentId, "the next item takes its place")
        assertTrue(q.remove(q.currentIndex))
        assertEquals("b", q.currentId, "the last item removed falls back to the one before")
    }

    @Test
    fun movingKeepsTheCurrentItemCurrentWhereverItGoes() {
        val q = queue("a", "b", "c", "d", start = 1)
        q.move(0, 3)
        assertEquals(listOf("b", "c", "d", "a"), q.ids)
        assertEquals("b", q.currentId)
        q.move(0, 2)
        assertEquals("b", q.currentId, "moving the current item itself")
        q.move(3, 0)
        assertEquals("b", q.currentId)
        assertEquals(q.ids.indexOf("b"), q.currentIndex)
    }

    @Test
    fun playNextGoesRightBehindTheCurrentItem() {
        val q = queue("a", "b", "c", start = 1)
        q.insertNext("x")
        assertEquals(listOf("a", "b", "x", "c"), q.ids)
        val empty = PlayQueue()
        empty.insertNext("only")
        assertEquals(listOf("only"), empty.ids)
    }

    @Test
    fun skippingAFailedItemMovesOnUntilEveryItemHasFailed() {
        val q = queue("a", "b", "c")
        assertEquals(1, q.skipFailed(consecutiveFailures = 1))
        assertEquals(2, q.skipFailed(consecutiveFailures = 2))
        assertNull(q.skipFailed(consecutiveFailures = 3), "all three failed: stop")
        assertNull(queue("a", "b").also { it.next() }.skipFailed(1), "the end of a queue that does not repeat stops")
        val looping = queue("a", "b")
        looping.next()
        looping.cycleRepeat()
        assertEquals(0, looping.skipFailed(1), "repeat all wraps")
    }

    @Test
    fun aSavedQueueIsRestoredWithoutItemsThatAreGone() {
        val q = PlayQueue()
        q.restore(listOf("a", "b", "c", "d"), savedIndex = 2, available = setOf("a", "c", "d"))
        assertEquals(listOf("a", "c", "d"), q.ids)
        assertEquals("c", q.currentId)
        q.restore(listOf("a", "b"), savedIndex = 1, available = setOf("a"))
        assertEquals("a", q.currentId, "the current item is gone, so the first one is current")
        q.restore(listOf("a"), savedIndex = 0, available = emptySet())
        assertTrue(q.ids.isEmpty())
        assertNull(q.currentId)
    }

    @Test
    fun theSnapshotIsWhatGetsPersisted() {
        val q = queue("a", "b", "c", start = 1)
        assertEquals(PlayQueueSnapshot(listOf("a", "b", "c"), 1), q.snapshot())
    }

    @Test
    fun bookmarksAreNamedByTimeAndDuplicatesAreRefused() {
        assertEquals("0:05", Bookmarks.defaultName(5_000))
        assertEquals("12:34", Bookmarks.defaultName(754_000))
        assertEquals("1:02:03", Bookmarks.defaultName(3_723_000))
        assertEquals("0:00", Bookmarks.defaultName(-5))
        val existing = listOf(StoredBookmark("1", "m", "x", 10_000, 0))
        assertNull(Bookmarks.create("m", 10_400, existing, "2", 1), "within a second of an existing one")
        val created = Bookmarks.create("m", 12_000, existing, "2", 99)
        assertEquals(StoredBookmark("2", "m", "0:12", 12_000, 99), created)
        val other = Bookmarks.create("other", 10_000, existing, "3", 1)
        assertEquals("other", other?.mediaId, "another track is unaffected")
    }
}
