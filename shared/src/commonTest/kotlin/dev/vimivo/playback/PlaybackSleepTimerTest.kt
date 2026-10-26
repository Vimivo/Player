package dev.vimivo.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSleepTimerTest {
    private class FakePlayer(var level: Float = 1f, var playing: Boolean = true) : SleepTimerPlayer {
        val volumes = mutableListOf<Float>()
        var pauses = 0
        override fun volume() = level
        override fun setVolume(volume: Float) {
            level = volume
            volumes += volume
        }
        override fun isPlaying() = playing
        override fun pause() {
            playing = false
            pauses++
        }
    }

    @Test
    fun theOptionsAreTheSameEverywhere() {
        assertEquals(listOf(15, 30, 45, 60), SleepTimerOptions.MINUTES)
        assertEquals(10, SleepTimerOptions.FADE_SECONDS)
    }

    @Test
    fun whenTheTimeIsUpTheVolumeFallsThenPlaybackPausesAndTheVolumeIsRestored() = runTest {
        val player = FakePlayer(level = 0.8f)
        var now = 0L
        val timer = PlaybackSleepTimer(backgroundScope, { now }, player)
        timer.start(minutes = 1, fadeSeconds = 2)
        now += 61_000
        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1, player.pauses)
        assertTrue(player.volumes.any { it in 0.05f..0.7f }, "it faded through the middle: ${player.volumes}")
        assertEquals(0.8f, player.level, "the volume is put back after the pause")
        assertFalse(timer.state.value.stopAfterCurrent)
        assertEquals(null, timer.state.value.deadlineEpochMillis, "the timer is spent")
    }

    @Test
    fun pausingDuringTheFadeEndsItWithoutAnotherPauseAndRestoresTheVolume() = runTest {
        val player = FakePlayer()
        val timer = PlaybackSleepTimer(backgroundScope, { 0L }, player)
        timer.expireNow(fadeSeconds = 5)
        runCurrent()
        advanceTimeBy(1_000)
        player.playing = false // the person pressed pause
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0, player.pauses, "it did not pause something already paused")
        assertEquals(1f, player.level)
    }

    @Test
    fun cancellingAFadePutsTheVolumeBack() = runTest {
        val player = FakePlayer()
        val timer = PlaybackSleepTimer(backgroundScope, { 0L }, player)
        timer.expireNow(fadeSeconds = 5)
        runCurrent()
        advanceTimeBy(2_000)
        timer.cancel()
        runCurrent()
        assertEquals(1f, player.level)
        assertEquals(0, player.pauses)
    }

    @Test
    fun afterTheCurrentTrackIsAQueueRequestThatIsUsedOnce() = runTest {
        val timer = PlaybackSleepTimer(backgroundScope, { 0L }, FakePlayer())
        assertFalse(timer.armed)
        timer.afterCurrent()
        assertTrue(timer.armed)
        assertTrue(timer.consume())
        assertFalse(timer.armed)
        assertFalse(timer.consume())
    }

    @Test
    fun noFadeMeansTheTimerPausesAtOnce() = runTest {
        val player = FakePlayer()
        val timer = PlaybackSleepTimer(backgroundScope, { 0L }, player)
        timer.expireNow(fadeSeconds = 0)
        runCurrent()
        assertEquals(1, player.pauses)
        assertEquals(1f, player.level)
    }
}
