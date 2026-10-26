@file:Suppress("ArgumentListWrapping", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackControlPolicyTest {
    @Test
    fun speedLadderCoversQuarterToFourTimes() {
        assertEquals(1.25f, PlaybackControlPolicy.fasterSpeed(1f))
        assertEquals(0.75f, PlaybackControlPolicy.slowerSpeed(1f))
        assertEquals(4f, PlaybackControlPolicy.fasterSpeed(3f))
        assertEquals(4f, PlaybackControlPolicy.fasterSpeed(4f))
        assertEquals(0.25f, PlaybackControlPolicy.slowerSpeed(0.25f))
    }

    @Test
    fun speedStepsSnapOffLadderValuesAndRespectEngineRange() {
        assertEquals(1.5f, PlaybackControlPolicy.fasterSpeed(1.3f))
        assertEquals(1.25f, PlaybackControlPolicy.slowerSpeed(1.3f))
        assertEquals(2f, PlaybackControlPolicy.fasterSpeed(2f, 0.5f..2f))
        assertEquals(0.5f, PlaybackControlPolicy.slowerSpeed(0.5f, 0.5f..2f))
        assertTrue(PlaybackControlPolicy.isNormalSpeed(1.001f))
    }

    @Test
    fun volumeStepsAreClampedAndRounded() {
        assertEquals(0.55f, PlaybackControlPolicy.stepVolume(0.5f, 0.05f))
        assertEquals(1f, PlaybackControlPolicy.stepVolume(0.98f, 0.05f))
        assertEquals(0f, PlaybackControlPolicy.stepVolume(0.02f, -0.05f))
    }

    @Test
    fun amplifiedVolumeClampsToEngineMaximumAndPausesAtUnity() {
        assertEquals(1f, PlaybackControlPolicy.stepVolume(0.98f, 0.05f, max = 2f))
        assertEquals(1.05f, PlaybackControlPolicy.stepVolume(1f, 0.05f, max = 2f))
        assertEquals(2f, PlaybackControlPolicy.stepVolume(1.98f, 0.05f, max = 2f))
        assertEquals(1f, PlaybackControlPolicy.stepVolume(1.03f, -0.05f, max = 2f))
        assertEquals(1.36f, PlaybackControlPolicy.stepVolume(1.35f, PlaybackControlPolicy.FINE_VOLUME_STEP, max = 2f))
    }

    @Test
    fun seekTargetIsClampedToMediaBounds() {
        assertEquals(0L, PlaybackControlPolicy.seekTarget(3_000, -5_000, 60_000))
        assertEquals(60_000L, PlaybackControlPolicy.seekTarget(58_000, 5_000, 60_000))
        assertEquals(63_000L, PlaybackControlPolicy.seekTarget(58_000, 5_000, null))
    }

    @Test
    fun syncDelayIsBounded() {
        assertEquals(-100L, PlaybackControlPolicy.stepDelay(0, -100))
        assertEquals(PlaybackControlPolicy.MAX_SYNC_DELAY_MILLIS, PlaybackControlPolicy.stepDelay(59_990, 100))
    }

    @Test
    fun subtitleCyclePassesThroughOff() {
        val tracks = listOf(track("1", selected = false), track("2", selected = true))
        assertEquals(TrackCycleResult.Disable, PlaybackControlPolicy.nextTrack(tracks, allowOff = true))
        assertEquals(TrackCycleResult.Select(tracks[0]), PlaybackControlPolicy.nextTrack(tracks, allowOff = false))
        val none = tracks.map { it.copy(isSelected = false) }
        assertEquals(TrackCycleResult.Select(none[0]), PlaybackControlPolicy.nextTrack(none, allowOff = true))
        assertEquals(TrackCycleResult.Select(tracks[1]), PlaybackControlPolicy.nextTrack(listOf(track("1", true), tracks[1]), allowOff = true))
        assertEquals(TrackCycleResult.Unavailable, PlaybackControlPolicy.nextTrack(emptyList(), allowOff = true))
    }

    @Test
    fun abLoopCommandSetsAThenBThenClears() {
        val a = PlaybackControlPolicy.nextABRepeat(ABRepeatRange(), 10_000)
        assertEquals(ABRepeatRange(pointA = 10_000), a)
        val ab = PlaybackControlPolicy.nextABRepeat(a, 25_000)
        assertEquals(ABRepeatRange(10_000, 25_000, enabled = true), ab)
        assertEquals(ABRepeatRange(), PlaybackControlPolicy.nextABRepeat(ab, 30_000))
    }

    @Test
    fun abLoopNeverCreatesAnEmptyRange() {
        val a = ABRepeatRange(pointA = 10_000)
        assertEquals(ABRepeatRange(pointA = 4_000), PlaybackControlPolicy.nextABRepeat(a, 4_000))
        assertEquals(ABRepeatRange(pointA = 10_000), PlaybackControlPolicy.nextABRepeat(a, 10_000))
    }

    private fun track(id: String, selected: Boolean) =
        TrackInfo(id = id, type = TrackType.SUBTITLE, label = "Track $id", isSelected = selected)

    @Test
    fun draggingAnEndKeepsTheRegionOrderedAndInsideTheMedia() {
        val range = ABRepeatRange(pointA = 10_000, pointB = 20_000, enabled = true)
        val duration = 60_000L
        assertEquals(
            19_500L,
            PlaybackControlPolicy.moveABPoint(range, ABRepeatEnd.A, 40_000, duration).pointA,
        )
        assertEquals(0L, PlaybackControlPolicy.moveABPoint(range, ABRepeatEnd.A, -5, duration).pointA)
        assertEquals(10_500L, PlaybackControlPolicy.moveABPoint(range, ABRepeatEnd.B, 1_000, duration).pointB)
        assertEquals(60_000L, PlaybackControlPolicy.moveABPoint(range, ABRepeatEnd.B, 90_000, duration).pointB)
        assertEquals(15_000L, PlaybackControlPolicy.moveABPoint(range, ABRepeatEnd.B, 15_000, duration).pointB)
    }

    @Test
    fun anIncompleteRegionCannotBeDragged() {
        val onlyA = ABRepeatRange(pointA = 5_000)
        assertEquals(onlyA, PlaybackControlPolicy.moveABPoint(onlyA, ABRepeatEnd.B, 9_000, 60_000))
    }
}
