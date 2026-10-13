package dev.vimivo.playback

import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

data class TrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
)

data class EngineCapabilities(
    val supportsVideo: Boolean,
    val supportsPlaybackSpeed: Boolean,
    val supportsAudioTrackSelection: Boolean,
    val supportsEmbeddedSubtitles: Boolean,
    val supportsExternalSubtitles: Boolean,
    val supportedSpeedRange: ClosedFloatingPointRange<Float> = 0.25f..4f,
)

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data object Ended : PlaybackStatus
    data class Failed(val error: PlaybackError) : PlaybackStatus
}

data class PlaybackState(
    val media: MediaItem? = null,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMillis: Long = 0,
    val durationMillis: Long? = null,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
)

sealed interface PlaybackError {
    data class Unsupported(val reason: String) : PlaybackError
    data class SourceUnavailable(val reason: String) : PlaybackError
    data class EngineFailure(val reason: String, val cause: Throwable? = null) : PlaybackError
}

interface PlayerEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>
    val capabilities: EngineCapabilities

    suspend fun open(media: MediaItem): Result<Unit>
    fun play(): Result<Unit>
    fun pause(): Result<Unit>
    fun stop(): Result<Unit>
    fun seekTo(positionMillis: Long): Result<Unit>
    fun setPlaybackSpeed(speed: Float): Result<Unit>
    fun setVolume(volume: Float): Result<Unit>
    fun selectAudioTrack(trackId: String?): Result<Unit>
    fun selectSubtitleTrack(trackId: String?): Result<Unit>
    override fun close()
}
