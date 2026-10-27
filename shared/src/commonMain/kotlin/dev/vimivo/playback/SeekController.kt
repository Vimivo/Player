package dev.vimivo.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Where a seek request goes: the platform's engine. `exact` = frame-accurate; otherwise a fast keyframe seek. */
fun interface SeekSink {
    fun seek(positionMillis: Long, exact: Boolean)
}

/**
 * What a timeline shows instead of the engine's position while the user seeks.
 *
 * @property scrubMillis the drag position while a drag is in progress.
 * @property pendingMillis the target of the last exact seek, until the engine confirms it landed.
 * @property seeksIssued how many seeks were sent to the engine (exact + keyframe), for tests and diagnostics.
 */
data class SeekState(
    val scrubMillis: Long? = null,
    val pendingMillis: Long? = null,
    val seeksIssued: Long = 0,
) {
    /** The position the timeline must draw, or null to draw the engine's (interpolated) position. */
    val overrideMillis: Long? get() = scrubMillis ?: pendingMillis
}

/**
 * The one seek path for every timeline, keyboard shortcut, gesture and double-tap on every platform.
 *
 * - A click (or tap, key press, double-tap, accessibility action) issues exactly one exact seek.
 * - A drag issues throttled keyframe seeks for a live preview and one exact seek on release.
 * - After an exact seek the timeline holds the target ([SeekState.pendingMillis]) until an engine report says the
 *   seek finished ([PlaybackState.isSeeking] false) at a position near the target. Reports taken before the seek
 *   landed therefore can never pull the thumb back to the old time.
 * - Relative seeks ([seekBy]) start from the pending target, so five quick presses of → move 25 s, not 5 s.
 *
 * Time is passed in (monotonic milliseconds) so the controller is pure and tested without a clock. Not thread-safe:
 * call it from the UI thread.
 */
class SeekController(
    private val sink: SeekSink,
    private val dragThrottleMillis: Long = DRAG_THROTTLE_MILLIS,
    private val confirmToleranceMillis: Long = CONFIRM_TOLERANCE_MILLIS,
    private val confirmTimeoutMillis: Long = CONFIRM_TIMEOUT_MILLIS,
) {
    private val mutableState = MutableStateFlow(SeekState())
    val state: StateFlow<SeekState> = mutableState.asStateFlow()

    /** Current value, for callers (Swift, draw phases) that read rather than collect. */
    val current: SeekState get() = mutableState.value

    private var durationMillis: Long? = null
    private var seekable = false
    private var pendingSinceMillis = 0L
    private var lastDragSeekMillis = Long.MIN_VALUE
    private var lastDragSeekTarget: Long? = null

    /** False for unknown durations and live/unseekable sources: clicks and drags are then ignored. */
    val canSeek: Boolean get() = seekable && (durationMillis ?: 0L) > 0L

    /**
     * Feed every engine report. Returns true when this report confirmed the pending seek: the caller then restarts
     * its interpolation from [positionMillis] without blending (see [PlaybackClock.report] `snap`).
     */
    fun onEngineReport(
        positionMillis: Long,
        durationMillis: Long?,
        isSeekable: Boolean,
        isSeeking: Boolean,
        nowMillis: Long,
    ): Boolean {
        this.durationMillis = durationMillis?.takeIf { it > 0 }
        seekable = isSeekable
        val pending = mutableState.value.pendingMillis ?: return false
        val landed = !isSeeking && abs(positionMillis - pending) <= confirmToleranceMillis
        // An engine that clamps the target (past the end, a damaged index) never reports it; stop holding then.
        val gaveUp = !isSeeking && nowMillis - pendingSinceMillis >= confirmTimeoutMillis
        if (!landed && !gaveUp) return false
        mutableState.value = mutableState.value.copy(pendingMillis = null)
        return true
    }

    /** Forget any hold, e.g. when a new file opens. */
    fun reset() {
        lastDragSeekTarget = null
        mutableState.value = SeekState(seeksIssued = mutableState.value.seeksIssued)
    }

    /** A click, tap, accessibility "set progress" or absolute key: one exact seek. */
    fun seekTo(targetMillis: Long, nowMillis: Long) {
        val target = clamp(targetMillis) ?: return
        issueExact(target, nowMillis)
    }

    /** Arrow keys, J/L, double-tap, wheel: relative to what the timeline shows ([shownMillis] when nothing pending). */
    fun seekBy(deltaMillis: Long, shownMillis: Long, nowMillis: Long) {
        val from = mutableState.value.overrideMillis ?: shownMillis
        seekTo(from + deltaMillis, nowMillis)
    }

    fun dragStart(targetMillis: Long, nowMillis: Long) {
        val target = clamp(targetMillis) ?: return
        lastDragSeekMillis = Long.MIN_VALUE
        lastDragSeekTarget = null
        mutableState.value = mutableState.value.copy(scrubMillis = target)
        dragSeek(target, nowMillis)
    }

    fun dragTo(targetMillis: Long, nowMillis: Long) {
        if (mutableState.value.scrubMillis == null) return
        val target = clamp(targetMillis) ?: return
        mutableState.value = mutableState.value.copy(scrubMillis = target)
        if (nowMillis - lastDragSeekMillis >= dragThrottleMillis) dragSeek(target, nowMillis)
    }

    /** Release: one exact seek to where the drag ended. */
    fun dragEnd(nowMillis: Long) {
        val target = mutableState.value.scrubMillis ?: return
        issueExact(target, nowMillis)
    }

    /** The drag was cancelled (pointer lost): the preview seeks already moved the engine, so hold their target. */
    fun dragCancel(nowMillis: Long) {
        val previewed = lastDragSeekTarget
        mutableState.value = mutableState.value.copy(scrubMillis = null)
        if (previewed != null) issueExact(previewed, nowMillis)
    }

    private fun dragSeek(target: Long, nowMillis: Long) {
        if (target == lastDragSeekTarget) return
        lastDragSeekMillis = nowMillis
        lastDragSeekTarget = target
        sink.seek(target, exact = false)
        mutableState.value = mutableState.value.let { it.copy(seeksIssued = it.seeksIssued + 1) }
    }

    private fun issueExact(target: Long, nowMillis: Long) {
        pendingSinceMillis = nowMillis
        lastDragSeekTarget = null
        sink.seek(target, exact = true)
        mutableState.value = mutableState.value.let {
            it.copy(scrubMillis = null, pendingMillis = target, seeksIssued = it.seeksIssued + 1)
        }
    }

    private fun clamp(target: Long): Long? {
        if (!canSeek) return null
        return target.coerceIn(0L, durationMillis ?: return null)
    }

    companion object {
        const val DRAG_THROTTLE_MILLIS: Long = 120L
        const val CONFIRM_TOLERANCE_MILLIS: Long = 750L
        const val CONFIRM_TIMEOUT_MILLIS: Long = 3_000L
    }
}

/**
 * Pointer x → timeline fraction, against the *track*, not the touch target: [trackStartPx] and [trackWidthPx] are the
 * drawn track's bounds inside the hit area (so padding and thumb insets do not skew the mapping), in the same pixel
 * space as [x]. In right-to-left layouts the start of the media is on the right.
 */
object TimelineGeometry {
    fun fractionAt(x: Float, trackStartPx: Float, trackWidthPx: Float, rightToLeft: Boolean = false): Float {
        if (trackWidthPx <= 0f) return 0f
        val fraction = ((x - trackStartPx) / trackWidthPx).coerceIn(0f, 1f)
        return if (rightToLeft) 1f - fraction else fraction
    }

    fun millisAt(x: Float, trackStartPx: Float, trackWidthPx: Float, durationMillis: Long, rightToLeft: Boolean = false): Long =
        (fractionAt(x, trackStartPx, trackWidthPx, rightToLeft).toDouble() * durationMillis).toLong()

    /** The inverse: where [positionMillis] is drawn. */
    fun xAt(positionMillis: Double, trackStartPx: Float, trackWidthPx: Float, durationMillis: Long, rightToLeft: Boolean = false): Float {
        if (durationMillis <= 0) return trackStartPx
        val fraction = (positionMillis / durationMillis).coerceIn(0.0, 1.0).toFloat()
        return trackStartPx + trackWidthPx * (if (rightToLeft) 1f - fraction else fraction)
    }
}
