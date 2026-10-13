@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.playback

import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

class SleepTimerController(private val diagnostics: PlaybackDiagnostics) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(SleepTimerState())
    private var timerJob: Job? = null
    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    init {
        scope.launch {
            diagnostics.state.collect { playback ->
                if (mutableState.value.stopAfterCurrent && playback.status == PlaybackStatus.Completed) expire()
            }
        }
    }

    fun schedule(durationMinutes: Int, fadeOutSeconds: Int = 0) {
        require(durationMinutes in 1..24 * 60)
        start(System.currentTimeMillis() + durationMinutes * 60_000L, false, fadeOutSeconds)
    }

    fun afterCurrent(fadeOutSeconds: Int = 0) {
        cancel()
        mutableState.value = SleepTimerState(stopAfterCurrent = true, fadeOutSeconds = fadeOutSeconds.coerceIn(0, 30))
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        mutableState.value = SleepTimerState()
    }

    private fun start(deadline: Long, afterCurrent: Boolean, fadeSeconds: Int) {
        cancel()
        mutableState.value = SleepTimerState(deadline, afterCurrent, fadeSeconds.coerceIn(0, 30), deadline - System.currentTimeMillis())
        timerJob = scope.launch {
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                mutableState.value = mutableState.value.copy(remainingMillis = remaining.coerceAtLeast(0))
                if (remaining <= 0) break
                delay(1_000L.coerceAtMost(remaining))
            }
            expire()
        }
    }

    private suspend fun expire() {
        timerJob?.cancel()
        val playback = diagnostics.state.value
        val fadeSeconds = mutableState.value.fadeOutSeconds
        val originalVolume = playback.volume
        if (fadeSeconds > 0 && playback.status == PlaybackStatus.Playing) {
            repeat(fadeSeconds * 5) { step ->
                val fraction = 1f - (step + 1f) / (fadeSeconds * 5f)
                diagnostics.setVolume(originalVolume * fraction)
                delay(200)
            }
        }
        diagnostics.pause()
        diagnostics.setVolume(originalVolume)
        mutableState.value = SleepTimerState()
    }

    override fun close() {
        timerJob?.cancel()
        scope.cancel()
    }
}
