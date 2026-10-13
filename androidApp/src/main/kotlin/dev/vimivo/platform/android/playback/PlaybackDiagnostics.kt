package dev.vimivo.platform.android.playback

import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local, read-mostly bridge for engine details not represented by MediaController. */
class PlaybackDiagnostics {
    private val mutableState = MutableStateFlow(PlaybackState())
    private var manager: PlaybackEngineManager? = null
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    fun attach(manager: PlaybackEngineManager) {
        this.manager = manager
    }

    fun publish(state: PlaybackState) {
        mutableState.value = state
    }

    fun selectAudioTrack(id: String?): Result<Unit> = manager
        ?.selectAudioTrack(id)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun selectSubtitleTrack(id: String?): Result<Unit> = manager
        ?.selectSubtitleTrack(id)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun detach() {
        manager = null
        mutableState.value = PlaybackState()
    }
}
