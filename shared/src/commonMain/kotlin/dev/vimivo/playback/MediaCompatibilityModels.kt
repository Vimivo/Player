@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.playback

enum class HardwareAccelerationMode(val label: String) {
    AUTO("Automatic (Hardware with Software Fallback)"),
    SOFTWARE("Software Only (CPU)"),
    HARDWARE_PREFERRED("Hardware Acceleration Preferred"),
}

enum class CodecSupportLevel {
    SUPPORTED_AND_VERIFIED,
    SOFTWARE_ONLY_VERIFIED,
    HARDWARE_ACCELERATED_VERIFIED,
    FALLBACK_VERIFIED,
    UNSUPPORTED,
    UNKNOWN,
}

enum class ColorTransfer(val label: String, val isHdr: Boolean) {
    SDR_BT709("SDR (BT.709)", false),
    HDR10_PQ("HDR10 (SMPTE ST 2084 / PQ)", true),
    HLG("HDR (ARIB STD-B67 / HLG)", true),
    UNKNOWN("Unknown / Standard", false),
}

enum class ColorSpace(val label: String) {
    BT709("ITU-R BT.709"),
    BT2020("ITU-R BT.2020 (Wide Color Gamut)"),
    BT601("ITU-R BT.601 (SD)"),
    UNKNOWN("Standard / Unknown"),
}

data class VideoStreamDetails(
    val codec: String,
    val profile: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitDepth: Int = 8,
    val colorTransfer: ColorTransfer = ColorTransfer.SDR_BT709,
    val colorSpace: ColorSpace = ColorSpace.BT709,
    val isHardwareAccelerated: Boolean = false,
)

data class AudioStreamDetails(
    val codec: String,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val bitrate: Int? = null,
    val sampleFormat: String? = null,
)

data class SubtitleStreamDetails(
    val id: String,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
)

data class MediaProbeDetails(
    val container: String,
    val durationMillis: Long? = null,
    val bitrate: Int? = null,
    val videoStreams: List<VideoStreamDetails> = emptyList(),
    val audioStreams: List<AudioStreamDetails> = emptyList(),
    val subtitleStreams: List<SubtitleStreamDetails> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
    val overallCompatibility: CodecSupportLevel = CodecSupportLevel.SUPPORTED_AND_VERIFIED,
    val statusMessage: String = "Compatible",
)

data class PlaybackDiagnosticsSnapshot(
    val engineId: EngineId,
    val mediaUri: String,
    val activeVideoCodec: String? = null,
    val activeAudioCodec: String? = null,
    val activeDecoder: String? = null,
    val hwdecActive: Boolean = false,
    val hwdecMode: String? = null,
    val activeRenderer: String? = null,
    val droppedFrames: Long = 0,
    val totalFrames: Long = 0,
    val avDriftMillis: Double = 0.0,
    val estimatedFps: Float = 0f,
    val cacheBufferBytes: Long = 0,
    val colorTransfer: ColorTransfer? = null,
    val positionMillis: Long = 0,
    val durationMillis: Long? = null,
)

interface MediaProbeService {
    suspend fun probeUri(uri: String): Result<MediaProbeDetails>
}
