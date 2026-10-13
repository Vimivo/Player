package dev.vimivo.platform.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcPlayerEngine(context: Context) : PlayerEngine {
    override val id = EngineId.LIBVLC
    override val capabilities = CAPABILITIES
    private val appContext = context.applicationContext
    private val libVlc = LibVLC(appContext, listOf("--no-stats"))
    private val mediaPlayer = MediaPlayer(libVlc)
    private val audioFocus = VlcAudioFocusController(appContext) { pauseForFocusLoss() }
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    private var currentMedia: Media? = null
    private var pendingStartPositionMillis = 0L
    private var released = false
    private var videoLayout: VLCVideoLayout? = null
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        mediaPlayer.setEventListener(::onEvent)
    }

    override fun supportFor(media: MediaItem): SupportLevel = support(media)

    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> = runCatching {
        checkOpen()
        require(supportFor(media) != SupportLevel.UNSUPPORTED) { "LibVLC does not support this URI" }
        currentMedia?.release()
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            positionMillis = startPositionMillis,
            metadata = PlaybackMetadata(media.title, media.artist, media.album, media.artworkUri),
        )
        val vlcMedia = Media(libVlc, Uri.parse(media.uri)).apply {
            setHWDecoderEnabled(true, true)
            setDefaultMediaPlayerOptions()
        }
        currentMedia = vlcMedia
        mediaPlayer.media = vlcMedia
        pendingStartPositionMillis = startPositionMillis
        mutableState.value = mutableState.value.copy(status = PlaybackStatus.Ready)
    }

    override fun play(): Result<Unit> = command {
        check(audioFocus.request()) { "Audio focus was denied" }
        mediaPlayer.play()
    }

    override fun pause(): Result<Unit> = command { mediaPlayer.pause() }

    override fun stop(): Result<Unit> = command {
        mediaPlayer.stop()
        audioFocus.abandon()
    }

    override fun seekTo(positionMillis: Long): Result<Unit> = command {
        require(positionMillis >= 0) { "Position cannot be negative" }
        check(mediaPlayer.isSeekable) { "This media source is not seekable" }
        mediaPlayer.setTime(positionMillis, true)
    }

    override fun setPlaybackSpeed(speed: Float): Result<Unit> = command {
        require(speed in capabilities.supportedSpeedRange) { "Playback speed is outside the supported range" }
        mediaPlayer.rate = speed
    }

    override fun setVolume(volume: Float): Result<Unit> = command {
        require(volume in 0f..1f) { "Volume must be between 0 and 1" }
        check(mediaPlayer.setVolume((volume * MAX_VLC_VOLUME).toInt()) >= 0) { "LibVLC rejected the volume" }
    }

    override fun selectAudioTrack(trackId: String?): Result<Unit> = command {
        check(trackId != null && mediaPlayer.setAudioTrack(trackId.toInt())) { "Audio track is unavailable" }
        publishTracks()
    }

    override fun selectVideoTrack(trackId: String?): Result<Unit> = command {
        if (trackId == null) {
            mediaPlayer.setVideoTrackEnabled(false)
        } else {
            mediaPlayer.setVideoTrackEnabled(true)
            check(mediaPlayer.setVideoTrack(trackId.toInt())) { "Video track is unavailable" }
        }
        publishTracks()
    }

    override fun selectSubtitleTrack(trackId: String?): Result<Unit> = command {
        check(mediaPlayer.setSpuTrack(trackId?.toInt() ?: DISABLED_TRACK)) { "Subtitle track is unavailable" }
        publishTracks()
    }

    fun attachVideo(layout: VLCVideoLayout) {
        checkOpen()
        if (videoLayout === layout) return
        detachVideo()
        videoLayout = layout
        mediaPlayer.attachViews(layout, null, true, false)
    }

    fun detachVideo() {
        if (mediaPlayer.vlcVout.areViewsAttached()) mediaPlayer.detachViews()
        videoLayout = null
    }

    private fun onEvent(event: MediaPlayer.Event) {
        if (released) return
        when (event.type) {
            MediaPlayer.Event.Opening -> updateStatus(PlaybackStatus.Preparing)
            MediaPlayer.Event.Buffering -> updateStatus(PlaybackStatus.Buffering)
            MediaPlayer.Event.Playing -> {
                if (pendingStartPositionMillis > 0) {
                    mediaPlayer.setTime(pendingStartPositionMillis, true)
                    pendingStartPositionMillis = 0
                }
                updateStatus(PlaybackStatus.Playing)
            }
            MediaPlayer.Event.Paused -> updateStatus(PlaybackStatus.Paused)
            MediaPlayer.Event.Stopped -> updateStatus(PlaybackStatus.Idle)
            MediaPlayer.Event.EndReached -> {
                audioFocus.abandon()
                updateStatus(PlaybackStatus.Completed)
            }
            MediaPlayer.Event.EncounteredError -> updateStatus(
                PlaybackStatus.Error(
                    PlaybackError(
                        code = PlaybackErrorCode.DECODING_FAILED,
                        message = "LibVLC could not decode or read this media",
                        engine = id,
                    ),
                ),
            )
            MediaPlayer.Event.TimeChanged,
            MediaPlayer.Event.PositionChanged,
            MediaPlayer.Event.LengthChanged,
            MediaPlayer.Event.SeekableChanged -> publishPosition()
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected -> publishTracks()
        }
    }

    private fun updateStatus(status: PlaybackStatus) {
        mutableState.value = snapshot().copy(status = status)
    }

    private fun publishPosition() {
        mutableState.value = snapshot()
    }

    private fun publishTracks() {
        mutableState.value = snapshot().copy(
            audioTracks = mediaPlayer.audioTracks.toTrackInfo(TrackType.AUDIO, mediaPlayer.audioTrack),
            videoTracks = mediaPlayer.videoTracks.toTrackInfo(TrackType.VIDEO, mediaPlayer.videoTrack),
            subtitleTracks = mediaPlayer.spuTracks.toTrackInfo(TrackType.SUBTITLE, mediaPlayer.spuTrack),
        )
    }

    private fun snapshot(): PlaybackState {
        val current = mutableState.value
        val video = mediaPlayer.currentVideoTrack
        val videoTracks = mediaPlayer.videoTracks.toTrackInfo(TrackType.VIDEO, mediaPlayer.videoTrack).map { track ->
            if (track.isSelected && video != null) {
                track.copy(
                    codec = video.codec,
                    bitrate = video.bitrate.takeIf { it > 0 },
                    width = video.width.takeIf { it > 0 },
                    height = video.height.takeIf { it > 0 },
                    frameRate = video.frameRateDen.takeIf { it > 0 }?.let { video.frameRateNum.toFloat() / it },
                )
            } else {
                track
            }
        }
        return current.copy(
            positionMillis = mediaPlayer.time.coerceAtLeast(0),
            bufferedPositionMillis = current.positionMillis,
            durationMillis = mediaPlayer.length.takeIf { it > 0 },
            speed = mediaPlayer.rate,
            volume = (mediaPlayer.volume.toFloat() / MAX_VLC_VOLUME).coerceIn(0f, 1f),
            isSeekable = mediaPlayer.isSeekable,
            audioTracks = mediaPlayer.audioTracks.toTrackInfo(TrackType.AUDIO, mediaPlayer.audioTrack),
            videoTracks = videoTracks,
            subtitleTracks = mediaPlayer.spuTracks.toTrackInfo(TrackType.SUBTITLE, mediaPlayer.spuTrack),
        )
    }

    private fun pauseForFocusLoss() {
        if (!released && mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    private fun command(block: () -> Unit): Result<Unit> = runCatching {
        checkOpen()
        block()
    }

    private fun checkOpen() = check(!released) { "LibVLC engine has been released" }

    override fun close() {
        if (released) return
        released = true
        audioFocus.abandon()
        detachVideo()
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        currentMedia?.release()
        currentMedia = null
        mediaPlayer.release()
        libVlc.release()
        mutableState.value = PlaybackState(engine = id)
    }

    companion object {
        private const val MAX_VLC_VOLUME = 100
        private const val DISABLED_TRACK = -1
        val CAPABILITIES = EngineCapabilities(
            localAudio = true,
            localVideo = true,
            httpStreaming = true,
            hardwareDecoding = true,
            softwareDecoding = true,
            playbackSpeed = true,
            audioTrackSelection = true,
            videoTrackSelection = true,
            embeddedSubtitles = true,
            externalSubtitles = false,
            hdr = true,
        )

        fun support(media: MediaItem): SupportLevel {
            val scheme = Uri.parse(media.uri).scheme
            return if (scheme in setOf("content", "file", "http", "https")) {
                SupportLevel.FALLBACK
            } else {
                SupportLevel.UNSUPPORTED
            }
        }
    }
}

class LibVlcPlayerEngineFactory(private val context: Context) : PlayerEngineFactory {
    override val id = EngineId.LIBVLC
    override fun supportFor(media: MediaItem) = LibVlcPlayerEngine.support(media)
    override fun create(): PlayerEngine = LibVlcPlayerEngine(context)
}

private fun Array<MediaPlayer.TrackDescription>?.toTrackInfo(type: TrackType, selectedId: Int): List<TrackInfo> =
    orEmpty().filter { it.id >= 0 }.map { description ->
        TrackInfo(
            id = description.id.toString(),
            type = type,
            label = description.name,
            isSelected = description.id == selectedId,
        )
    }

private class VlcAudioFocusController(context: Context, private val onLoss: () -> Unit) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
    }
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(listener)
        .build()

    fun request(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    fun abandon() = Unit.also { audioManager.abandonAudioFocusRequest(request) }
}
