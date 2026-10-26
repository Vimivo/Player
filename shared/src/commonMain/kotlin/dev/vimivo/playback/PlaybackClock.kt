package dev.vimivo.playback

import kotlin.math.abs

/**
 * Smooth playback position for timelines. Engines report the position a few times per second; drawing that value
 * makes the playhead jump in steps. The clock extrapolates from the last report (elapsed time × speed) on every frame
 * and blends small disagreements with new reports in gradually, so the playhead moves continuously and never jumps
 * backwards by a few pixels. Large disagreements (a seek, a new file) snap at once.
 *
 * Platform-neutral: UIs feed it engine reports and frame times in nanoseconds from any monotonic clock.
 */
class PlaybackClock(
    /** Disagreements larger than this are treated as a jump (seek) and applied immediately. */
    private val snapThresholdMillis: Long = DEFAULT_SNAP_THRESHOLD_MILLIS,
    /** How long a small disagreement takes to blend out. */
    private val correctionMillis: Long = DEFAULT_CORRECTION_MILLIS,
) {
    private var anchorPosition = 0.0
    private var anchorNanos = 0L
    private var speed = 1.0
    private var running = false
    private var duration: Long? = null

    /** Remaining offset still being blended in, and when the blend started. */
    private var correction = 0.0
    private var correctionStartNanos = 0L

    /**
     * Records an engine report.
     *
     * @param positionMillis the engine's position.
     * @param running true only while the position actually advances (playing; not paused, buffering or seeking).
     * @param snap start exactly at [positionMillis] with no blending, e.g. right after a confirmed seek.
     */
    fun report(
        positionMillis: Long,
        nowNanos: Long,
        running: Boolean,
        speed: Float,
        durationMillis: Long?,
        snap: Boolean = false,
    ) {
        val shown = positionAt(nowNanos)
        anchorPosition = positionMillis.toDouble()
        anchorNanos = nowNanos
        this.running = running
        this.speed = speed.toDouble()
        duration = durationMillis?.takeIf { it > 0 }
        val disagreement = shown - anchorPosition
        if (!snap && running && abs(disagreement) < snapThresholdMillis) {
            // Keep showing what was on screen and let the difference fade, instead of jumping by a few pixels.
            correction = disagreement
            correctionStartNanos = nowNanos
        } else {
            correction = 0.0
        }
    }

    /** The position to draw at [nowNanos], within 0..duration. */
    fun positionAt(nowNanos: Long): Double {
        val elapsedMillis = if (running) (nowNanos - anchorNanos).coerceAtLeast(0) / NANOS_PER_MILLI else 0.0
        val blend = if (correction == 0.0) {
            0.0
        } else {
            val progress = ((nowNanos - correctionStartNanos) / NANOS_PER_MILLI / correctionMillis).coerceIn(0.0, 1.0)
            correction * (1.0 - progress)
        }
        val position = anchorPosition + elapsedMillis * speed + blend
        val end = duration?.toDouble() ?: Double.MAX_VALUE
        return position.coerceIn(0.0, end)
    }

    private companion object {
        const val DEFAULT_SNAP_THRESHOLD_MILLIS = 1_500L
        const val DEFAULT_CORRECTION_MILLIS = 400L
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
