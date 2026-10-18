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
import dev.vimivo.playback.PlaybackSpeeds
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Canvas
import java.io.File
import java.net.URI
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
    private val nativeLock = Any()
    private val nativeBuffer = Memory(8L)

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
        val currentSession = synchronized(nativeLock) {
            polling?.cancel()
            ++sessionGeneration
        }
        userStopped = false
        userPaused = false
        val previousDeinterlace = mutableState.value.deinterlace
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            metadata = PlaybackMetadata(media.title, media.artist, media.album, media.artworkUri),
            sessionId = currentSession,
        )
        // Published Preparing state lets the UI compose the video surface before the renderer needs it.
        attachRendererIfPossible(media)
        if (media.kind == MediaKind.STREAM) {
            applyLatencySettings(liveLatencyMode)
        }
        if (startPositionMillis > 0) {
            setProperty("start", (startPositionMillis / 1_000.0).toString())
        } else {
            setProperty("start", "none")
        }
        setProperty("sub-file-paths", subtitleFolders(media).joinToString(File.pathSeparator))
        // A-B loop points and sync offsets are global mpv options; a new file must not inherit them.
        setProperty("ab-loop-a", "no")
        setProperty("ab-loop-b", "no")
        setProperty("sub-delay", "0")
        setProperty("audio-delay", "0")
        command("loadfile", mpvTarget(media.uri), "replace")
        setProperty("pause", "yes")
        setProperty("deinterlace", if (previousDeinterlace) "yes" else "no")
        mutableState.update { it.copy(deinterlace = previousDeinterlace) }
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
        val seekable = flagProperty("seekable") ?: false
        if (seekable) {
            val mode = if (exact) "absolute+exact" else "absolute+keyframes"
            command("seek", (positionMillis / 1_000.0).toString(), mode)
        } else {
            setProperty("start", (positionMillis / 1_000.0).toString())
        }
    }
    override fun setPlaybackSpeed(speed: Float) = action {
        require(speed in capabilities.supportedSpeedRange)
        setProperty("speed", speed.toString())
    }
    override fun setVolume(volume: Float) = action {
        require(volume in 0f..capabilities.maxVolume) { "Volume must be between 0 and ${capabilities.maxVolume}" }
        setProperty("volume", (volume * 100).toString())
    }
    override fun selectAudioTrack(trackId: String?) = action { setProperty("aid", trackId ?: "no") }
    override fun selectVideoTrack(trackId: String?) = action { setProperty("vid", trackId ?: "no") }
    override fun selectSubtitleTrack(trackId: String?) = action { setProperty("sid", trackId ?: "no") }
    override fun addExternalSubtitle(subtitle: ExternalSubtitle) = action { command("sub-add", mpvTarget(subtitle.uri), "select") }
    override fun setSubtitleDelay(delayMillis: Long) = action {
        setProperty("sub-delay", (delayMillis / 1_000.0).toString())
        mutableState.update { it.copy(subtitleDelayMillis = delayMillis) }
    }
    override fun setAudioDelay(delayMillis: Long) = action {
        setProperty("audio-delay", (delayMillis / 1_000.0).toString())
        mutableState.update { it.copy(audioDelayMillis = delayMillis) }
    }

    /** Uses mpv's frame-step commands, which pause playback after displaying the new frame. */
    override fun frameStep(forward: Boolean): Result<Unit> = action {
        userPaused = true
        command(if (forward) "frame-step" else "frame-back-step")
    }

    override fun setDeinterlace(enabled: Boolean): Result<Unit> = action {
        setProperty("deinterlace", if (enabled) "yes" else "no")
        mutableState.update { it.copy(deinterlace = enabled) }
    }

    /** Container-level details for the media information dialog; null when nothing is loaded. */
    fun containerInfo(): DesktopContainerInfo? = synchronized(nativeLock) {
        if (context == null || closed || mutableState.value.media == null) return null
        val metadataCount = stringProperty("metadata/list/count")?.toIntOrNull() ?: 0
        val metadata = (0 until metadataCount).mapNotNull { index ->
            val key = stringProperty("metadata/list/$index/key") ?: return@mapNotNull null
            val value = stringProperty("metadata/list/$index/value") ?: return@mapNotNull null
            key to value
        }
        DesktopContainerInfo(
            format = stringProperty("file-format"),
            fileSizeBytes = longProperty("file-size"),
            videoParameters = listOfNotNull(
                stringProperty("video-params/pixelformat"),
                stringProperty("video-params/colormatrix"),
                stringProperty("video-params/primaries"),
                stringProperty("video-params/gamma"),
            ).joinToString(" · ").ifBlank { null },
            audioParameters = listOfNotNull(
                stringProperty("audio-params/format"),
                stringProperty("audio-params/hr-channels") ?: stringProperty("audio-params/channels"),
                stringProperty("audio-params/samplerate")?.let { "$it Hz" },
            ).joinToString(" · ").ifBlank { null },
            metadata = metadata,
        )
    }
    override fun setAudioBalance(balance: Float) = action {
        require(balance in -1f..1f) { "Stereo balance must be between -1.0 and 1.0" }
        setProperty("balance", balance.toString())
    }

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

    override fun getAudioOutputDevices(): List<AudioOutputDevice> = synchronized(nativeLock) {
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
        command("loadfile", mpvTarget(media.uri), "replace")
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

    override fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> = action {
        val effective = DesktopAudioEffects.effectiveSettings(settings)
        setProperty("af", DesktopAudioEffects.filter(effective).orEmpty())
        val active = effective.enabled || effective.volumeNormalization
        setProperty("balance", effective.balance.toString())
        mutableAudio.value = AudioEnhancementState(
            capabilities = capabilities.audioEnhancements,
            settings = effective,
            availableBandsHz = DesktopAudioEffects.frequencies,
            active = active,
            message = if (active) "libmpv audio filters active" else null,
        )
    }

    /**
     * Native renderers read the canvas peer, which only exists once the canvas is in a showing window, so the
     * renderer is attached lazily: video waits briefly for the surface, audio and streams never block on it.
     * A failed attach leaves the libmpv context usable and is retried on the next open.
     */
    private suspend fun attachRendererIfPossible(media: MediaItem) {
        val surface = videoSurface
        val created = context
        if (renderer != null || surface == null || created == null) return
        if (!surface.isDisplayable && media.kind == MediaKind.VIDEO) {
            val ready = withTimeoutOrNull(SURFACE_READY_TIMEOUT_MILLIS) {
                while (!surface.isDisplayable) delay(SURFACE_POLL_MILLIS)
            }
            checkNotNull(ready) { "The video window was not ready in time; try opening the file again" }
        }
        if (!surface.isDisplayable) return
        val videoRenderer = DesktopVideoRendererFactory.create(desktopPlatform)
        synchronized(nativeLock) { videoRenderer.attach(surface, created) }
        renderer = videoRenderer
    }

    private fun ensureInitialized() = synchronized(nativeLock) {
        if (context != null) return@synchronized
        val loaded = MpvNative.load()
        MpvLocale.ensureCNumeric(desktopPlatform)
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
        // Without any working audio output (no device, device unplugged, headless machine) keep playing
        // the picture instead of stalling the whole player.
        checkCode(loaded.mpv_set_option_string(created, "audio-fallback-to-null", "yes"))
        // Sidecar subtitles load with the file: "movie.srt", "movie.en.srt", and the same names in Subs/ or
        // Subtitles/. Letting libmpv match at load time avoids racing sub-add against an unfinished load.
        checkCode(loaded.mpv_set_option_string(created, "sub-auto", "fuzzy"))
        // Software amplification up to 200%; the UI marks the range above 100% as boosted.
        checkCode(loaded.mpv_set_option_string(created, "volume-max", (capabilities.maxVolume * 100).toInt().toString()))
        checkCode(loaded.mpv_set_option_string(created, "audio-pitch-correction", "yes"))
        checkCode(loaded.mpv_initialize(created))
    }

    private fun startPolling(expectedSession: Long) {
        synchronized(nativeLock) {
            if (sessionGeneration != expectedSession) return
            polling?.cancel()
            polling = scope.launch {
                var lastTrackCount = -1
                var cachedAudio: List<TrackInfo> = emptyList()
                var cachedVideo: List<TrackInfo> = emptyList()
                var cachedSubtitle: List<TrackInfo> = emptyList()
                var cachedTags = PlaybackMetadata()
                var iteration = 0L
                var everLoaded = false
                val openedAt = TimeSource.Monotonic.markNow()

                while (isActive && !closed && sessionGeneration == expectedSession) {
                    iteration++
                    val duration = doubleProperty("duration")?.times(1_000)?.toLong()
                    val position = doubleProperty("time-pos")?.times(1_000)?.toLong()?.coerceAtLeast(0) ?: 0
                    val paused = flagProperty("pause") ?: true
                    val idle = flagProperty("idle-active") ?: false
                    val pausedForCache = flagProperty("paused-for-cache") ?: false
                    val cacheDurationMs = doubleProperty("demuxer-cache-time")?.times(1_000)?.toLong() ?: 0L
                    val bufferedPosition = position + cacheDurationMs

                    val currentTrackCount = stringProperty("track-list/count")?.toIntOrNull() ?: 0
                    if (currentTrackCount != lastTrackCount || iteration % 20L == 1L) {
                        lastTrackCount = currentTrackCount
                        cachedAudio = tracks(TrackType.AUDIO)
                        cachedVideo = tracks(TrackType.VIDEO)
                        cachedSubtitle = tracks(TrackType.SUBTITLE)
                        cachedTags = fileTags()
                    }
                    // Tags appear a moment after the track list; retry until they do so integrations update promptly.
                    if (cachedTags == PlaybackMetadata() && currentTrackCount > 0) cachedTags = fileTags()

                    // Track lists only exist once a demuxer opened the file, so this separates "loading" from "failed".
                    if (duration != null || (!idle && currentTrackCount > 0)) everLoaded = true
                    val loadFailed = idle && !everLoaded && openedAt.elapsedNow() >= LOAD_FAILURE_GRACE
                    // With keep-open=yes libmpv pauses on the last frame at end of file and never goes idle;
                    // eof-reached is its end-of-file signal. Without it the queue never advanced (Q1).
                    val eofReached = flagProperty("eof-reached") ?: false
                    val status = when {
                        loadFailed -> PlaybackStatus.Error(loadFailure(mutableState.value.media))
                        eofReached && everLoaded -> PlaybackStatus.Completed
                        idle && duration != null && position >= duration - 500 -> PlaybackStatus.Completed
                        idle -> PlaybackStatus.Ready
                        pausedForCache -> PlaybackStatus.Buffering
                        paused || userPaused -> PlaybackStatus.Paused
                        else -> PlaybackStatus.Playing
                    }

                    val speed = doubleProperty("speed")?.toFloat()
                    val volume = doubleProperty("volume")?.let { (it / 100.0).toFloat().coerceIn(0f, capabilities.maxVolume) }
                    val seekable = flagProperty("seekable") ?: false

                    if (sessionGeneration != expectedSession) break

                    mutableState.update { current ->
                        if (sessionGeneration != expectedSession) return@update current
                        current.copy(
                            status = status,
                            positionMillis = position,
                            bufferedPositionMillis = bufferedPosition,
                            durationMillis = duration,
                            speed = speed ?: current.speed,
                            volume = volume ?: current.volume,
                            isSeekable = seekable,
                            audioTracks = cachedAudio,
                            videoTracks = cachedVideo,
                            subtitleTracks = cachedSubtitle,
                            metadata = current.metadata.withTags(cachedTags),
                            sessionId = expectedSession,
                        )
                    }
                    val pollDelay = when (status) {
                        PlaybackStatus.Completed, is PlaybackStatus.Error -> 1000L
                        PlaybackStatus.Paused, PlaybackStatus.Ready -> 500L
                        else -> 250L
                    }
                    delay(pollDelay)
                }
            }
        }
    }

    private fun command(vararg arguments: String) {
        synchronized(nativeLock) {
            if (closed || context == null) return
            val handle = checkNotNull(context)
            val array = StringArray(arguments)
            checkCode(checkNotNull(native).mpv_command(handle, array))
        }
    }

    private fun setProperty(name: String, value: String) {
        synchronized(nativeLock) {
            if (closed || context == null) return
            checkCode(checkNotNull(native).mpv_set_property_string(checkNotNull(context), name, value))
        }
    }

    private fun doubleProperty(name: String): Double? = synchronized(nativeLock) {
        if (closed || context == null) return null
        return if (checkNotNull(native).mpv_get_property(checkNotNull(context), name, MPV_FORMAT_DOUBLE, nativeBuffer) >= 0) {
            nativeBuffer.getDouble(0)
        } else null
    }

    private fun flagProperty(name: String): Boolean? = synchronized(nativeLock) {
        if (closed || context == null) return null
        return if (checkNotNull(native).mpv_get_property(checkNotNull(context), name, MPV_FORMAT_FLAG, nativeBuffer) >= 0) {
            nativeBuffer.getInt(0) != 0
        } else null
    }

    private fun stringProperty(name: String): String? = synchronized(nativeLock) {
        if (closed || context == null) return null
        val value = checkNotNull(native).mpv_get_property_string(checkNotNull(context), name) ?: return null
        return try { value.getString(0) } finally { checkNotNull(native).mpv_free(value) }
    }

    private fun longProperty(name: String): Long? = synchronized(nativeLock) {
        if (closed || context == null) return null
        return if (checkNotNull(native).mpv_get_property(checkNotNull(context), name, MPV_FORMAT_INT64, nativeBuffer) >= 0) {
            nativeBuffer.getLong(0)
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

    /** Title/artist/album tags of the loaded file, so files opened outside the library still show them. */
    private fun fileTags(): PlaybackMetadata = PlaybackMetadata(
        title = stringProperty("metadata/by-key/title")?.takeIf(String::isNotBlank),
        artist = stringProperty("metadata/by-key/artist")?.takeIf(String::isNotBlank),
        album = stringProperty("metadata/by-key/album")?.takeIf(String::isNotBlank),
    )

    private fun loadFailure(media: MediaItem?): PlaybackError {
        val localFile = media?.uri?.takeIf { it.startsWith("file:") }?.let { runCatching { File(URI(it)) }.getOrNull() }
        val code = when {
            localFile != null && !localFile.exists() -> PlaybackErrorCode.SOURCE_NOT_FOUND
            localFile != null && !localFile.canRead() -> PlaybackErrorCode.ACCESS_DENIED
            localFile == null -> PlaybackErrorCode.NETWORK
            else -> PlaybackErrorCode.UNSUPPORTED_CONTAINER
        }
        val message = when (code) {
            PlaybackErrorCode.SOURCE_NOT_FOUND -> "The file no longer exists"
            PlaybackErrorCode.ACCESS_DENIED -> "Vimivo does not have permission to read this file"
            PlaybackErrorCode.NETWORK -> "The stream could not be opened"
            else -> "This file could not be opened; it may be damaged or in an unsupported format"
        }
        return PlaybackError(code, message, engine = id)
    }

    private fun publishFailure(error: Throwable) {
        val message = error.message.orEmpty()
        val lower = message.lowercase()
        val code = when {
            error is java.io.FileNotFoundException || "not found" in lower || "no such file" in lower -> PlaybackErrorCode.SOURCE_NOT_FOUND
            error is java.lang.SecurityException || "permission denied" in lower || "access denied" in lower -> PlaybackErrorCode.ACCESS_DENIED
            error is java.net.SocketException || error is java.net.UnknownHostException || "timeout" in lower || "network" in lower || "connection" in lower -> PlaybackErrorCode.NETWORK
            "unsupported" in lower || "codec" in lower -> PlaybackErrorCode.UNSUPPORTED_CODEC
            "demux" in lower || "container" in lower || "corrupt" in lower -> PlaybackErrorCode.UNSUPPORTED_CONTAINER
            "decode" in lower || "decoder" in lower -> PlaybackErrorCode.DECODING_FAILED
            else -> PlaybackErrorCode.ENGINE_INITIALIZATION
        }
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.Error(
                PlaybackError(
                    code,
                    if (message.isNotBlank()) message else "libmpv playback failed",
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
        synchronized(nativeLock) {
            renderer?.detach()
            renderer = null
            context?.let { native?.mpv_terminate_destroy(it) }
            context = null
            native = null
        }
        scope.cancel()
        mutableState.value = PlaybackState(engine = id)
    }

    companion object {
        private val LOAD_FAILURE_GRACE = 1.5.seconds
        private const val MAX_VOLUME = 2f
        private const val SURFACE_READY_TIMEOUT_MILLIS = 5_000L
        private const val SURFACE_POLL_MILLIS = 16L
        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_FORMAT_INT64 = 4
        private const val MPV_FORMAT_DOUBLE = 5
        val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "webvtt", "sub")
        private val SUBTITLE_FOLDERS = listOf("Subs", "Subtitles", "subs", "subtitles")

        /**
         * Subtitle folders that exist beside a local file, once each: on case-insensitive filesystems
         * (macOS, Windows) "Subs" and "subs" are the same folder and must not load its files twice.
         */
        internal fun subtitleFolders(media: MediaItem): List<String> {
            val directory = runCatching { File(URI(media.uri)).parentFile }.getOrNull() ?: return emptyList()
            return SUBTITLE_FOLDERS
                .map { File(directory, it) }
                .filter(File::isDirectory)
                .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path).lowercase() }
                .map(File::getName)
        }

        /** A subtitle file the user dropped or chose; null for anything that is not a subtitle. */
        fun externalSubtitleFor(file: File): ExternalSubtitle? =
            file.takeIf { it.extension.lowercase() in SUBTITLE_EXTENSIONS }?.let {
                ExternalSubtitle(it.toPath().toUri().toString(), it.name, languageFromSubtitleName(it.name), subtitleMimeType(it.extension))
            }
        private val SUPPORTED_SCHEMES = setOf("file", "http", "https", "rtsp", "rtsps")

        /**
         * libmpv rejects single-slash `file:/path` URIs (what `File.toURI()` produces), so local media is always
         * handed over as a filesystem path; network URLs pass through unchanged.
         */
        internal fun mpvTarget(uri: String): String {
            if (!uri.startsWith("file:", ignoreCase = true)) return uri
            return runCatching { Paths.get(URI(uri)).toString() }
                .getOrElse { error -> throw IllegalArgumentException("Invalid local file URI", error) }
        }
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
            frameStepping = platform != DesktopPlatform.UNKNOWN,
            deinterlacing = platform != DesktopPlatform.UNKNOWN,
            supportedSpeedRange = PlaybackSpeeds.RANGE,
            maxVolume = MAX_VOLUME,
            audioEnhancements = AudioEnhancementCapabilities(
                graphicEqualizer = true,
                bassEnhancement = true,
                trebleAdjustment = true,
                attenuatingPreamp = true,
                replayGain = true,
                gapless = true,
                stereoBalance = true,
                pitchPreservation = true,
                outputSelection = true,
                normalization = true,
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

/** Container-level properties read through the libmpv property API. */
data class DesktopContainerInfo(
    val format: String?,
    val fileSizeBytes: Long?,
    val videoParameters: String?,
    val audioParameters: String?,
    val metadata: List<Pair<String, String>>,
)

/** Embedded tags win over values derived from the file name; missing tags keep what was there. */
internal fun PlaybackMetadata.withTags(tags: PlaybackMetadata) = copy(
    title = tags.title ?: title,
    artist = tags.artist ?: artist,
    album = tags.album ?: album,
)
