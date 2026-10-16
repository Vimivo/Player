@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.desktop.playback

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.streaming.LiveLatencyMode
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.AudioOutputDevice
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.HardwareAccelerationMode
import dev.vimivo.playback.PlaybackDiagnosticsSnapshot
import dev.vimivo.playback.PlaybackError
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackMetadata
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.ReplayGainMode
import dev.vimivo.playback.SubtitleStyle
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackOrigin
import dev.vimivo.playback.TrackType
import dev.vimivo.playback.VideoAdjustments
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

/** Desktop libmpv adapter with advanced audio and video controls. */
class MpvPlayerEngine : PlayerEngine {
    override val id = EngineId.MPV
    private val desktopPlatform = DesktopPlatform.detect()
    override val capabilities = capabilitiesFor(desktopPlatform)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    private val mutableAudio = MutableStateFlow(
        AudioEnhancementState(AudioEnhancementCapabilities(limitation = "Audio processing is controlled by libmpv profiles")),
    )
    private var native: MpvNative? = null
    private var context: Pointer? = null
    private var polling: Job? = null
    private var renderer: DesktopVideoRenderer? = null
    private var videoSurface: Canvas? = null
    private var hwdecMode: HardwareAccelerationMode = HardwareAccelerationMode.AUTO
    private var attemptedSoftwareFallback = false
    private var closed = false
    private var sessionGeneration = 0L
    private var userPaused = false
    private var userStopped = false
    var liveLatencyMode: LiveLatencyMode = LiveLatencyMode.BALANCED
        private set

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    override val audioEnhancementState: StateFlow<AudioEnhancementState> = mutableAudio.asStateFlow()

    override fun supportFor(media: MediaItem): SupportLevel = when {
        media.kind == MediaKind.VIDEO && !capabilities.localVideo -> SupportLevel.UNSUPPORTED
        media.uri.substringBefore(':').lowercase() in SUPPORTED_SCHEMES -> SupportLevel.PREFERRED
        else -> SupportLevel.UNSUPPORTED
    }

    /** Must be called before opening video media; audio-only playback does not require a surface. */
    fun attachVideoSurface(canvas: Canvas): Result<Unit> = runCatching {
        check(context == null) { "Attach the video surface before initializing libmpv" }
        videoSurface = canvas
    }

    override fun setLiveLatencyMode(mode: LiveLatencyMode): Result<Unit> = runCatching {
        check(!closed) { "Player is closed" }
        liveLatencyMode = mode
        if (context != null) {
            applyLatencySettings(mode)
        }
    }

    private fun applyLatencySettings(mode: LiveLatencyMode) {
        setProperty("demuxer-readahead-secs", mode.readaheadSeconds.toString())
        setProperty("demuxer-max-bytes", mode.maxBufferBytes.toString())
    }

    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> = runCatching {
        check(!closed) { "Player is closed" }
        require(supportFor(media) != SupportLevel.UNSUPPORTED) { "Unsupported desktop media URI" }
        ensureInitialized()
        val currentSession = ++sessionGeneration
        userStopped = false
        userPaused = false
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            metadata = PlaybackMetadata(media.title, media.artist, media.album, media.artworkUri),
        )
        if (media.kind == MediaKind.STREAM) {
            applyLatencySettings(liveLatencyMode)
        }
        command("loadfile", media.uri, "replace")
        if (startPositionMillis > 0) command("seek", (startPositionMillis / 1_000.0).toString(), "absolute+exact")
        setProperty("pause", "yes")
        startPolling(currentSession)
    }.onFailure(::publishFailure)

    override fun play() = action {
        userStopped = false
        userPaused = false
        setProperty("pause", "no")
    }

    override fun pause() = runCatching {
        check(!closed) { "Player is closed" }
        userPaused = true
        if (context != null) {
            setProperty("pause", "yes")
        }
    }

    override fun stop() = runCatching {
        check(!closed) { "Player is closed" }
        userStopped = true
        userPaused = false
        sessionGeneration++
        polling?.cancel()
        if (context != null) {
            command("stop")
        }
        mutableState.value = PlaybackState(engine = id, status = PlaybackStatus.Idle)
    }

    override fun seekTo(positionMillis: Long): Result<Unit> = seekTo(positionMillis, exact = true)

    override fun seekTo(positionMillis: Long, exact: Boolean): Result<Unit> = action {
        require(positionMillis >= 0)
        val mode = if (exact) "absolute+exact" else "absolute+keyframes"
        command("seek", (positionMillis / 1_000.0).toString(), mode)
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

    override fun setReplayGainMode(mode: ReplayGainMode): Result<Unit> = action {
        val value = when (mode) {
            ReplayGainMode.OFF -> "no"
            ReplayGainMode.TRACK -> "track"
            ReplayGainMode.ALBUM -> "album"
        }
        setProperty("replaygain", value)
        setProperty("replaygain-clip", "yes")
    }

    override fun setABRepeat(range: ABRepeatRange): Result<Unit> = action {
        val a = range.pointA
        val b = range.pointB
        if (range.enabled && a != null && b != null) {
            setProperty("ab-loop-a", (a / 1_000.0).toString())
            setProperty("ab-loop-b", (b / 1_000.0).toString())
        } else {
            setProperty("ab-loop-a", "no")
            setProperty("ab-loop-b", "no")
        }
        mutableState.value = mutableState.value.copy(abRepeat = range)
    }

    override fun getChapters(): List<ChapterInfo> {
        if (context == null || closed) return emptyList()
        val count = stringProperty("chapter-list/count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).map { index ->
            val title = stringProperty("chapter-list/$index/title") ?: "Chapter ${index + 1}"
            val timeSec = doubleProperty("chapter-list/$index/time") ?: 0.0
            ChapterInfo(index = index, title = title, startPositionMillis = (timeSec * 1_000).toLong())
        }
    }

    override fun selectChapter(index: Int): Result<Unit> = action {
        setProperty("chapter", index.toString())
    }

    override fun nextChapter(): Result<Unit> = action {
        command("add", "chapter", "1")
    }

    override fun previousChapter(): Result<Unit> = action {
        command("add", "chapter", "-1")
    }

    override fun applyVideoAdjustments(adjustments: VideoAdjustments): Result<Unit> = action {
        setProperty("brightness", adjustments.brightness.toString())
        setProperty("contrast", adjustments.contrast.toString())
        setProperty("saturation", adjustments.saturation.toString())
        setProperty("gamma", adjustments.gamma.toString())
        setProperty("video-rotate", adjustments.rotationDegrees.toString())
        setProperty("video-zoom", adjustments.zoom.toString())
        setProperty("video-pan-x", adjustments.panX.toString())
        setProperty("video-pan-y", adjustments.panY.toString())
        setProperty("video-aspect-override", adjustments.aspectRatio.value)
        mutableState.value = mutableState.value.copy(videoAdjustments = adjustments)
    }

    override fun applySubtitleStyle(style: SubtitleStyle): Result<Unit> = action {
        setProperty("sub-font-size", style.fontSizeSp.toString())
        setProperty("sub-color", style.textColorHex)
        style.backgroundColorHex?.let { setProperty("sub-back-color", it) }
        setProperty("sub-pos", (100 - style.bottomMarginPercent).coerceIn(0, 100).toString())
    }

    override fun takeScreenshot(destinationFilePath: String): Result<Unit> = action {
        command("screenshot-to-file", destinationFilePath, "video")
    }

    override fun getAudioOutputDevices(): List<AudioOutputDevice> {
        if (context == null || closed) return emptyList()
        val count = stringProperty("audio-device-list/count")?.toIntOrNull() ?: return emptyList()
        val currentDevice = stringProperty("audio-device")
        return (0 until count).mapNotNull { index ->
            val name = stringProperty("audio-device-list/$index/name") ?: return@mapNotNull null
            val desc = stringProperty("audio-device-list/$index/description") ?: name
            AudioOutputDevice(id = name, name = desc, description = desc, isSelected = name == currentDevice)
        }
    }

    override fun selectAudioOutputDevice(deviceId: String): Result<Unit> = action {
        setProperty("audio-device", deviceId)
    }

    override fun setHardwareAccelerationMode(mode: HardwareAccelerationMode): Result<Unit> = action {
        hwdecMode = mode
        val hwdecValue = when (mode) {
            HardwareAccelerationMode.AUTO -> "auto-safe"
            HardwareAccelerationMode.SOFTWARE -> "no"
            HardwareAccelerationMode.HARDWARE_PREFERRED -> when (desktopPlatform) {
                DesktopPlatform.MACOS -> "videotoolbox"
                DesktopPlatform.WINDOWS -> "d3d11va"
                DesktopPlatform.LINUX -> "vaapi"
                DesktopPlatform.UNKNOWN -> "auto"
            }
        }
        setProperty("hwdec", hwdecValue)
    }

    fun attemptSoftwareDecoderFallback(): Result<Unit> = runCatching {
        check(!closed && context != null) { "Cannot fallback on closed player" }
        val media = mutableState.value.media ?: return@runCatching
        val resumePosition = mutableState.value.positionMillis
        attemptedSoftwareFallback = true
        hwdecMode = HardwareAccelerationMode.SOFTWARE
        setProperty("hwdec", "no")
        command("loadfile", media.uri, "replace")
        if (resumePosition > 0) {
            command("seek", (resumePosition / 1_000.0).toString(), "absolute+exact")
        }
        setProperty("pause", "no")
    }

    override fun getDiagnosticsSnapshot(): PlaybackDiagnosticsSnapshot? {
        if (context == null || closed) return null
        val mediaUri = mutableState.value.media?.uri ?: return null
        val vCodec = stringProperty("video-codec") ?: stringProperty("video-format")
        val aCodec = stringProperty("audio-codec") ?: stringProperty("audio-format")
        val activeHwdec = stringProperty("hwdec-current")
        val isHwdecActive = flagProperty("hwdec-active") ?: (activeHwdec != null && activeHwdec != "no")
        val activeRenderer = stringProperty("current-vo")
            ?: if (desktopPlatform == DesktopPlatform.MACOS) "libmpv/cocoa" else "gpu"
        val dropped = longProperty("drop-frame-count") ?: longProperty("vo-delayed-frame-count") ?: 0L
        val totalFrames = longProperty("estimated-frame-count") ?: 0L
        val avDriftSec = doubleProperty("avsync") ?: 0.0
        val fps = doubleProperty("container-fps")?.toFloat() ?: 0f
        val cacheBytes = longProperty("demuxer-cache-state/cache-used")
            ?: longProperty("demuxer-cache-state/bytes") ?: 0L
        val colormatrix = stringProperty("colormatrix")
        val colorTransfer = when {
            colormatrix?.contains("2084") == true || colormatrix?.contains("pq") == true -> ColorTransfer.HDR10_PQ
            colormatrix?.contains("hlg") == true || colormatrix?.contains("arib") == true -> ColorTransfer.HLG
            colormatrix?.contains("bt.709") == true || colormatrix?.contains("709") == true -> ColorTransfer.SDR_BT709
            else -> ColorTransfer.UNKNOWN
        }

        return PlaybackDiagnosticsSnapshot(
            engineId = id,
            mediaUri = mediaUri,
            activeVideoCodec = vCodec,
            activeAudioCodec = aCodec,
            activeDecoder = if (isHwdecActive) "hw:$activeHwdec" else (stringProperty("video-dec") ?: "sw:$vCodec"),
            hwdecActive = isHwdecActive,
            hwdecMode = activeHwdec ?: hwdecMode.name.lowercase(),
            activeRenderer = activeRenderer,
            droppedFrames = dropped,
            totalFrames = totalFrames,
            avDriftMillis = avDriftSec * 1000.0,
            estimatedFps = fps,
            cacheBufferBytes = cacheBytes,
            colorTransfer = colorTransfer,
            positionMillis = mutableState.value.positionMillis,
            durationMillis = mutableState.value.durationMillis,
        )
    }

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

    override fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> = action {
        val effective = DesktopAudioEffects.effectiveSettings(settings)
        setProperty("af", DesktopAudioEffects.filter(effective).orEmpty())
        mutableAudio.value = AudioEnhancementState(
            capabilities = capabilities.audioEnhancements,
            settings = effective,
            availableBandsHz = DesktopAudioEffects.frequencies,
            active = effective.enabled,
            message = if (effective.enabled) "libmpv audio filters active" else null,
        )
    }

    private fun ensureInitialized() {
        if (context != null) return
        val loaded = MpvNative.load()
        val created = checkNotNull(loaded.mpv_create()) { "libmpv could not create a playback context" }
        native = loaded
        context = created
        checkCode(loaded.mpv_set_option_string(created, "config", "no"))
        checkCode(loaded.mpv_set_option_string(created, "terminal", "no"))
        val vo = if (desktopPlatform == DesktopPlatform.MACOS) "libmpv" else "gpu"
        checkCode(loaded.mpv_set_option_string(created, "vo", vo))
        val initialHwdec = when (hwdecMode) {
            HardwareAccelerationMode.AUTO -> "auto-safe"
            HardwareAccelerationMode.SOFTWARE -> "no"
            HardwareAccelerationMode.HARDWARE_PREFERRED -> when (desktopPlatform) {
                DesktopPlatform.MACOS -> "videotoolbox"
                DesktopPlatform.WINDOWS -> "d3d11va"
                DesktopPlatform.LINUX -> "vaapi"
                DesktopPlatform.UNKNOWN -> "auto"
            }
        }
        loaded.mpv_set_option_string(created, "hwdec", initialHwdec)
        loaded.mpv_set_option_string(created, "demuxer-max-bytes", "33554432")
        loaded.mpv_set_option_string(created, "demuxer-readahead-secs", "5.0")
        loaded.mpv_set_option_string(created, "network-timeout", "15")
        loaded.mpv_set_option_string(created, "tls-verify", "yes")
        loaded.mpv_set_option_string(created, "user-agent", "Vimivo/1.0.0 (Universal Media Player)")
        checkCode(loaded.mpv_set_option_string(created, "keep-open", "yes"))
        checkCode(loaded.mpv_set_option_string(created, "gapless-audio", "yes"))
        checkCode(loaded.mpv_set_option_string(created, "audio-pitch-correction", "yes"))
        checkCode(loaded.mpv_initialize(created))
        videoSurface?.let { surface ->
            DesktopVideoRendererFactory.create(desktopPlatform).also { renderer = it }.attach(surface, created)
        }
    }

    private fun startPolling(expectedSession: Long) {
        polling?.cancel()
        polling = scope.launch {
            while (isActive && !closed && sessionGeneration == expectedSession) {
                val previous = mutableState.value
                val duration = doubleProperty("duration")?.times(1_000)?.toLong()
                val position = doubleProperty("time-pos")?.times(1_000)?.toLong()?.coerceAtLeast(0) ?: 0
                val paused = flagProperty("pause") ?: true
                val idle = flagProperty("idle-active") ?: false
                val pausedForCache = flagProperty("paused-for-cache") ?: false
                val cacheDurationMs = doubleProperty("demuxer-cache-time")?.times(1_000)?.toLong() ?: 0L
                val bufferedPosition = position + cacheDurationMs
                if (sessionGeneration != expectedSession) break
                mutableState.value = previous.copy(
                    status = when {
                        idle && duration != null && position >= duration - 500 -> PlaybackStatus.Completed
                        idle -> PlaybackStatus.Ready
                        pausedForCache -> PlaybackStatus.Buffering
                        paused || userPaused -> PlaybackStatus.Paused
                        else -> PlaybackStatus.Playing
                    },
                    positionMillis = position,
                    bufferedPositionMillis = bufferedPosition,
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

    private fun longProperty(name: String): Long? {
        val memory = Memory(Long.SIZE_BYTES.toLong())
        return if (checkNotNull(native).mpv_get_property(checkNotNull(context), name, MPV_FORMAT_INT64, memory) >= 0) {
            memory.getLong(0)
        } else null
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
                bitrate = stringProperty("$prefix/demux-bitrate")?.toIntOrNull(),
                channels = stringProperty("$prefix/demux-channel-count")?.toIntOrNull(),
                sampleRate = stringProperty("$prefix/demux-samplerate")?.toIntOrNull(),
                width = stringProperty("$prefix/demux-w")?.toIntOrNull(),
                height = stringProperty("$prefix/demux-h")?.toIntOrNull(),
                frameRate = stringProperty("$prefix/demux-fps")?.toFloatOrNull(),
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
        private const val MPV_FORMAT_INT64 = 4
        private const val MPV_FORMAT_DOUBLE = 5
        private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "webvtt")
        private val SUPPORTED_SCHEMES = setOf("file", "http", "https", "rtsp", "rtsps")
        internal fun capabilitiesFor(platform: DesktopPlatform) = EngineCapabilities(
            localAudio = true,
            localVideo = platform != DesktopPlatform.UNKNOWN,
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
            hdr = platform != DesktopPlatform.UNKNOWN,
            subtitleDelay = true,
            audioDelay = true,
            subtitleAppearance = true,
            subtitlePosition = true,
            chapters = true,
            abRepeat = true,
            videoAdjustments = true,
            screenshots = true,
            supportedSpeedRange = 0.25f..4f,
            audioEnhancements = AudioEnhancementCapabilities(
                graphicEqualizer = true,
                bassEnhancement = true,
                trebleAdjustment = true,
                attenuatingPreamp = true,
                replayGain = true,
                gapless = true,
                pitchPreservation = true,
                outputSelection = true,
                limitation = "Equalizer uses libmpv's FFmpeg filter chain; crossfade and bit-perfect output are not available",
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
