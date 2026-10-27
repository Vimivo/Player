package dev.vimivo.desktop.playback

import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SleepTimerPlayer

/** The desktop player as the shared sleep timer sees it: the engine's volume, state and pause. */
internal class EngineSleepTimerPlayer(private val engine: PlayerEngine) : SleepTimerPlayer {
    override fun volume(): Float = engine.state.value.volume

    override fun setVolume(volume: Float) {
        engine.setVolume(volume)
    }

    override fun isPlaying(): Boolean = engine.state.value.status == PlaybackStatus.Playing

    override fun pause() {
        engine.pause()
    }
}
