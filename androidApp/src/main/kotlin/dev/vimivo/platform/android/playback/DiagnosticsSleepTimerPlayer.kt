package dev.vimivo.platform.android.playback

import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.SleepTimerPlayer

/** Android playback as the shared sleep timer sees it: the active engine's volume, state and pause. */
class DiagnosticsSleepTimerPlayer(private val diagnostics: PlaybackDiagnostics) : SleepTimerPlayer {
    override fun volume(): Float = diagnostics.state.value.volume

    override fun setVolume(volume: Float) {
        diagnostics.setVolume(volume)
    }

    override fun isPlaying(): Boolean = diagnostics.state.value.status == PlaybackStatus.Playing

    override fun pause() {
        diagnostics.pause()
    }
}
