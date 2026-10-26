package dev.vimivo.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * The shared [PlaybackSleepTimer] for the iOS app: the same countdown, fade and "after this track" as on the other
 * platforms. It reads the wall clock, so a timer that was running while the app was in the background (audio keeps it
 * alive) is still right when it next runs, and it reports every change to [onChange] on the main thread so SwiftUI can
 * show the countdown. The player (volume, playing, pause) is the Swift engine, passed as [player].
 */
class IosSleepTimer(
    clock: () -> Long,
    player: SleepTimerPlayer,
    private val onChange: (SleepTimerState) -> Unit,
) {
    constructor(player: SleepTimerPlayer, onChange: (SleepTimerState) -> Unit) :
        this(::wallClockMillis, player, onChange)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val timer = PlaybackSleepTimer(scope, clock, player)

    init {
        scope.launch { timer.state.collect { onChange(it) } }
    }

    fun schedule(durationMinutes: Int, fadeOutSeconds: Int) = timer.start(durationMinutes, fadeOutSeconds)

    fun afterCurrent() = timer.afterCurrent()

    fun cancel() = timer.cancel()

    /** True once when "stop after this track" was armed; the player calls it as a track ends. */
    fun consumeStopAfterCurrent(): Boolean = timer.consume()

    /** Runs the end of the countdown now (tests and manual checks). */
    fun expireNow(fadeOutSeconds: Int) = timer.expireNow(fadeOutSeconds)

    fun close() {
        timer.close()
        scope.cancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun wallClockMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000).toLong()
