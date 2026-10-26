package dev.vimivo.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The sleep timer's choices and fade, the same on every platform. */
object SleepTimerOptions {
    val MINUTES: List<Int> = listOf(15, 30, 45, 60)

    /** The fade-out before a timed stop. "After the current track" needs none: the track has already ended. */
    const val FADE_SECONDS: Int = 10
}

/** What a sleep timer needs from a platform's player: its volume, whether it is playing, and pausing. */
interface SleepTimerPlayer {
    fun volume(): Float
    fun setVolume(volume: Float)
    fun isPlaying(): Boolean
    fun pause()
}

/** Lets a sleep timer stop a queue at the end of the current item. */
interface QueueStopRequest {
    /** True while "stop after this track" is waiting, so nothing should be queued behind the track. */
    val armed: Boolean

    /** True once if it was armed, and disarms it; the queue calls this as the item ends. */
    fun consume(): Boolean

    companion object {
        val NONE: QueueStopRequest = object : QueueStopRequest {
            override val armed = false
            override fun consume() = false
        }
    }
}

/**
 * The sleep timer as the apps use it, written once: a countdown ([SleepTimer]), a fade of the player's volume and a
 * pause when the time is up, and "stop after the current track". It never keeps its own time: [clock] is
 * the platform's wall clock in milliseconds, so a timer that ran while the app was in the background is still right.
 */
class PlaybackSleepTimer(
    private val scope: CoroutineScope,
    clock: () -> Long,
    private val player: SleepTimerPlayer,
) : QueueStopRequest, AutoCloseable {
    private val timer = SleepTimer(scope, clock) { fadeSeconds -> startFade(fadeSeconds) }
    private var fadeJob: Job? = null

    val state: StateFlow<SleepTimerState> = timer.state

    /** Fades out and pauses after [minutes]. */
    fun start(minutes: Int, fadeSeconds: Int = SleepTimerOptions.FADE_SECONDS) {
        cancelFade()
        timer.schedule(minutes, fadeSeconds)
    }

    /** Stops when the current track ends; the queue asks through [consume]. */
    fun afterCurrent() {
        cancelFade()
        timer.afterCurrent(0)
    }

    /** Cancels the timer, and a fade already under way (the volume is put back). */
    fun cancel() {
        cancelFade()
        timer.cancel()
    }

    override val armed: Boolean get() = timer.state.value.stopAfterCurrent

    override fun consume(): Boolean = timer.consumeStopAfterCurrent()

    /** Runs the end of the countdown now: fade out, pause, put the volume back. For tests and manual checks. */
    fun expireNow(fadeSeconds: Int = SleepTimerOptions.FADE_SECONDS) = startFade(fadeSeconds)

    private fun startFade(fadeSeconds: Int) {
        cancelFade()
        fadeJob = scope.launch { fadeOutAndPause(fadeSeconds) }
    }

    private fun cancelFade() {
        fadeJob?.cancel()
        fadeJob = null
    }

    /** Pressing play during the fade ends it: the volume goes back and nothing is paused. */
    private suspend fun fadeOutAndPause(fadeSeconds: Int) {
        val original = player.volume()
        val totalMillis = fadeSeconds * MILLIS_PER_SECOND
        var elapsed = 0L
        try {
            while (totalMillis > 0 && elapsed < totalMillis && player.isPlaying()) {
                player.setVolume(original * SleepFade.volumeFraction(elapsed, totalMillis))
                delay(FADE_STEP_MILLIS)
                elapsed += FADE_STEP_MILLIS
            }
            if (player.isPlaying()) player.pause()
        } finally {
            player.setVolume(original)
        }
    }

    override fun close() = cancel()

    private companion object {
        const val FADE_STEP_MILLIS = 100L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
