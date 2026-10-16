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
    private val currentTimeProvider: () -> Long = {
        kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
    },
    private val onTrigger: () -> Unit,
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
                    mutableState.value = SleepTimerState()
                    onTrigger()
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
