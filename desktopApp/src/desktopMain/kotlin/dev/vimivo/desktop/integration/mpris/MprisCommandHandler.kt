package dev.vimivo.desktop.integration.mpris

import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.QueueRepeatMode

/** The queue and app-level actions MPRIS can trigger. */
internal interface MprisQueueActions {
    val queueState: PlaybackQueueState
    suspend fun togglePlayback()
    suspend fun next()
    suspend fun previous()
    fun toggleShuffle()
    fun cycleRepeat()
}

/**
 * Applies MPRIS requests to the player. Suspend queue operations are handed to [launch]; everything
 * else runs synchronously on the caller's (D-Bus) thread.
 */
internal class MprisCommandHandler(
    private val engine: PlayerEngine,
    private val queue: MprisQueueActions,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    /** Returns the new position in milliseconds when the command moved it, for the `Seeked` signal. */
    fun handle(command: MprisCommand): Long? = when (command) {
        is MprisCommand.Seek -> seek(engine.state.value, command.offsetMicros)
        is MprisCommand.SetPosition -> setPosition(engine.state.value, command)
        is MprisCommand.SetVolume, is MprisCommand.SetRate, is MprisCommand.SetShuffle, is MprisCommand.SetLoopStatus ->
            null.also { applyProperty(command) }
        else -> null.also { applyTransport(command) }
    }

    private fun applyTransport(command: MprisCommand) {
        when (command) {
            MprisCommand.PlayPause -> launch(queue::togglePlayback)
            // "Play" on a restored, not yet opened queue must open it, which togglePlayback does.
            MprisCommand.Play -> if (engine.state.value.media == null) launch(queue::togglePlayback) else engine.play()
            MprisCommand.Pause -> engine.pause()
            MprisCommand.Stop -> engine.stop()
            MprisCommand.Next -> launch(queue::next)
            MprisCommand.Previous -> launch(queue::previous)
            else -> Unit
        }
    }

    private fun applyProperty(command: MprisCommand) {
        when (command) {
            is MprisCommand.SetVolume ->
                engine.setVolume(MprisRequests.engineVolume(command.volume, engine.capabilities.maxVolume))
            is MprisCommand.SetRate -> if (command.rate > 0) {
                engine.setPlaybackSpeed(command.rate.toFloat().coerceIn(PlaybackSpeeds.RANGE))
            }
            is MprisCommand.SetShuffle -> if (command.enabled != queue.queueState.shuffleEnabled) queue.toggleShuffle()
            is MprisCommand.SetLoopStatus -> setLoopStatus(command.status)
            else -> Unit
        }
    }

    /** The specification asks players to move to the next track when a seek passes the end. */
    private fun seek(state: PlaybackState, offsetMicros: Long): Long? {
        if (state.media == null) return null
        val offsetMillis = MprisModel.microsToMillis(offsetMicros)
        val target = PlaybackControlPolicy.seekTarget(state.positionMillis, offsetMillis, durationMillis = null)
        val duration = state.durationMillis
        if (duration != null && duration > 0 && target >= duration) {
            launch(queue::next)
            return null
        }
        return engine.seekTo(target).map { target }.getOrNull()
    }

    /** Ignored unless it targets the current track and lies inside it, as the specification requires. */
    private fun setPosition(state: PlaybackState, command: MprisCommand.SetPosition): Long? {
        val position = MprisModel.microsToMillis(command.positionMicros)
        val duration = state.durationMillis
        val valid = state.media != null &&
            command.trackId == MprisModel.toTrackId(state.media) &&
            position >= 0 &&
            (duration == null || position <= duration)
        return if (valid) engine.seekTo(position).map { position }.getOrNull() else null
    }

    private fun setLoopStatus(status: String) {
        val target = when (status) {
            "None" -> QueueRepeatMode.OFF
            "Playlist" -> QueueRepeatMode.ALL
            "Track" -> QueueRepeatMode.ONE
            else -> return
        }
        // The queue only exposes "cycle"; with three modes, at most two steps reach any target.
        repeat(QueueRepeatMode.entries.size) {
            if (queue.queueState.repeatMode == target) return
            queue.cycleRepeat()
        }
    }
}
