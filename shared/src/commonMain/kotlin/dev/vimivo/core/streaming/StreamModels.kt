@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.core.streaming

import kotlinx.serialization.Serializable

@Serializable
enum class StreamProtocol(val displayName: String, val isAdaptive: Boolean) {
    HTTP_PROGRESSIVE("HTTP Progressive", false),
    HTTPS_PROGRESSIVE("HTTPS Progressive", false),
    HLS("HTTP Live Streaming (HLS)", true),
    DASH("Dynamic Adaptive Streaming over HTTP (DASH)", true),
    RTSP("Real-Time Streaming Protocol (RTSP)", false),
    INTERNET_RADIO("Internet Radio Stream", false),
    UNKNOWN("Unknown Network Protocol", false),
}

@Serializable
enum class StreamType(val displayName: String) {
    VOD("Video/Audio on Demand",),
    LIVE("Live Broadcast",),
    UNKNOWN("Unknown Stream Type",),
}

@Serializable
enum class StreamConnectionState(val displayName: String) {
    IDLE("Idle"),
    VALIDATING("Validating URL"),
    CONNECTING("Connecting"),
    PREPARING("Preparing Stream"),
    BUFFERING("Buffering"),
    READY("Ready"),
    PLAYING("Playing"),
    PAUSED("Paused"),
    RECONNECTING("Reconnecting"),
    COMPLETED("Stream Completed"),
    ERROR("Connection Error"),
}

@Serializable
enum class StreamFailureReason(val userMessage: String, val isRecoverable: Boolean) {
    INVALID_URL("The provided address is not a valid URL.", false),
    UNSUPPORTED_PROTOCOL("The protocol is not supported for media streaming.", false),
    WEBPAGE_NOT_DIRECT_STREAM("This URL points to a webpage, not a direct media stream.", false),
    CONNECTION_FAILED("Could not establish a connection to the media server.", true),
    TIMEOUT("Network request timed out while contacting the stream server.", true),
    HTTP_FORBIDDEN("Access denied (403 Forbidden). The stream may require authentication or token.", false),
    NOT_FOUND("Media stream resource not found (404).", false),
    EXPIRED_URL("The streaming link or security token has expired.", false),
    DECODER_ERROR("Native playback engine encountered an unsupported codec or decoding failure.", false),
    STREAM_ENDED("The live broadcast has ended.", false),
    UNKNOWN("An unexpected network playback error occurred.", true),
}

sealed interface StreamValidationResult {
    val isValid: Boolean get() = this is Valid

    data class Valid(
        val protocol: StreamProtocol,
        val type: StreamType,
        val normalizedUrl: String,
        val isLive: Boolean,
        val displayTitle: String,
    ) : StreamValidationResult

    data class Invalid(
        val reason: StreamFailureReason,
        val message: String,
    ) : StreamValidationResult
}

@Serializable
data class StreamRequest(
    val originalUrl: String,
    val normalizedUrl: String,
    val protocol: StreamProtocol,
    val type: StreamType,
    val displayTitle: String? = null,
    val customHeaders: Map<String, String> = emptyMap(),
)

@Serializable
data class StreamDiagnostics(
    val protocol: StreamProtocol = StreamProtocol.UNKNOWN,
    val container: String? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val resolution: String? = null,
    val bitrate: Int? = null,
    val connectionState: StreamConnectionState = StreamConnectionState.IDLE,
    val retryAttempts: Int = 0,
    val httpStatus: Int? = null,
    val isLive: Boolean = false,
    val bufferedMillis: Long = 0L,
    val droppedFrames: Long = 0L,
)
