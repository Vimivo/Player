@file:Suppress("ImportOrdering")

package dev.vimivo.platform.android.playback

import android.graphics.Bitmap
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local, read-mostly bridge for engine details not represented by MediaController. */
class PlaybackDiagnostics {
    private val mutableState = MutableStateFlow(PlaybackState())
    private val mutableAudio = MutableStateFlow(
        AudioEnhancementState(AudioEnhancementCapabilities(limitation = "No active playback engine")),
    )
    private var manager: PlaybackEngineManager? = null
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    val audio: StateFlow<AudioEnhancementState> = mutableAudio.asStateFlow()

    fun attach(manager: PlaybackEngineManager) {
        this.manager = manager
    }

    fun publish(state: PlaybackState) {
        mutableState.value = state
    }

    fun publishAudio(state: AudioEnhancementState) { mutableAudio.value = state }

    fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> = manager
        ?.applyAudioEnhancements(settings)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun selectAudioTrack(id: String?): Result<Unit> = manager
        ?.selectAudioTrack(id)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun selectSubtitleTrack(id: String?): Result<Unit> = manager
        ?.selectSubtitleTrack(id)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun addExternalSubtitle(subtitle: ExternalSubtitle): Result<Unit> = manager
        ?.addExternalSubtitle(subtitle)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun setSubtitleDelay(delayMillis: Long): Result<Unit> = manager
        ?.setSubtitleDelay(delayMillis)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun setAudioDelay(delayMillis: Long): Result<Unit> = manager
        ?.setAudioDelay(delayMillis)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun pause(): Result<Unit> = manager?.pause()
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    /** Volume 0 to 2: above 1 is the software boost (limited), see [AndroidAudioEnhancementController.MAX_BOOST]. */
    fun setVolume(volume: Float): Result<Unit> =
        manager?.setVolume(volume.coerceIn(0f, AndroidAudioEnhancementController.MAX_BOOST))
            ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun setAbRepeat(range: ABRepeatRange): Result<Unit> = manager?.setABRepeat(range)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    fun frameStep(forward: Boolean): Result<Unit> = manager?.activeEngine?.value?.frameStep(forward)
        ?: Result.failure(IllegalStateException("Playback service is unavailable"))

    /** The frame on screen, from whichever engine is showing it. */
    suspend fun captureFrame(): Result<Bitmap> = when (val engine = manager?.activeEngine?.value) {
        is Media3PlayerEngine -> engine.video.captureFrame()
        is LibmpvPlayerEngine -> engine.captureFrame()
        else -> Result.failure(IllegalStateException("There is no video on screen"))
    }

    fun detach() {
        manager = null
        mutableState.value = PlaybackState()
        mutableAudio.value = AudioEnhancementState(
            AudioEnhancementCapabilities(limitation = "No active playback engine"),
        )
    }
}
