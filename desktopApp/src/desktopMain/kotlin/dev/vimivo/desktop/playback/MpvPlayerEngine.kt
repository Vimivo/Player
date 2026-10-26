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
import dev.vimivo.playback.AbRegionWatcher
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.AudioOutputDevice
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.DeinterlaceMode
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.HardwareAccelerationMode
import dev.vimivo.playback.PlaybackDiagnosticsSnapshot
import dev.vimivo.playback.PlaybackMetadata
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.ReplayGainMode
import dev.vimivo.playback.SnapshotOptions
import dev.vimivo.playback.SubtitleStyle
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.VideoAdjustments
import dev.vimivo.playback.VideoAspectRatio
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
    override val capabilities = MpvCapabilities.forPlatform(desktopPlatform)
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
    private val audioChain = MpvAudioChain { filter -> setProperty("af", filter) }

    private val preparedNext = MpvPreparedNext(
        clearPlaylist = { command("playlist-clear") },
        append = { target -> command("loadfile", target, "append") },
    )

    private val trackReader = MpvTrackReader(::stringProperty)

    /** Set when a track was selected or added, so the next poll refreshes the track lists. */
    @Volatile private var trackListChanged = false

    private val abWatcher = AbRegionWatcher(
        scope,
        positionMillis = { doubleProperty("time-pos")?.times(1_000)?.toLong() ?: 0L },
        isPaused = { flagProperty("pause") ?: true },
        seekTo = { millis -> command("seek", (millis / 1_000.0).toString(), "absolute+exact") },
    )
    private var attemptedSoftwareFallback = false
    private val rendererWatch = MpvRendererWatch()
    private var closed = false
    private var sessionGeneration = 0L
    private var userPaused = false

    /** libmpv's latest fatal player message for the current file, until the next open. */
    @Volatile private var fatalPlaybackText: String? = null

    /** Set for local files; reloads them at the end while they are still growing. */
    @Volatile private var growing: GrowingFileTracker? = null
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
        abWatcher.stop()
        preparedNext.clear()
        val previousDeinterlace = mutableState.value.deinterlaceMode
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            metadata = PlaybackMetadata(media.title, media.artist, media.album, media.artworkUri),
            volume = audioChain.requestedVolume,
            sessionId = currentSession,
        )
        // Published Preparing state lets the UI compose the video surface before the renderer needs it.
        // Only video needs the renderer: if it cannot attach, audio still plays and video reports why.
        runCatching { attachRendererIfPossible(media) }.onFailure { error -> if (media.kind == MediaKind.VIDEO) throw error }
        if (media.kind == MediaKind.STREAM) {
            applyLatencySettings(liveLatencyMode)
        }
        if (startPositionMillis > 0) {
            setProperty("start", (startPositionMillis / 1_000.0).toString())
        } else {
            setProperty("start", "none")
        }
        setProperty("sub-file-paths", MpvSubtitleFiles.foldersBeside(media).joinToString(File.pathSeparator))
        // A-B loop points and sync offsets are global mpv options; a new file must not inherit them.
        setProperty("ab-loop-a", "no")
        setProperty("ab-loop-b", "no")
        setProperty("sub-delay", "0")
        setProperty("audio-delay", "0")
        command("loadfile", mpvTarget(media.uri), "replace")
        growing = GrowingFileTracker.forMedia(media)
        fatalPlaybackText = null
        setProperty("pause", "yes")
        // The deinterlacing filter outlives the file in libmpv's filter list; the state must say so.
        mutableState.update { it.copy(deinterlace = previousDeinterlace != DeinterlaceMode.OFF, deinterlaceMode = previousDeinterlace) }
        startPolling(currentSession)
    }.onFailure(::publishFailure)

    /** See [MpvPreparedNext]: the poll loop notices libmpv moving on (`playlist-pos`) and calls [adoptPreparedNext]. */
    override fun prepareNext(media: MediaItem?): Result<Unit> = action {
        require(media == null || supportFor(media) != SupportLevel.UNSUPPORTED) { "Unsupported desktop media URI" }
        synchronized(nativeLock) { preparedNext.prepare(mutableState.value.media, media) }
    }

    /** libmpv moved to the prepared file on its own: publish it like [open] does and poll for it. */
    private fun adoptPreparedNext(expectedSession: Long): Boolean {
        val session = synchronized(nativeLock) {
            if (sessionGeneration != expectedSession || closed) return false
            ++sessionGeneration
        }
        val next = preparedNext.take() ?: return false
        runCatching { command("playlist-remove", "0") }
        abWatcher.stop()
        growing = GrowingFileTracker.forMedia(next)
        fatalPlaybackText = null
        mutableState.value = PlaybackState(
            engine = id,
            media = next,
            status = PlaybackStatus.Playing,
            metadata = PlaybackMetadata(next.title, next.artist, next.album, next.artworkUri),
            volume = audioChain.requestedVolume,
            speed = mutableState.value.speed,
            sessionId = session,
        )
        startPolling(session)
        return true
    }

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
        preparedNext.clear()
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
            // Until the next poll reads mpv's own `seeking` flag, the seek is in flight.
            mutableState.update { it.copy(isSeeking = true) }
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
        // Up to 100 % is mpv's own volume; above that the gain and a limiter go into the audio filter chain.
        setProperty("volume", (volume.coerceAtMost(1f) * 100).toString())
        audioChain.setVolume(volume)
        mutableState.update { it.copy(volume = volume) }
    }
    override fun selectAudioTrack(trackId: String?) = trackAction { setProperty("aid", trackId ?: "no") }
    override fun selectVideoTrack(trackId: String?) = trackAction { setProperty("vid", trackId ?: "no") }
    override fun selectSubtitleTrack(trackId: String?) = trackAction { setProperty("sid", trackId ?: "no") }
    override fun addExternalSubtitle(subtitle: ExternalSubtitle) = trackAction {
        command("sub-add", mpvTarget(subtitle.uri), "select")
    }

    /** A track change: the poll loop rereads the track lists at once, so the UI shows it without waiting. */
    private fun trackAction(block: () -> Unit) = action {
        block()
        trackListChanged = true
    }
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

    override fun setDeinterlace(enabled: Boolean): Result<Unit> =
        setDeinterlaceMode(if (enabled) DeinterlaceMode.AUTO else DeinterlaceMode.OFF)

    /** Replaces the labelled deinterlacing filter in libmpv's video filter chain (V1 #13). */
    override fun setDeinterlaceMode(mode: DeinterlaceMode): Result<Unit> = action {
        // Removing a filter that is not there is an error libmpv reports; nothing to undo in that case.
        runCatching { command("vf", "remove", "@$DEINTERLACE_LABEL") }
        mode.lavfiFilter?.let { command("vf", "add", "@$DEINTERLACE_LABEL:lavfi=[$it]") }
        setProperty("deinterlace", "no")
        mutableState.update { it.copy(deinterlace = mode != DeinterlaceMode.OFF, deinterlaceMode = mode) }
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
            rendering = rendererWatch.description(),
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
        abWatcher.watch(range)
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
        setProperty("hue", adjustments.hue.toString())
        setProperty("video-crop", adjustments.crop?.toMpvValue().orEmpty())
        setProperty("video-rotate", adjustments.rotationDegrees.toString())
        setProperty("video-zoom", adjustments.zoom.toString())
        setProperty("video-pan-x", adjustments.panX.toString())
        setProperty("video-pan-y", adjustments.panY.toString())
        setProperty("video-aspect-override", mpvAspectOverride(adjustments.aspectRatio))
        // Fill crops the picture to cover the window; every other preset shows the whole picture.
        setProperty("panscan", if (adjustments.aspectRatio == VideoAspectRatio.FILL) "1.0" else "0.0")
        mutableState.value = mutableState.value.copy(videoAdjustments = adjustments)
    }

    override fun applySubtitleStyle(style: SubtitleStyle): Result<Unit> = action {
        setProperty("sub-font-size", style.fontSizeSp.toString())
        setProperty("sub-color", style.textColorHex)
        style.backgroundColorHex?.let { setProperty("sub-back-color", it) }
        setProperty("sub-pos", (100 - style.bottomMarginPercent).coerceIn(0, 100).toString())
    }

    override fun takeScreenshot(destinationFilePath: String): Result<Unit> =
        takeSnapshot(destinationFilePath, SnapshotOptions())

    /** `screenshot-to-file`: "subtitles" burns the visible subtitles in, "video" is the bare picture. */
    override fun takeSnapshot(destinationFilePath: String, options: SnapshotOptions): Result<Unit> = action {
        command("screenshot-to-file", destinationFilePath, if (options.includeSubtitles) "subtitles" else "video")
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
        setProperty("hwdec", hwdecValue(mode))
    }

    /** libmpv's value of [name] right now (null when unset or unavailable); for diagnostics and live tests. */
    internal fun readProperty(name: String): String? = stringProperty(name)

    /** Frames the video renderer has presented, for tests that wait on real frames (null where not tracked). */
    internal fun presentedFrame(): PresentedFrame? = renderer?.presentedFrame()

    /** True while the window system animates a fullscreen change and would ignore another one. */
    internal fun windowTransitioning(): Boolean = renderer?.windowTransitioning() == true

    // macOS uses VideoToolbox's copy variant: zero-copy frames cannot be saved as snapshots or run through filters
    // (crop detection, deinterlacing), and on unified memory the copy costs little.
    private fun hwdecValue(mode: HardwareAccelerationMode): String = when (mode) {
        HardwareAccelerationMode.AUTO ->
            if (desktopPlatform == DesktopPlatform.MACOS) "videotoolbox-copy" else "auto-safe"
        HardwareAccelerationMode.SOFTWARE -> "no"
        HardwareAccelerationMode.HARDWARE_PREFERRED -> when (desktopPlatform) {
            DesktopPlatform.MACOS -> "videotoolbox-copy"
            DesktopPlatform.WINDOWS -> "d3d11va"
            DesktopPlatform.LINUX -> "vaapi"
            DesktopPlatform.UNKNOWN -> "auto"
        }
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
        val state = mutableState.value
        val mediaUri = state.media?.uri ?: return null
        val properties = object : MpvProperties {
            override fun string(name: String) = stringProperty(name)
            override fun flag(name: String) = flagProperty(name)
            override fun long(name: String) = longProperty(name)
            override fun double(name: String) = doubleProperty(name)
        }
        return MpvDiagnostics.snapshot(
            properties,
            DiagnosticsContext(
                engineId = id,
                mediaUri = mediaUri,
                positionMillis = state.positionMillis,
                durationMillis = state.durationMillis,
                requestedHwdec = hwdecMode.name.lowercase(),
                defaultRenderer = if (desktopPlatform == DesktopPlatform.MACOS) "libmpv/cocoa" else "gpu",
                softwareRendering = rendererWatch.softwareRenderer,
            ),
        )
    }

    override fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> = action {
        val effective = DesktopAudioEffects.effectiveSettings(settings)
        audioChain.setSettings(effective)
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
        // Opt-in diagnostics for bug reports and CI: libmpv's own log, written only where the user points it.
        System.getenv("VIMIVO_MPV_LOG_FILE")?.takeIf(String::isNotBlank)?.let { path ->
            checkCode(loaded.mpv_set_option_string(created, "log-file", path))
        }
        // libmpv never opens its own top-level window: without a Vimivo surface (headless use, tests) video is
        // decoded but not displayed. macOS and Linux render through the render API into Vimivo's own GL context
        // (the LGPL libmpv has no X11 output, ADR-004); Windows embeds libmpv's Direct3D output in the canvas.
        val vo = when {
            // No surface (tests, headless use): libmpv's render-API output would fail to initialize without a render
            // context and take video-only files down with it.
            videoSurface == null -> "null"
            desktopPlatform == DesktopPlatform.MACOS || desktopPlatform == DesktopPlatform.LINUX -> "libmpv"
            else -> "gpu"
        }
        checkCode(loaded.mpv_set_option_string(created, "vo", vo))
        if (vo == "gpu") {
            // Without GPU drivers (VMs, remote desktops, CI) only a software OpenGL renderer exists. libmpv refuses it
            // by default and shows no picture at all; a slower picture is better than none.
            checkCode(loaded.mpv_set_option_string(created, "gpu-sw", "yes"))
        }
        // VIMIVO_HWDEC (e.g. "no", "vaapi", "auto-safe") overrides the decoder for hardware test runs
        // (docs/LINUX_MANUAL_TEST_CHECKLIST.md); normal use follows the hardware acceleration mode.
        val hwdec = System.getenv("VIMIVO_HWDEC")?.takeIf { it.matches(HWDEC_OVERRIDE) } ?: hwdecValue(hwdecMode)
        loaded.mpv_set_option_string(created, "hwdec", hwdec)
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
        // Vimivo draws cover art itself. Left to libmpv, embedded art becomes a video track, and where no GPU
        // context can be created (headless Linux, broken EGL) the failed video output aborts the whole song.
        checkCode(loaded.mpv_set_option_string(created, "audio-display", "no"))
        // Vimivo owns the pointer: libmpv's embedded window must not take mouse input or hide the cursor itself,
        // so the fullscreen controller sees mouse movement over the video.
        checkCode(loaded.mpv_set_option_string(created, "input-cursor", "no"))
        checkCode(loaded.mpv_set_option_string(created, "cursor-autohide", "no"))
        // Sidecar subtitles load with the file: "movie.srt", "movie.en.srt", and the same names in Subs/ or
        // Subtitles/. Letting libmpv match at load time avoids racing sub-add against an unfinished load.
        checkCode(loaded.mpv_set_option_string(created, "sub-auto", "fuzzy"))
        // Software amplification up to 200%; the UI marks the range above 100% as boosted.
        checkCode(loaded.mpv_set_option_string(created, "volume-max", (capabilities.maxVolume * 100).toInt().toString()))
        checkCode(loaded.mpv_set_option_string(created, "audio-pitch-correction", "yes"))
        loaded.mpv_set_option_string(created, "screenshot-jpeg-quality", SNAPSHOT_JPEG_QUALITY)
        checkCode(loaded.mpv_initialize(created))
        // Log lines arrive as events; the polling loop reads them (see handleLogMessages). "v" includes the renderer
        // name libmpv reports when its video output starts.
        loaded.mpv_request_log_messages(created, "v")
    }

    private fun startPolling(expectedSession: Long) {
        synchronized(nativeLock) {
            if (sessionGeneration != expectedSession) return
            polling?.cancel()
            polling = scope.launch {
                val trackWatcher = MpvTrackListWatcher()
                var cachedAudio: List<TrackInfo> = emptyList()
                var cachedVideo: List<TrackInfo> = emptyList()
                var cachedSubtitle: List<TrackInfo> = emptyList()
                var cachedTags = PlaybackMetadata()
                var iteration = 0L
                var everLoaded = false
                val openedAt = TimeSource.Monotonic.markNow()

                while (isActive && !closed && sessionGeneration == expectedSession) {
                    iteration++
                    handleLogMessages()
                    renderer?.afterVideoOutputChange()
                    val duration = doubleProperty("duration")?.times(1_000)?.toLong()
                    val position = doubleProperty("time-pos")?.times(1_000)?.toLong()?.coerceAtLeast(0) ?: 0
                    val paused = flagProperty("pause") ?: true
                    if (preparedNext.pending != null && stringProperty("playlist-pos")?.toIntOrNull() == 1 &&
                        adoptPreparedNext(expectedSession)
                    ) {
                        return@launch // adoptPreparedNext started the poll for the next file
                    }
                    val idle = flagProperty("idle-active") ?: false
                    val pausedForCache = flagProperty("paused-for-cache") ?: false
                    val cacheDurationMs = doubleProperty("demuxer-cache-time")?.times(1_000)?.toLong() ?: 0L
                    val bufferedPosition = position + cacheDurationMs

                    val currentTrackCount = stringProperty("track-list/count")?.toIntOrNull() ?: 0
                    trackWatcher.refresh(trackReader, currentTrackCount, iteration, trackListChanged)?.let { lists ->
                        trackListChanged = false
                        cachedAudio = lists.audio
                        cachedVideo = lists.video
                        cachedSubtitle = lists.subtitle
                        cachedTags = fileTags()
                    }
                    // Tags appear a moment after the track list; retry until they do so integrations update promptly.
                    if (cachedTags == PlaybackMetadata() && currentTrackCount > 0) cachedTags = fileTags()

                    // Track lists only exist once a demuxer opened the file, so this separates "loading" from "failed".
                    if (duration != null || (!idle && currentTrackCount > 0)) everLoaded = true
                    val loadFailed = idle && !everLoaded && openedAt.elapsedNow() >= LOAD_FAILURE_GRACE
                    // Opened but nothing could be played (output or decoder failed): libmpv says so with a fatal line.
                    val playbackFatal = fatalPlaybackText?.takeIf { idle && everLoaded && position <= 0 }
                    // With keep-open=yes libmpv pauses on the last frame at end of file and never goes idle;
                    // eof-reached is its end-of-file signal. Without it the queue never advanced (Q1).
                    val eofReached = flagProperty("eof-reached") ?: false
                    // A file that is still being written (a download or recording) is reloaded at its end position
                    // and keeps playing; it completes only once it stopped growing (V1 #3).
                    val followingGrowingFile = eofReached && everLoaded && reloadGrowingFile(duration ?: position)
                    val status = when {
                        loadFailed -> PlaybackStatus.Error(MpvPlaybackErrors.loadFailure(mutableState.value.media))
                        playbackFatal != null -> PlaybackStatus.Error(MpvPlaybackErrors.playbackFailure(playbackFatal))
                        followingGrowingFile -> PlaybackStatus.Buffering
                        eofReached && everLoaded -> PlaybackStatus.Completed
                        idle && duration != null && position >= duration - 500 -> PlaybackStatus.Completed
                        idle -> PlaybackStatus.Ready
                        pausedForCache -> PlaybackStatus.Buffering
                        paused || userPaused -> PlaybackStatus.Paused
                        else -> PlaybackStatus.Playing
                    }

                    val speed = doubleProperty("speed")?.toFloat()
                    val seekable = flagProperty("seekable") ?: false
                    // True from the seek command until playback restarts (mpv's playback-restart) at the new time.
                    val seeking = flagProperty("seeking") ?: false

                    if (sessionGeneration != expectedSession) break

                    mutableState.update { current ->
                        if (sessionGeneration != expectedSession) return@update current
                        current.copy(
                            status = status,
                            positionMillis = position,
                            bufferedPositionMillis = bufferedPosition,
                            durationMillis = duration,
                            speed = speed ?: current.speed,
                            isSeekable = seekable,
                            isSeeking = seeking,
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

    /** At the end of a local file: true when it is still growing and playback must not finish (see the tracker). */
    private fun reloadGrowingFile(positionMillis: Long): Boolean {
        val media = mutableState.value.media ?: return false
        return growing?.keepFollowing {
            runCatching {
                command("loadfile", mpvTarget(media.uri), "replace", "-1", "start=${positionMillis / 1_000.0}")
                if (!userPaused) setProperty("pause", "no")
            }.isSuccess
        } ?: false
    }

    /** Reads libmpv's queued events; switches to the `fast` profile when it reports a software renderer. */
    private fun handleLogMessages() {
        val messages = synchronized(nativeLock) {
            val handle = context
            val loaded = native
            if (closed || handle == null || loaded == null) return
            MpvLogEvents.drain(loaded, handle)
        }
        messages.firstNotNullOfOrNull(MpvLogEvents::fatalPlaybackText)?.let { fatalPlaybackText = it }
        if (rendererWatch.firstSoftwareRendererReport(messages)) runCatching { command("apply-profile", "fast") }
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

    /**
     * Runs a control (property or command) against the loaded media. A failed control is returned to the caller,
     * which reports it; it never becomes a playback error, so the queue does not skip a file that still plays.
     */
    private fun action(block: () -> Unit): Result<Unit> = runCatching {
        check(!closed && context != null) { "Open media before using playback controls" }
        block()
    }

    private fun checkCode(code: Int) {
        if (code < 0) error(native?.mpv_error_string(code) ?: "libmpv error $code")
    }

    /** Title/artist/album tags of the loaded file, so files opened outside the library still show them. */
    private fun fileTags(): PlaybackMetadata = PlaybackMetadata(
        title = stringProperty("metadata/by-key/title")?.takeIf(String::isNotBlank),
        artist = stringProperty("metadata/by-key/artist")?.takeIf(String::isNotBlank),
        album = stringProperty("metadata/by-key/album")?.takeIf(String::isNotBlank),
    )

    private fun publishFailure(error: Throwable) {
        mutableState.value = mutableState.value.copy(status = PlaybackStatus.Error(MpvPlaybackErrors.fromException(error)))
    }

    override fun close() {
        if (closed) return
        closed = true
        polling?.cancel()
        synchronized(nativeLock) {
            val closing = renderer
            val loaded = native
            context?.let { handle -> loaded?.let { MpvShutdown.stopBeforeDestroy(it, handle, desktopPlatform) } }
            closing?.detach()
            renderer = null
            context?.let { native?.mpv_terminate_destroy(it) }
            closing?.afterVideoOutputChange()
            context = null
            native = null
        }
        scope.cancel()
        mutableState.value = PlaybackState(engine = id)
    }

    companion object {
        private val LOAD_FAILURE_GRACE = 1.5.seconds
        private const val SURFACE_READY_TIMEOUT_MILLIS = 5_000L
        private const val SURFACE_POLL_MILLIS = 16L
        private const val DEINTERLACE_LABEL = "vimivo-deint"
        private const val SNAPSHOT_JPEG_QUALITY = "92"
        private val HWDEC_OVERRIDE = Regex("[a-z0-9,-]{1,64}")
        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_FORMAT_INT64 = 4
        private const val MPV_FORMAT_DOUBLE = 5
        private val SUPPORTED_SCHEMES = setOf("file", "http", "https", "rtsp", "rtsps")

        /** libmpv `video-aspect-override` values; Fill keeps the source aspect and crops through `panscan`. */
        internal fun mpvAspectOverride(ratio: VideoAspectRatio): String = when (ratio) {
            VideoAspectRatio.ORIGINAL, VideoAspectRatio.FILL -> "-1"
            VideoAspectRatio.FIT -> "0"
            VideoAspectRatio.RATIO_16_9 -> "16:9"
            VideoAspectRatio.RATIO_4_3 -> "4:3"
            VideoAspectRatio.RATIO_21_9 -> "2.35:1"
        }

        /**
         * libmpv rejects single-slash `file:/path` URIs (what `File.toURI()` produces), so local media is always
         * handed over as a filesystem path; network URLs pass through unchanged.
         */
        internal fun mpvTarget(uri: String): String {
            if (!uri.startsWith("file:", ignoreCase = true)) return uri
            return runCatching { Paths.get(URI(uri)).toString() }
                .getOrElse { error -> throw IllegalArgumentException("Invalid local file URI", error) }
        }
    }
}

/** Container-level properties read through the libmpv property API. */
data class DesktopContainerInfo(
    val format: String?,
    val fileSizeBytes: Long?,
    val videoParameters: String?,
    val audioParameters: String?,
    val metadata: List<Pair<String, String>>,
    /** The video renderer, e.g. "Software rendering — performance mode on (llvmpipe …)". */
    val rendering: String? = null,
)

/** Embedded tags win over values derived from the file name; missing tags keep what was there. */
internal fun PlaybackMetadata.withTags(tags: PlaybackMetadata) = copy(
    title = tags.title ?: title,
    artist = tags.artist ?: artist,
    album = tags.album ?: album,
)
