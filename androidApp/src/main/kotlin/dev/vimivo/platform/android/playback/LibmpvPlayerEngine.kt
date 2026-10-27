@file:Suppress("TooManyFunctions") // One override per PlayerEngine operation.

package dev.vimivo.platform.android.playback

import android.content.Context
import android.view.SurfaceHolder
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AbRegionWatcher
import dev.vimivo.playback.AudioEnhancementCapabilities
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
import dev.vimivo.playback.PlayerEngineFactory
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackType
import dev.vimivo.settings.DecoderPreference
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Android fallback engine on Vimivo's own LGPL libmpv (ADR-005), replacing LibVLC 1:1: it plays what Media3 cannot.
 * Video renders through libmpv's Android GPU context into the session's Surface, decoding with MediaCodec where the
 * user allows it; audio goes to AAudio (AudioTrack fallback). One event thread turns libmpv events into [state].
 */
class LibmpvPlayerEngine(
    context: Context,
    private val settingsRepository: SettingsRepository,
    /** libmpv's own log, for diagnostics and tests only; never written unless a file is given. */
    logFile: java.io.File? = null,
) : PlayerEngine {
    override val id = EngineId.MPV
    override val capabilities = CAPABILITIES
    override val audioEnhancementState = MutableStateFlow(AudioEnhancementState(capabilities.audioEnhancements))
    private val appContext = context.applicationContext
    private val sources = LibmpvSources(appContext.contentResolver)
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val lock = Any()
    private val audioFocus = MediaAudioFocusController(
        context = appContext,
        onPause = ::pauseForFocusLoss,
        onResume = { runCatching { setProperty("pause", "no") } },
        onDuck = ::setDucked,
    )

    @Volatile private var handle: Long = 0

    @Volatile private var released = false

    @Volatile private var fileLoaded = false

    /** Bumped by every open(); a state sample taken before it is stale. */
    @Volatile private var generation = 0
    private var surfaceReference = 0L
    private var surfaceHolder: SurfaceHolder? = null
    private var volumeBeforeDuck = MAX_VOLUME
    private val eventThread: Thread

    init {
        check(LibmpvNative.available) { "libmpv is not available on this device" }
        val created = LibmpvNative.create()
        check(created != 0L) { "libmpv could not create a playback context" }
        BASE_OPTIONS.forEach { (name, value) -> LibmpvNative.setOption(created, name, value) }
        logFile?.let { LibmpvNative.setOption(created, "log-file", it.absolutePath) }
        check(LibmpvNative.initialize(created) >= 0) {
            LibmpvNative.destroy(created)
            "libmpv could not be initialized"
        }
        handle = created
        eventThread = Thread(::eventLoop, "vimivo-libmpv-events").apply {
            isDaemon = true
            start()
        }
    }

    override fun supportFor(media: MediaItem): SupportLevel = support(media)

    private val abScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val abWatcher = AbRegionWatcher(
        abScope,
        positionMillis = {
            LibmpvNative.getProperty(handle, "time-pos")?.toDoubleOrNull()?.times(MILLIS)?.toLong() ?: 0L
        },
        isPaused = { released || LibmpvNative.getProperty(handle, "pause") != "no" },
        seekTo = { millis -> if (!released) command("seek", seconds(millis), "absolute+exact") },
    )

    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> = runCatching {
        checkOpen()
        require(supportFor(media) != SupportLevel.UNSUPPORTED) { "libmpv does not support this URI" }
        val target = sources.resolve(media.uri)
        generation++
        fileLoaded = false
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            positionMillis = startPositionMillis,
            metadata = PlaybackMetadata(media.title, media.artist, media.album, media.artworkUri),
        )
        val mode = dev.vimivo.playback.InMemoryDecoderPreferenceStore.shared.getDecoderForMedia(media.uri)
        val hwdecValue = when (mode) {
            dev.vimivo.playback.MobileDecoderMode.SW -> "no"
            dev.vimivo.playback.MobileDecoderMode.HW_PLUS, dev.vimivo.playback.MobileDecoderMode.HW -> "mediacodec-copy"
            null -> {
                val decoder = settingsRepository.settings.first().decoderPreference
                hwdecFor(decoder)
            }
        }
        setProperty("hwdec", hwdecValue)
        setProperty("pause", "yes")
        setProperty("start", if (startPositionMillis > 0) seconds(startPositionMillis) else "none")
        command("loadfile", target, "replace")
    }

    /** libmpv's own A–B loop is not used (see [AbRegionWatcher]); the watcher seeks exactly at B. */
    override fun setABRepeat(range: ABRepeatRange): Result<Unit> = runCatching {
        checkOpen()
        abWatcher.watch(range)
        mutableState.update { it.copy(abRepeat = range) }
    }

    override fun play(): Result<Unit> = runCatching {
        checkOpen()
        check(audioFocus.request()) { "Audio focus was denied" }
        setProperty("pause", "no")
    }

    override fun pause(): Result<Unit> = runCatching { setProperty("pause", "yes") }

    override fun stop(): Result<Unit> = runCatching {
        command("stop")
        audioFocus.abandon()
    }

    override fun seekTo(positionMillis: Long): Result<Unit> = seekTo(positionMillis, exact = true)

    override fun seekTo(positionMillis: Long, exact: Boolean): Result<Unit> = runCatching {
        require(positionMillis >= 0) { "Position cannot be negative" }
        check(mutableState.value.isSeekable) { "This media source is not seekable" }
        command("seek", seconds(positionMillis), if (exact) "absolute+exact" else "absolute+keyframes")
    }

    /** libmpv's own frame stepping; it leaves playback paused. */
    override fun frameStep(forward: Boolean): Result<Unit> = runCatching {
        checkOpen()
        command(if (forward) "frame-step" else "frame-back-step")
    }

    /** The frame on screen as a bitmap at the video's own size; fails when nothing is being shown. */
    suspend fun captureFrame(): Result<android.graphics.Bitmap> {
        val surface = synchronized(lock) { surfaceHolder?.surface }
            ?: return Result.failure(IllegalStateException("There is no video on screen"))
        val picture = mutableState.value.videoTracks.firstOrNull { it.isSelected }
        return SurfaceSnapshot.capture(surface, picture?.width ?: 0, picture?.height ?: 0)
    }

    override fun setPlaybackSpeed(speed: Float): Result<Unit> = runCatching {
        require(speed in capabilities.supportedSpeedRange) { "Playback speed is outside the supported range" }
        setProperty("speed", speed.toString())
    }

    override fun setVolume(volume: Float): Result<Unit> = runCatching {
        require(volume in 0f..1f) { "Volume must be between 0 and 1" }
        setProperty("volume", (volume * MAX_VOLUME).toString())
    }

    override fun selectAudioTrack(trackId: String?): Result<Unit> = selectTrack("aid", trackId)
    override fun selectVideoTrack(trackId: String?): Result<Unit> = selectTrack("vid", trackId)
    override fun selectSubtitleTrack(trackId: String?): Result<Unit> = selectTrack("sid", trackId)

    override fun addExternalSubtitle(subtitle: ExternalSubtitle): Result<Unit> = runCatching {
        command("sub-add", sources.resolve(subtitle.uri), "select")
        publish()
    }

    override fun setSubtitleDelay(delayMillis: Long): Result<Unit> = runCatching {
        setProperty("sub-delay", seconds(delayMillis))
        mutableState.update { it.copy(subtitleDelayMillis = delayMillis) }
    }

    override fun setAudioDelay(delayMillis: Long): Result<Unit> = runCatching {
        setProperty("audio-delay", seconds(delayMillis))
        mutableState.update { it.copy(audioDelayMillis = delayMillis) }
    }

    /** Renders into [holder]'s Surface from now on; follows its size and lets go of it when it is destroyed. */
    fun setVideoSurfaceHolder(holder: SurfaceHolder) = synchronized(lock) {
        checkOpen()
        if (surfaceHolder === holder && surfaceReference != 0L) return
        clearVideoSurface()
        surfaceHolder = holder
        holder.addCallback(surfaceCallback)
        if (holder.surface?.isValid == true) attachSurface(holder)
    }

    fun clearVideoSurface() = synchronized(lock) {
        surfaceHolder?.removeCallback(surfaceCallback)
        surfaceHolder = null
        detachSurface()
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = synchronized(lock) { attachSurface(holder) }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            runCatching { setProperty("android-surface-size", "${width}x$height") }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) = synchronized(lock) { detachSurface() }
    }

    private fun attachSurface(holder: SurfaceHolder) {
        if (released || surfaceReference != 0L) return
        val frame = holder.surfaceFrame
        LibmpvNative.setProperty(handle, "android-surface-size", "${frame.width()}x${frame.height()}")
        surfaceReference = LibmpvNative.attachSurface(handle, holder.surface)
        if (surfaceReference != 0L) LibmpvNative.setProperty(handle, "vo", "gpu")
    }

    /** libmpv stops its video output before `wid` and the Surface reference go away. */
    private fun detachSurface() {
        if (released || surfaceReference == 0L) return
        LibmpvNative.setProperty(handle, "vo", "null")
        LibmpvNative.releaseSurface(handle, surfaceReference)
        surfaceReference = 0L
    }

    private fun selectTrack(property: String, trackId: String?): Result<Unit> = runCatching {
        setProperty(property, trackId ?: "no")
        publish()
    }

    private fun eventLoop() {
        while (!released) {
            val event = LibmpvNative.waitEvent(handle, EVENT_WAIT_SECONDS)
            when (event[0]) {
                EVENT_SHUTDOWN -> return
                EVENT_START_FILE -> {
                    fileLoaded = false
                    updateStatus(PlaybackStatus.Preparing)
                }
                EVENT_FILE_LOADED -> {
                    fileLoaded = true
                    LibmpvNative.setProperty(handle, "start", "none")
                }
                EVENT_END_FILE -> onEndFile(reason = event[2], error = event[3])
            }
            if (!released) publish()
        }
    }

    private fun onEndFile(reason: Int, error: Int) {
        when (reason) {
            END_FILE_EOF -> {
                audioFocus.abandon()
                updateStatus(PlaybackStatus.Completed)
            }
            END_FILE_ERROR -> {
                val stream = mutableState.value.media?.kind == MediaKind.STREAM
                updateStatus(
                    PlaybackStatus.Error(
                        PlaybackError(
                            code = if (stream) PlaybackErrorCode.NETWORK else PlaybackErrorCode.DECODING_FAILED,
                            message = "libmpv could not play this media: ${LibmpvNative.errorString(error)}",
                            engine = id,
                        ),
                    ),
                )
            }
        }
    }

    private fun updateStatus(status: PlaybackStatus) {
        mutableState.update { it.copy(status = status) }
    }

    private class Timing(val positionMillis: Long?, val bufferedMillis: Long?, val durationMillis: Long?)

    private class Flags(val seekable: Boolean, val pausedForCache: Boolean, val paused: Boolean)

    private class TrackLists(val audio: List<TrackInfo>, val video: List<TrackInfo>, val subtitles: List<TrackInfo>)

    /** What libmpv reports right now, read without touching [state] so the write below is one atomic update. */
    private class Sample(
        val generation: Int,
        val timing: Timing,
        val flags: Flags,
        val tracks: TrackLists,
        val speed: Float?,
        val volume: Float?,
    )

    private fun sample() = Sample(
        generation = generation,
        timing = Timing(millis("time-pos"), millis("demuxer-cache-time"), millis("duration")),
        flags = Flags(
            seekable = property("seekable") == "yes",
            pausedForCache = property("paused-for-cache") == "yes",
            paused = property("pause") == "yes",
        ),
        tracks = TrackLists(tracks(TrackType.AUDIO), tracks(TrackType.VIDEO), tracks(TrackType.SUBTITLE)),
        speed = property("speed")?.toFloatOrNull(),
        volume = property("volume")?.toFloatOrNull()?.div(MAX_VOLUME)?.coerceIn(0f, 1f),
    )

    /**
     * Folds libmpv's current values into [state] after every event and timeout (four times a second at most). Slow
     * (dozens of JNI calls) and run on the event thread while other threads write state, so it samples first and then
     * applies the sample in one atomic update, dropping a sample that predates the latest open().
     */
    private fun publish() {
        if (released || mutableState.value.media == null) return
        val sample = sample()
        mutableState.update { current ->
            if (sample.generation != generation) return@update current
            current.copy(
                status = statusFor(current, sample),
                positionMillis = sample.timing.positionMillis?.coerceAtLeast(0) ?: current.positionMillis,
                bufferedPositionMillis = sample.timing.bufferedMillis ?: current.bufferedPositionMillis,
                durationMillis = sample.timing.durationMillis ?: current.durationMillis,
                speed = sample.speed ?: current.speed,
                volume = sample.volume ?: current.volume,
                isSeekable = sample.flags.seekable,
                audioTracks = sample.tracks.audio,
                videoTracks = sample.tracks.video,
                subtitleTracks = sample.tracks.subtitles,
            )
        }
    }

    /** Terminal states and the time before the file is loaded keep their status; otherwise libmpv's flags decide. */
    private fun statusFor(current: PlaybackState, sample: Sample): PlaybackStatus {
        val terminal = current.status is PlaybackStatus.Completed || current.status is PlaybackStatus.Error
        // A valid time-pos means the file is playing, even when its FILE_LOADED event is still queued behind a slow
        // publish on the event thread (cold emulators); the event only covers the time before playback starts.
        val loaded = fileLoaded || sample.timing.positionMillis != null
        return when {
            terminal || current.media == null || !loaded -> current.status
            sample.flags.pausedForCache -> PlaybackStatus.Buffering
            !sample.flags.paused -> PlaybackStatus.Playing
            current.status == PlaybackStatus.Preparing -> PlaybackStatus.Ready
            else -> PlaybackStatus.Paused
        }
    }

    private fun millis(property: String): Long? = property(property)?.toDoubleOrNull()?.let { (it * MILLIS).toLong() }

    private fun tracks(type: TrackType): List<TrackInfo> {
        val mpvType = when (type) {
            TrackType.AUDIO -> "audio"
            TrackType.VIDEO -> "video"
            TrackType.SUBTITLE -> "sub"
        }
        val count = property("track-list/count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val prefix = "track-list/$index"
            if (property("$prefix/type") != mpvType || property("$prefix/albumart") == "yes") return@mapNotNull null
            val trackId = property("$prefix/id") ?: return@mapNotNull null
            val language = property("$prefix/lang")
            TrackInfo(
                id = trackId,
                type = type,
                label = property("$prefix/title") ?: language?.let { Locale.forLanguageTag(it).displayLanguage }
                    ?: "Track $trackId",
                language = language,
                codec = property("$prefix/codec"),
                bitrate = property("$prefix/demux-bitrate")?.toIntOrNull(),
                width = property("$prefix/demux-w")?.toIntOrNull(),
                height = property("$prefix/demux-h")?.toIntOrNull(),
                frameRate = property("$prefix/demux-fps")?.toFloatOrNull(),
                isSelected = property("$prefix/selected") == "yes",
            )
        }
    }

    private fun pauseForFocusLoss(): Boolean {
        val wasPlaying = !released && property("pause") == "no" && mutableState.value.media != null
        if (wasPlaying) runCatching { setProperty("pause", "yes") }
        return wasPlaying
    }

    private fun setDucked(ducked: Boolean) {
        if (released) return
        if (ducked) {
            volumeBeforeDuck = property("volume")?.toFloatOrNull() ?: MAX_VOLUME
            runCatching { setProperty("volume", (volumeBeforeDuck * DUCK_FACTOR).toString()) }
        } else {
            runCatching { setProperty("volume", volumeBeforeDuck.toString()) }
        }
    }

    private fun property(name: String): String? = if (released) null else LibmpvNative.getProperty(handle, name)

    private fun setProperty(name: String, value: String) {
        checkOpen()
        val code = LibmpvNative.setProperty(handle, name, value)
        check(code >= 0) { "libmpv rejected $name=$value: ${LibmpvNative.errorString(code)}" }
    }

    private fun command(vararg arguments: String) {
        checkOpen()
        val code = LibmpvNative.command(handle, arrayOf(*arguments))
        check(code >= 0) { "libmpv rejected ${arguments.first()}: ${LibmpvNative.errorString(code)}" }
    }

    private fun checkOpen() = check(!released) { "libmpv engine has been released" }

    /**
     * Stops playback while the core is alive (closing the audio output), waits for idle, lets go of the Surface, then
     * destroys the handle; the same order as on desktop (docs/evidence/D-coreaudio-shutdown).
     */
    override fun close() {
        if (released) return
        abWatcher.stop()
        abScope.cancel()
        audioFocus.abandon()
        synchronized(lock) {
            surfaceHolder?.removeCallback(surfaceCallback)
            surfaceHolder = null
            detachSurface()
        }
        val closing = handle
        LibmpvNative.setProperty(closing, "gapless-audio", "no")
        LibmpvNative.command(closing, arrayOf("stop"))
        val deadline = System.nanoTime() + SHUTDOWN_TIMEOUT_NANOS
        while (System.nanoTime() < deadline && LibmpvNative.getProperty(closing, "idle-active") != "yes") {
            Thread.sleep(SHUTDOWN_POLL_MILLIS)
        }
        released = true
        LibmpvNative.wakeup(closing)
        eventThread.join(SHUTDOWN_POLL_MILLIS * EVENT_JOIN_POLLS)
        LibmpvNative.destroy(closing)
        handle = 0
        mutableState.value = PlaybackState(engine = id)
    }

    companion object {
        private const val MAX_VOLUME = 100f
        private const val DUCK_FACTOR = 0.2f
        private const val MILLIS = 1_000.0
        private const val EVENT_WAIT_SECONDS = 0.25
        private const val SHUTDOWN_TIMEOUT_NANOS = 2_000_000_000L
        private const val SHUTDOWN_POLL_MILLIS = 10L
        private const val EVENT_JOIN_POLLS = 100L
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_START_FILE = 6
        private const val EVENT_END_FILE = 7
        private const val EVENT_FILE_LOADED = 8
        private const val END_FILE_EOF = 0
        private const val END_FILE_ERROR = 4

        // No configuration or scripts; video waits for a Surface (vo=null until then); keep the last frame at the end.
        private val BASE_OPTIONS = listOf(
            "config" to "no",
            "terminal" to "no",
            "idle" to "yes",
            "keep-open" to "yes",
            "vo" to "null",
            "gpu-context" to "android",
            "opengl-es" to "yes",
            "ao" to "aaudio,audiotrack",
            "audio-display" to "no",
            // Decode on their own threads: a stalled MediaCodec decoder must not block the core (and every call).
            "vd-queue-enable" to "yes",
            "ad-queue-enable" to "yes",
            "input-default-bindings" to "no",
            "sub-font-provider" to "none",
            "sub-fonts-dir" to "/system/fonts",
            "osd-fonts-dir" to "/system/fonts",
            "sub-font" to "Roboto",
            "network-timeout" to "15",
            "user-agent" to "Vimivo/1.0.0 (Android)",
        )

        val CAPABILITIES = EngineCapabilities(
            localAudio = true,
            localVideo = true,
            httpStreaming = true,
            hlsStreaming = false,
            dashStreaming = false,
            liveStreaming = false,
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
            frameStepping = true,
            screenshots = true,
            abRepeat = true,
            audioEnhancements = AudioEnhancementCapabilities(
                pitchPreservation = true,
                limitation = "The fallback engine plays without Vimivo's system equalizer. Effects are disabled " +
                    "while it is active.",
            ),
        )

        private fun seconds(millis: Long) = String.format(Locale.US, "%.3f", millis / MILLIS)

        /**
         * Automatic decodes in software: this engine only plays what Media3 could not, and FFmpeg's MediaCodec path
         * stalled for good on the Android emulator's H.264 decoder (libmpv then blocks every call). MediaCodec is used
         * only when the user asks for hardware decoding; real-device behaviour is on the manual checklist (ADR-005).
         */
        internal fun hwdecFor(preference: DecoderPreference) = when (preference) {
            DecoderPreference.SOFTWARE, DecoderPreference.AUTOMATIC -> "no"
            DecoderPreference.HARDWARE -> "mediacodec-copy"
        }

        /** Local files, content:// documents, plain http and RTSP; HTTPS/SMB/UPnP stay with Media3 (no TLS yet). */
        fun support(media: MediaItem): SupportLevel =
            if (LibmpvSources.isSupported(media.uri)) SupportLevel.FALLBACK else SupportLevel.UNSUPPORTED
    }
}

class LibmpvPlayerEngineFactory(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : PlayerEngineFactory {
    override val id = EngineId.MPV
    override fun supportFor(media: MediaItem) =
        if (LibmpvNative.available) LibmpvPlayerEngine.support(media) else SupportLevel.UNSUPPORTED
    override fun create(): PlayerEngine = LibmpvPlayerEngine(context, settingsRepository)
}
