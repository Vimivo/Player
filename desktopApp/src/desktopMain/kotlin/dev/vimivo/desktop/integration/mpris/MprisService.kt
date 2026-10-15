package dev.vimivo.desktop.integration.mpris

import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState

/**
 * Service interface for desktop media integration (e.g. Linux MPRIS 2 / D-Bus).
 */
internal interface MprisService : AutoCloseable {
    fun updatePlayback(state: PlaybackState)
    fun updateQueue(queueState: PlaybackQueueState)
    override fun close() {}
}

/**
 * Fallback no-op implementation when MPRIS or D-Bus is unavailable.
 */
internal class NoOpMprisService : MprisService {
    override fun updatePlayback(state: PlaybackState) = Unit
    override fun updateQueue(queueState: PlaybackQueueState) = Unit
    override fun close() = Unit
}
