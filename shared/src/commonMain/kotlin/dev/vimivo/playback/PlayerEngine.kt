package dev.vimivo.playback

import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

enum class EngineId { MEDIA3, LIBVLC }
enum class TrackType { AUDIO, VIDEO, SUBTITLE }
enum class TrackOrigin { EMBEDDED, EXTERNAL }
enum class SupportLevel { PREFERRED, FALLBACK, UNSUPPORTED }

data class TrackInfo(
    val id: String,
    val type: TrackType,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val isSelected: Boolean = false,
    val origin: TrackOrigin = TrackOrigin.EMBEDDED,
)

data class ExternalSubtitle(
    val uri: String,
    val label: String,
    val language: String? = null,
    val mimeType: String,
)

data class PlaybackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
)

data class EngineCapabilities(
    val localAudio: Boolean,
    val localVideo: Boolean,
    val httpStreaming: Boolean,
    val hlsStreaming: Boolean = false,
    val dashStreaming: Boolean = false,
    val liveStreaming: Boolean = false,
    val hardwareDecoding: Boolean,
    val softwareDecoding: Boolean,
    val playbackSpeed: Boolean,
    val audioTrackSelection: Boolean,
    val videoTrackSelection: Boolean,
    val embeddedSubtitles: Boolean,
    val externalSubtitles: Boolean,
    val hdr: Boolean,
    val subtitleDelay: Boolean = false,
    val audioDelay: Boolean = false,
    val subtitleAppearance: Boolean = false,
    val subtitlePosition: Boolean = false,
    val supportedSpeedRange: ClosedFloatingPointRange<Float> = 0.25f..4f,
    val audioEnhancements: AudioEnhancementCapabilities = AudioEnhancementCapabilities(
        limitation = "Audio enhancements are unavailable with this engine",
    ),
)

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data object Preparing : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Ready : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data object Completed : PlaybackStatus
    data class Error(val error: PlaybackError) : PlaybackStatus
}

enum class PlaybackErrorCode {
    SOURCE_NOT_FOUND,
    ACCESS_DENIED,
    NETWORK,
    UNSUPPORTED_CONTAINER,
    DECODER_INITIALIZATION,
    DECODING_FAILED,
    ENGINE_INITIALIZATION,
    UNKNOWN,
}

data class PlaybackError(
    val code: PlaybackErrorCode,
    val message: String,
    val engine: EngineId? = null,
    val recoverableByFallback: Boolean = false,
    val cause: Throwable? = null,
)

data class QueueContext(val items: List<MediaItem>, val currentIndex: Int) {
    init {
        require(items.isNotEmpty()) { "A playback queue cannot be empty" }
        require(currentIndex in items.indices) { "Queue index is out of bounds" }
    }

    val current: MediaItem get() = items[currentIndex]
}

data class PlaybackState(
    val engine: EngineId? = null,
    val queue: QueueContext? = null,
    val media: MediaItem? = null,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMillis: Long = 0,
    val bufferedPositionMillis: Long = 0,
    val durationMillis: Long? = null,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val isSeekable: Boolean = false,
    val metadata: PlaybackMetadata = PlaybackMetadata(),
    val audioTracks: List<TrackInfo> = emptyList(),
    val videoTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
)

interface PlayerEngine : AutoCloseable {
    val id: EngineId
    val state: StateFlow<PlaybackState>
    val capabilities: EngineCapabilities
    val audioEnhancementState: StateFlow<AudioEnhancementState>

    fun supportFor(media: MediaItem): SupportLevel
    suspend fun open(media: MediaItem, startPositionMillis: Long = 0): Result<Unit>
    fun play(): Result<Unit>
    fun pause(): Result<Unit>
    fun resume(): Result<Unit> = play()
    fun stop(): Result<Unit>
    fun seekTo(positionMillis: Long): Result<Unit>
    fun setPlaybackSpeed(speed: Float): Result<Unit>
    fun setVolume(volume: Float): Result<Unit>
    fun selectAudioTrack(trackId: String?): Result<Unit>
    fun selectVideoTrack(trackId: String?): Result<Unit>
    fun selectSubtitleTrack(trackId: String?): Result<Unit>
    fun addExternalSubtitle(subtitle: ExternalSubtitle): Result<Unit> =
        Result.failure(UnsupportedOperationException("External subtitles are not supported"))
    fun setSubtitleDelay(delayMillis: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Subtitle delay is not supported"))
    fun setAudioDelay(delayMillis: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio delay is not supported"))
    fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> =
        Result.failure(UnsupportedOperationException(capabilities.audioEnhancements.limitation))
    override fun close()
}

interface PlayerEngineFactory {
    val id: EngineId
    fun supportFor(media: MediaItem): SupportLevel
    fun create(): PlayerEngine
}
