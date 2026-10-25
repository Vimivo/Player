package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrowingFilePolicyTest {
    private val opened = FileSnapshot(sizeBytes = 1_000, modifiedAtMillis = 100_000)

    @Test
    fun aLargerFileIsGrowing() {
        assertTrue(GrowingFilePolicy.isGrowing(opened, opened.copy(sizeBytes = 2_000), nowMillis = 900_000))
    }

    @Test
    fun aRecentlyModifiedFileIsGrowingEvenAtTheSameSize() {
        assertTrue(GrowingFilePolicy.isGrowing(opened, opened.copy(modifiedAtMillis = 998_000), nowMillis = 1_000_000))
    }

    @Test
    fun aFinishedFileIsNotGrowing() {
        assertFalse(GrowingFilePolicy.isGrowing(opened, opened, nowMillis = 1_000_000))
    }

    @Test
    fun aModifiedTimeInTheFutureDoesNotCountAsActive() {
        // Clock skew (a file on a network share written by another machine) must not keep a finished file looping.
        val skewed = opened.copy(modifiedAtMillis = 2_000_000)
        assertFalse(GrowingFilePolicy.isGrowing(opened, skewed, nowMillis = 1_000_000))
    }

    @Test
    fun reloadsAreSpacedByTheReloadInterval() {
        val bigger = opened.copy(sizeBytes = 5_000)
        assertTrue(GrowingFilePolicy.shouldReload(opened, bigger, nowMillis = 10_000, lastReloadMillis = null))
        assertFalse(GrowingFilePolicy.shouldReload(opened, bigger, nowMillis = 10_500, lastReloadMillis = 10_000))
        assertTrue(GrowingFilePolicy.shouldReload(opened, bigger, nowMillis = 11_000, lastReloadMillis = 10_000))
    }

    @Test
    fun noReloadWhenTheFileIsNotGrowingEvenIfTheIntervalPassed() {
        assertFalse(GrowingFilePolicy.shouldReload(opened, opened, nowMillis = 1_000_000, lastReloadMillis = null))
    }
}
