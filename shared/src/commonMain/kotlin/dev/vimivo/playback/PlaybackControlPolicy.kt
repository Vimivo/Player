package dev.vimivo.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Platform-neutral rules for incremental player controls (keyboard shortcuts, menu commands,
 * remote controls). Engines only receive the resulting absolute values.
 */
object PlaybackControlPolicy {
    /** Speed ladder used by the faster/slower commands, matching common desktop player steps. */
    val speedSteps: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)

    const val DEFAULT_VOLUME_STEP = 0.05f
    const val FINE_VOLUME_STEP = 0.01f

    /** 100%: unamplified output. Engines that can amplify report a higher [EngineCapabilities.maxVolume]. */
    const val UNITY_VOLUME = 1f
    const val SHORT_SEEK_MILLIS = 5_000L
    const val LONG_SEEK_MILLIS = 30_000L
    const val SUBTITLE_DELAY_STEP_MILLIS = 100L
    const val AUDIO_DELAY_STEP_MILLIS = 50L
    const val MAX_SYNC_DELAY_MILLIS = 60_000L

    private const val SPEED_EPSILON = 0.01f
    private const val VOLUME_PRECISION = 100f

    fun fasterSpeed(current: Float, range: ClosedFloatingPointRange<Float> = DEFAULT_SPEED_RANGE): Float =
        speedSteps.firstOrNull { it > current + SPEED_EPSILON && it in range } ?: current.coerceIn(range)

    fun slowerSpeed(current: Float, range: ClosedFloatingPointRange<Float> = DEFAULT_SPEED_RANGE): Float =
        speedSteps.lastOrNull { it < current - SPEED_EPSILON && it in range } ?: current.coerceIn(range)

    fun isNormalSpeed(speed: Float): Boolean = abs(speed - 1f) < SPEED_EPSILON

    /**
     * Steps volume within `0..max`. A step that crosses 100% stops exactly at 100%, so amplification is
     * always a deliberate second step rather than an accidental overshoot.
     */
    fun stepVolume(current: Float, delta: Float, max: Float = UNITY_VOLUME): Float {
        val next = ((current + delta) * VOLUME_PRECISION).roundToInt().div(VOLUME_PRECISION).coerceIn(0f, max)
        val crossesUnity = (current - UNITY_VOLUME) * (next - UNITY_VOLUME) < 0f
        return if (crossesUnity) UNITY_VOLUME else next
    }

    /** Clamps a relative seek to the start and, when known, the end of the media. */
    fun seekTarget(positionMillis: Long, deltaMillis: Long, durationMillis: Long?): Long {
        val target = (positionMillis + deltaMillis).coerceAtLeast(0)
        return if (durationMillis != null && durationMillis > 0) target.coerceAtMost(durationMillis) else target
    }

    fun stepDelay(currentMillis: Long, deltaMillis: Long): Long =
        (currentMillis + deltaMillis).coerceIn(-MAX_SYNC_DELAY_MILLIS, MAX_SYNC_DELAY_MILLIS)

    /**
     * Returns the track that follows the selected one. When [allowOff] is true the cycle passes
     * through "off" ([TrackCycleResult.Disable]) after the last track, like subtitle cycling.
     */
    fun nextTrack(tracks: List<TrackInfo>, allowOff: Boolean): TrackCycleResult {
        if (tracks.isEmpty()) return TrackCycleResult.Unavailable
        val selectedIndex = tracks.indexOfFirst(TrackInfo::isSelected)
        val nextIndex = selectedIndex + 1
        return when {
            selectedIndex == -1 -> TrackCycleResult.Select(tracks.first())
            nextIndex < tracks.size -> TrackCycleResult.Select(tracks[nextIndex])
            allowOff -> TrackCycleResult.Disable
            else -> TrackCycleResult.Select(tracks.first())
        }
    }

    /**
     * Advances a single "A-B loop" command: set A, then set B (enabling the loop), then clear.
     * A B point at or before A moves A instead so the loop never becomes empty.
     */
    fun nextABRepeat(current: ABRepeatRange, positionMillis: Long): ABRepeatRange {
        val position = positionMillis.coerceAtLeast(0)
        val pointA = current.pointA
        return when {
            pointA == null -> ABRepeatRange(pointA = position)
            current.pointB == null && position > pointA -> ABRepeatRange(pointA, position, enabled = true)
            current.pointB == null -> ABRepeatRange(pointA = position)
            else -> ABRepeatRange()
        }
    }

    /** Moves one end of a complete A–B region, keeping A before B by [AB_MIN_GAP_MILLIS] and inside the media. */
    fun moveABPoint(range: ABRepeatRange, end: ABRepeatEnd, positionMillis: Long, durationMillis: Long): ABRepeatRange {
        val a = range.pointA ?: return range
        val b = range.pointB ?: return range
        val last = durationMillis.coerceAtLeast(AB_MIN_GAP_MILLIS)
        return when (end) {
            ABRepeatEnd.A -> range.copy(pointA = positionMillis.coerceIn(0, (b - AB_MIN_GAP_MILLIS).coerceAtLeast(0)))
            ABRepeatEnd.B -> range.copy(pointB = positionMillis.coerceIn(a + AB_MIN_GAP_MILLIS, last))
        }
    }

    const val AB_MIN_GAP_MILLIS = 500L

    private val DEFAULT_SPEED_RANGE = 0.25f..4f
}

sealed interface TrackCycleResult {
    data class Select(val track: TrackInfo) : TrackCycleResult
    data object Disable : TrackCycleResult
    data object Unavailable : TrackCycleResult
}
