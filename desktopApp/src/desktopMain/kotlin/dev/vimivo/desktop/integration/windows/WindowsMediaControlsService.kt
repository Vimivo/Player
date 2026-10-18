package dev.vimivo.desktop.integration.windows

import dev.vimivo.core.model.MediaKind
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

/**
 * Windows System Media Transport Controls: the volume/media flyout, lock screen controls and hardware
 * media keys. Backed by the C++/WinRT bridge in `native/windows/VimivoMediaControls.cpp`.
 */
internal class WindowsMediaControlsService(
    private val engine: MpvPlayerEngine,
    private val queue: PlaybackQueueController,
) : MprisService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playback = PlaybackState()
    private var queueState = PlaybackQueueState()
    private var configured = false

    fun start() {
        val bundled = MpvNative.findBundledWindowsDllDir()?.resolve(LIBRARY_FILE)
        if (bundled?.exists() == true) System.load(bundled.absolutePath) else System.loadLibrary(LIBRARY_NAME)
        val result = configureNative()
        check(result == 0) { "Windows media controls are unavailable (HRESULT 0x${result.toUInt().toString(HEX)})" }
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
        val index = queueState.currentIndex
        updateNative(
            title = media?.title,
            artist = media?.artist,
            album = media?.album,
            durationMillis = playback.durationMillis ?: 0L,
            positionMillis = playback.positionMillis,
            playing = playback.status == PlaybackStatus.Playing,
            video = media?.kind == MediaKind.VIDEO,
            hasNext = index in 0 until queueState.items.size - 1,
            hasPrevious = index > 0 || playback.positionMillis > 0,
        )
    }

    @Suppress("unused") // Called from the native bridge.
    fun remotePlay() {
        engine.play()
    }

    @Suppress("unused") // Called from the native bridge.
    fun remotePause() {
        engine.pause()
    }

    @Suppress("unused") // Called from the native bridge.
    fun remoteNext() {
        scope.launch { queue.next() }
    }

    @Suppress("unused") // Called from the native bridge.
    fun remotePrevious() {
        scope.launch { queue.previous() }
    }

    @Suppress("unused") // Called from the native bridge.
    fun remoteSeek(positionMillis: Long) {
        engine.seekTo(positionMillis.coerceAtLeast(0))
    }

    override fun close() {
        if (configured) closeNative()
        configured = false
        scope.cancel()
    }

    private external fun configureNative(): Int

    @Suppress("LongParameterList")
    private external fun updateNative(
        title: String?,
        artist: String?,
        album: String?,
        durationMillis: Long,
        positionMillis: Long,
        playing: Boolean,
        video: Boolean,
        hasNext: Boolean,
        hasPrevious: Boolean,
    )

    private external fun closeNative()

    private companion object {
        const val LIBRARY_NAME = "vimivo_media_controls"
        const val LIBRARY_FILE = "$LIBRARY_NAME.dll"
        const val HEX = 16
    }
}
