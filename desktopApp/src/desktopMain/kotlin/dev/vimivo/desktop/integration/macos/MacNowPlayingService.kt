package dev.vimivo.desktop.integration.macos

import dev.vimivo.desktop.integration.mpris.MprisService
import dev.vimivo.desktop.playback.MpvNative
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** macOS MediaPlayer integration for the existing desktop engine and queue. */
internal class MacNowPlayingService(
    private val engine: MpvPlayerEngine,
    private val queue: PlaybackQueueController,
    private val togglePlayback: suspend () -> Unit,
) : MprisService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playback = PlaybackState()
    private var queueState = PlaybackQueueState()
    private var configured = false

    fun start() {
        MpvNative.loadCocoaBridge()
        check(configureNative()) { "Unable to configure macOS Now Playing commands" }
        configured = true
    }

    override fun updatePlayback(state: PlaybackState) {
        playback = state
        publish()
    }

    override fun updateQueue(queueState: PlaybackQueueState) {
        this.queueState = queueState
        publish()
    }

    private fun publish() {
        if (!configured) return
        val media = playback.media
        updateNative(
            title = media?.let { playback.metadata.title ?: it.title },
            artist = media?.let { playback.metadata.artist ?: it.artist },
            album = media?.let { playback.metadata.album ?: it.album },
            durationMillis = playback.durationMillis ?: 0L,
            positionMillis = playback.positionMillis,
            playing = playback.status == PlaybackStatus.Playing,
            rate = playback.speed,
            queueIndex = queueState.currentIndex,
            queueCount = queueState.items.size,
        )
    }

    @Suppress("unused")
    fun remoteTogglePlayback() {
        scope.launch { togglePlayback() }
    }

    @Suppress("unused")
    fun remoteNext() {
        scope.launch { queue.next() }
    }

    @Suppress("unused")
    fun remotePrevious() {
        scope.launch { queue.previous() }
    }

    @Suppress("unused")
    fun remoteSeek(positionMillis: Long) {
        engine.seekTo(positionMillis)
    }

    override fun close() {
        if (configured) closeNative()
        configured = false
        scope.cancel()
    }

    private external fun configureNative(): Boolean

    @Suppress("LongParameterList")
    private external fun updateNative(
        title: String?,
        artist: String?,
        album: String?,
        durationMillis: Long,
        positionMillis: Long,
        playing: Boolean,
        rate: Float,
        queueIndex: Int,
        queueCount: Int,
    )

    private external fun closeNative()
}
