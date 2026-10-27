@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepTimerState(
    val deadlineEpochMillis: Long? = null,
    val stopAfterCurrent: Boolean = false,
    val fadeOutSeconds: Int = 0,
    val remainingMillis: Long? = null,
)

/**
 * Thread-safe, platform-neutral sleep timer for scheduled playback stopping.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val currentTimeProvider: () -> Long = MonotonicMillis(),
    /** Called when the time is up, with the fade-out length that was chosen so the platform can fade before it stops. */
    private val onTrigger: (fadeOutSeconds: Int) -> Unit,
) {
    private val mutableState = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun schedule(durationMinutes: Int, fadeOutSeconds: Int = 0) {
        require(durationMinutes in 1..1440) { "Sleep duration must be between 1 and 1440 minutes" }
        cancel()
        val now = currentTimeProvider()
        val durationMillis = durationMinutes * 60_000L
        val deadline = now + durationMillis

        mutableState.value = SleepTimerState(
            deadlineEpochMillis = deadline,
            stopAfterCurrent = false,
            fadeOutSeconds = fadeOutSeconds.coerceIn(0, 30),
            remainingMillis = durationMillis,
        )

        job = scope.launch {
            while (true) {
                delay(500)
                val current = currentTimeProvider()
                val remaining = deadline - current
                if (remaining <= 0) {
                    val fadeOutSeconds = mutableState.value.fadeOutSeconds
                    mutableState.value = SleepTimerState()
                    onTrigger(fadeOutSeconds)
                    break
                } else {
                    mutableState.value = mutableState.value.copy(remainingMillis = remaining)
                }
            }
        }
    }

    fun afterCurrent(fadeOutSeconds: Int = 0) {
        cancel()
        mutableState.value = SleepTimerState(
            stopAfterCurrent = true,
            fadeOutSeconds = fadeOutSeconds.coerceIn(0, 30),
        )
    }

    fun cancel() {
        job?.cancel()
        job = null
        mutableState.value = SleepTimerState()
    }

    fun consumeStopAfterCurrent(): Boolean {
        if (!mutableState.value.stopAfterCurrent) return false
        cancel()
        return true
    }
}

/** Milliseconds on a monotonic clock that really advances (one origin, read again on every call). */
internal class MonotonicMillis : () -> Long {
    private val origin = kotlin.time.TimeSource.Monotonic.markNow()
    override fun invoke(): Long = origin.elapsedNow().inWholeMilliseconds
}

/** How loud playback is while a sleep timer fades it out: 1 at the start, 0 when [fadeMillis] have passed. */
object SleepFade {
    fun volumeFraction(elapsedMillis: Long, fadeMillis: Long): Float {
        if (fadeMillis <= 0) return 0f
        return (1f - elapsedMillis.toFloat() / fadeMillis).coerceIn(0f, 1f)
    }
}
