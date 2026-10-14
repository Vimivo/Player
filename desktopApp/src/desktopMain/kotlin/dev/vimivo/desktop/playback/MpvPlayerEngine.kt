@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "TooGenericExceptionCaught",
    "Wrapping",
)

package dev.vimivo.desktop.playback

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.PlaybackError
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackMetadata
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackOrigin
import dev.vimivo.playback.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.io.File
import java.net.URI

/** Desktop libmpv adapter. mpv owns its native output window until render embedding is introduced. */
class MpvPlayerEngine : PlayerEngine {
    override val id = EngineId.MPV
    override val capabilities = CAPABILITIES
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    private val mutableAudio = MutableStateFlow(
        AudioEnhancementState(AudioEnhancementCapabilities(limitation = "Audio processing is controlled by libmpv profiles")),
    )
    private var native: MpvNative? = null
    private var context: Pointer? = null
    private var polling: Job? = null
    private var renderer: MpvCocoaRenderer? = null
    private var videoSurface: Canvas? = null
    private var closed = false

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    override val audioEnhancementState: StateFlow<AudioEnhancementState> = mutableAudio.asStateFlow()

    override fun supportFor(media: MediaItem): SupportLevel = when (media.uri.substringBefore(':').lowercase()) {
        "file", "http", "https", "rtsp", "rtsps" -> SupportLevel.PREFERRED
        else -> SupportLevel.UNSUPPORTED
    }

    /** Must be called before opening video media; audio-only playback does not require a surface. */
    fun attachVideoSurface(canvas: Canvas): Result<Unit> = runCatching {
        check(context == null) { "Attach the video surface before initializing libmpv" }
        videoSurface = canvas
    }

    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> = runCatching {
        check(!closed) { "Player is closed" }
        require(supportFor(media) != SupportLevel.UNSUPPORTED) { "Unsupported desktop media URI" }
        ensureInitialized()
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            metadata = PlaybackMetadata(media.title, media.artist, media.album, media.artworkUri),
        )
        command("loadfile", media.uri, "replace")
        if (startPositionMillis > 0) command("seek", (startPositionMillis / 1_000.0).toString(), "absolute+exact")
        setProperty("pause", "yes")
        startPolling()
    }.onFailure(::publishFailure)

    override fun play() = action { setProperty("pause", "no") }
    override fun pause() = action { setProperty("pause", "yes") }
    override fun stop() = action { command("stop") }
    override fun seekTo(positionMillis: Long) = action {
        require(positionMillis >= 0)
        command("seek", (positionMillis / 1_000.0).toString(), "absolute+exact")
    }
    override fun setPlaybackSpeed(speed: Float) = action {
        require(speed in capabilities.supportedSpeedRange)
        setProperty("speed", speed.toString())
    }
    override fun setVolume(volume: Float) = action {
        require(volume in 0f..1f)
        setProperty("volume", (volume * 100).toString())
    }
    override fun selectAudioTrack(trackId: String?) = action { setProperty("aid", trackId ?: "no") }
    override fun selectVideoTrack(trackId: String?) = action { setProperty("vid", trackId ?: "no") }
    override fun selectSubtitleTrack(trackId: String?) = action { setProperty("sid", trackId ?: "no") }
    override fun addExternalSubtitle(subtitle: ExternalSubtitle) = action { command("sub-add", subtitle.uri, "select") }
    override fun setSubtitleDelay(delayMillis: Long) = action { setProperty("sub-delay", (delayMillis / 1_000.0).toString()) }
    override fun setAudioDelay(delayMillis: Long) = action { setProperty("audio-delay", (delayMillis / 1_000.0).toString()) }

    /** Finds sibling subtitles only for file URIs the user has already opened. */
    fun detectAssociatedSubtitles(media: MediaItem): List<ExternalSubtitle> = runCatching {
        val mediaFile = File(URI(media.uri))
        val stem = mediaFile.nameWithoutExtension.lowercase()
        mediaFile.parentFile?.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.lowercase() in SUBTITLE_EXTENSIONS }
            .filter { it.nameWithoutExtension.lowercase() == stem || it.nameWithoutExtension.lowercase().startsWith("$stem.") }
            .map { file -> ExternalSubtitle(file.toURI().toString(), file.name, languageFromSubtitleName(file.name), subtitleMimeType(file.extension)) }
            .toList()
    }.getOrDefault(emptyList())
    override fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> =
        Result.failure(UnsupportedOperationException(capabilities.audioEnhancements.limitation))

    private fun ensureInitialized() {
        if (context != null) return
        val loaded = MpvNative.load()
        val created = checkNotNull(loaded.mpv_create()) { "libmpv could not create a playback context" }
        native = loaded
        context = created
        checkCode(loaded.mpv_set_option_string(created, "config", "no"))
        checkCode(loaded.mpv_set_option_string(created, "terminal", "no"))
        checkCode(loaded.mpv_set_option_string(created, "vo", "libmpv"))
        checkCode(loaded.mpv_set_option_string(created, "keep-open", "yes"))
        checkCode(loaded.mpv_initialize(created))
        videoSurface?.let { surface ->
            MpvCocoaRenderer().also { renderer = it }.attach(surface, created)
        }
    }

    private fun startPolling() {
        polling?.cancel()
        polling = scope.launch {
            while (isActive && !closed) {
                val previous = mutableState.value
                val duration = doubleProperty("duration")?.times(1_000)?.toLong()
                val position = doubleProperty("time-pos")?.times(1_000)?.toLong()?.coerceAtLeast(0) ?: 0
                val paused = flagProperty("pause") ?: true
                val idle = flagProperty("idle-active") ?: false
                mutableState.value = previous.copy(
                    status = when {
                        idle && duration != null && position >= duration - 500 -> PlaybackStatus.Completed
                        idle -> PlaybackStatus.Ready
                        paused -> PlaybackStatus.Paused
                        else -> PlaybackStatus.Playing
                    },
                    positionMillis = position,
                    bufferedPositionMillis = position,
                    durationMillis = duration,
                    speed = doubleProperty("speed")?.toFloat() ?: previous.speed,
                    volume = ((doubleProperty("volume") ?: 100.0) / 100.0).toFloat().coerceIn(0f, 1f),
                    isSeekable = flagProperty("seekable") ?: false,
                    audioTracks = tracks(TrackType.AUDIO),
                    videoTracks = tracks(TrackType.VIDEO),
                    subtitleTracks = tracks(TrackType.SUBTITLE),
                )
                delay(250)
            }
        }
    }

    private fun command(vararg arguments: String) {
        val handle = checkNotNull(context)
        val array = StringArray(arguments)
        checkCode(checkNotNull(native).mpv_command(handle, array))
    }

    private fun setProperty(name: String, value: String) =
        checkCode(checkNotNull(native).mpv_set_property_string(checkNotNull(context), name, value))

    private fun doubleProperty(name: String): Double? {
        val memory = Memory(Double.SIZE_BYTES.toLong())
        return if (checkNotNull(native).mpv_get_property(checkNotNull(context), name, MPV_FORMAT_DOUBLE, memory) >= 0) {
            memory.getDouble(0)
        } else null
    }

    private fun flagProperty(name: String): Boolean? {
        val memory = Memory(Int.SIZE_BYTES.toLong())
        return if (checkNotNull(native).mpv_get_property(checkNotNull(context), name, MPV_FORMAT_FLAG, memory) >= 0) {
            memory.getInt(0) != 0
        } else null
    }

    private fun stringProperty(name: String): String? {
        val value = checkNotNull(native).mpv_get_property_string(checkNotNull(context), name) ?: return null
        return try { value.getString(0) } finally { checkNotNull(native).mpv_free(value) }
    }

    private fun tracks(type: TrackType): List<TrackInfo> {
        val count = stringProperty("track-list/count")?.toIntOrNull() ?: return emptyList()
        val selected = stringProperty(when (type) {
            TrackType.AUDIO -> "aid"
            TrackType.VIDEO -> "vid"
            TrackType.SUBTITLE -> "sid"
        })
        return (0 until count).mapNotNull { index ->
            val prefix = "track-list/$index"
            val actualType = stringProperty("$prefix/type") ?: return@mapNotNull null
            if (actualType != type.mpvName) return@mapNotNull null
            val id = stringProperty("$prefix/id") ?: return@mapNotNull null
            val language = stringProperty("$prefix/lang")
            TrackInfo(
                id = id,
                type = type,
                label = stringProperty("$prefix/title") ?: language ?: "${type.displayName} $id",
                language = language,
                codec = stringProperty("$prefix/codec"),
                isSelected = id == selected,
                origin = if (stringProperty("$prefix/external") == "yes") TrackOrigin.EXTERNAL else TrackOrigin.EMBEDDED,
            )
        }
    }

    private fun action(block: () -> Unit): Result<Unit> = runCatching {
        check(!closed && context != null) { "Open media before using playback controls" }
        block()
    }.onFailure(::publishFailure)

    private fun checkCode(code: Int) {
        if (code < 0) error(native?.mpv_error_string(code) ?: "libmpv error $code")
    }

    private fun publishFailure(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.Error(
                PlaybackError(
                    PlaybackErrorCode.ENGINE_INITIALIZATION,
                    error.message ?: "libmpv playback failed",
                    engine = id,
                    cause = error,
                ),
            ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        polling?.cancel()
        renderer?.detach()
        renderer = null
        context?.let { native?.mpv_terminate_destroy(it) }
        context = null
        native = null
        scope.cancel()
        mutableState.value = PlaybackState(engine = id)
    }

    companion object {
        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_FORMAT_DOUBLE = 5
        private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "webvtt")
        val CAPABILITIES = EngineCapabilities(
            localAudio = true,
            localVideo = true,
            httpStreaming = true,
            hlsStreaming = true,
            dashStreaming = true,
            liveStreaming = true,
            hardwareDecoding = true,
            softwareDecoding = true,
            playbackSpeed = true,
            audioTrackSelection = true,
            videoTrackSelection = true,
            embeddedSubtitles = true,
            externalSubtitles = true,
            hdr = true,
            subtitleDelay = true,
            audioDelay = true,
            supportedSpeedRange = 0.25f..4f,
            audioEnhancements = AudioEnhancementCapabilities(
                pitchPreservation = true,
                limitation = "Use explicit libmpv profiles; Android audio-session effects do not apply on desktop",
            ),
        )
    }
}

private val TrackType.mpvName: String get() = when (this) {
    TrackType.AUDIO -> "audio"
    TrackType.VIDEO -> "video"
    TrackType.SUBTITLE -> "sub"
}

private val TrackType.displayName: String get() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun languageFromSubtitleName(fileName: String): String? =
    fileName.substringBeforeLast('.').substringAfterLast('.', "").takeIf { it.length in 2..8 }

private fun subtitleMimeType(extension: String): String = when (extension.lowercase()) {
    "srt" -> "application/x-subrip"
    "vtt", "webvtt" -> "text/vtt"
    "ass", "ssa" -> "text/x-ssa"
    else -> "application/octet-stream"
}
