@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("ImportOrdering", "UnstableApiUsage")

package dev.vimivo.platform.android.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
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
import dev.vimivo.core.model.MediaItem as VimivoMediaItem

class Media3PlayerEngine(context: Context) : PlayerEngine {
    override val id = EngineId.MEDIA3
    override val capabilities = CAPABILITIES
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    private var positionJob: Job? = null
    private var released = false
    private val audioEnhancements = AndroidAudioEnhancementController(context.applicationContext)
    override val audioEnhancementState = audioEnhancements.state

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = publishState()
        override fun onIsLoadingChanged(isLoading: Boolean) = publishState()
        override fun onTracksChanged(tracks: Tracks) = publishState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = publishState()
        override fun onVideoSizeChanged(videoSize: VideoSize) = publishState()
        override fun onAudioSessionIdChanged(audioSessionId: Int) = audioEnhancements.attach(audioSessionId)
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = audioEnhancements.refreshRoute()

        override fun onPlayerError(error: PlaybackException) {
            val mapped = mapError(error)
            mutableState.value = snapshot().copy(status = PlaybackStatus.Error(mapped))
        }
    }

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
        addListener(listener)
    }
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    override fun supportFor(media: VimivoMediaItem): SupportLevel = support(media)

    override suspend fun open(media: VimivoMediaItem, startPositionMillis: Long): Result<Unit> = runCatching {
        checkOpen()
        require(supportFor(media) != SupportLevel.UNSUPPORTED) { "Media3 does not support this URI or container" }
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Preparing,
            positionMillis = startPositionMillis,
            metadata = media.toPlaybackMetadata(),
        )
        player.setMediaItem(MediaItem.fromUri(Uri.parse(media.uri)), startPositionMillis)
        player.prepare()
        startPositionUpdates()
    }

    override fun play(): Result<Unit> = command { player.play() }
    override fun pause(): Result<Unit> = command { player.pause() }
    override fun stop(): Result<Unit> = command { player.stop() }
    override fun seekTo(positionMillis: Long): Result<Unit> = command {
        require(positionMillis >= 0) { "Position cannot be negative" }
        player.seekTo(positionMillis)
    }

    override fun setPlaybackSpeed(speed: Float): Result<Unit> = command {
        require(speed in capabilities.supportedSpeedRange) { "Playback speed is outside the supported range" }
        player.setPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float): Result<Unit> = command {
        require(volume in 0f..1f) { "Volume must be between 0 and 1" }
        player.volume = volume
    }

    override fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> =
        audioEnhancements.apply(settings)

    override fun selectAudioTrack(trackId: String?): Result<Unit> = selectTrack(C.TRACK_TYPE_AUDIO, trackId)
    override fun selectVideoTrack(trackId: String?): Result<Unit> = selectTrack(C.TRACK_TYPE_VIDEO, trackId)
    override fun selectSubtitleTrack(trackId: String?): Result<Unit> = selectTrack(C.TRACK_TYPE_TEXT, trackId)

    override fun addExternalSubtitle(subtitle: ExternalSubtitle): Result<Unit> = command {
        val current = player.currentMediaItem ?: error("Open a video before loading subtitles")
        val configuration = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
            .setMimeType(subtitle.mimeType)
            .setLanguage(subtitle.language)
            .setLabel(subtitle.label)
            .setId("external:${subtitle.uri}")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
            .filterNot { it.uri == configuration.uri }
        val replacement = current.buildUpon().setSubtitleConfigurations(existing + configuration).build()
        val position = player.currentPosition.coerceAtLeast(0)
        val shouldPlay = player.playWhenReady
        player.setMediaItem(replacement, position)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        player.prepare()
        player.playWhenReady = shouldPlay
    }

    private fun selectTrack(type: Int, trackId: String?): Result<Unit> = command {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (trackId == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val selection = findTrack(trackId, type) ?: error("Track is no longer available")
            builder.setTrackTypeDisabled(type, false)
            builder.addOverride(TrackSelectionOverride(selection.first.mediaTrackGroup, selection.second))
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun findTrack(id: String, type: Int): Pair<Tracks.Group, Int>? {
        player.currentTracks.groups.filter { it.type == type }.forEachIndexed { groupIndex, group ->
            repeat(group.length) { trackIndex ->
                if (trackId(type, groupIndex, trackIndex) == id) return group to trackIndex
            }
        }
        return null
    }

    private fun publishState() {
        if (!released) mutableState.value = snapshot()
    }

    private fun snapshot(): PlaybackState {
        val previous = mutableState.value
        val tracks = player.currentTracks.toTrackInfo()
        return previous.copy(
            engine = id,
            status = status(),
            positionMillis = player.currentPosition.coerceAtLeast(0),
            bufferedPositionMillis = player.bufferedPosition.coerceAtLeast(0),
            durationMillis = player.duration.takeUnless { it == C.TIME_UNSET || it < 0 },
            speed = player.playbackParameters.speed,
            volume = player.volume,
            isSeekable = player.isCurrentMediaItemSeekable,
            metadata = player.mediaMetadata.toPlaybackMetadata(previous.metadata),
            audioTracks = tracks.filter { it.type == TrackType.AUDIO },
            videoTracks = tracks.filter { it.type == TrackType.VIDEO },
            subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE },
        )
    }

    private fun status(): PlaybackStatus = when {
        player.playerError != null -> PlaybackStatus.Error(mapError(checkNotNull(player.playerError)))
        player.playbackState == Player.STATE_IDLE && mutableState.value.media == null -> PlaybackStatus.Idle
        player.playbackState == Player.STATE_IDLE -> PlaybackStatus.Preparing
        player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
        player.playbackState == Player.STATE_ENDED -> PlaybackStatus.Completed
        player.isPlaying -> PlaybackStatus.Playing
        player.playbackState == Player.STATE_READY && player.playWhenReady -> PlaybackStatus.Ready
        player.playbackState == Player.STATE_READY && player.currentPosition > 0 -> PlaybackStatus.Paused
        else -> PlaybackStatus.Ready
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                publishState()
                delay(POSITION_UPDATE_MILLIS)
            }
        }
    }

    private fun <T> command(block: () -> T): Result<Unit> = runCatching {
        checkOpen()
        block()
        publishState()
    }

    private fun checkOpen() = check(!released) { "Media3 engine has been released" }

    override fun close() {
        if (released) return
        released = true
        positionJob?.cancel()
        player.removeListener(listener)
        player.release()
        audioEnhancements.close()
        scope.cancel()
        mutableState.value = PlaybackState(engine = id)
    }

    private fun mapError(error: PlaybackException): PlaybackError {
        val code = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorCode.SOURCE_NOT_FOUND
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackErrorCode.ACCESS_DENIED
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorCode.NETWORK
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlaybackErrorCode.UNSUPPORTED_CONTAINER
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackErrorCode.DECODER_INITIALIZATION
            PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackErrorCode.DECODING_FAILED
            else -> PlaybackErrorCode.UNKNOWN
        }
        return PlaybackError(
            code = code,
            message = error.message ?: "Media3 playback failed",
            engine = id,
            recoverableByFallback = code in FALLBACK_ERRORS,
            cause = error,
        )
    }

    companion object {
        private const val POSITION_UPDATE_MILLIS = 250L
        private val FALLBACK_ERRORS = setOf(
            PlaybackErrorCode.UNSUPPORTED_CONTAINER,
            PlaybackErrorCode.DECODER_INITIALIZATION,
            PlaybackErrorCode.DECODING_FAILED,
        )
        private val MEDIA3_EXTENSIONS = setOf(
            "mp4", "m4a", "mkv", "webm", "mov", "mpeg", "mpg", "ts", "m2ts", "3gp",
            "flv", "mp3", "aac", "flac", "wav", "ogg", "opus",
        )
        val CAPABILITIES = EngineCapabilities(
            localAudio = true,
            localVideo = true,
            httpStreaming = true,
            hlsStreaming = true,
            dashStreaming = true,
            liveStreaming = true,
            hardwareDecoding = true,
            softwareDecoding = false,
            playbackSpeed = true,
            audioTrackSelection = true,
            videoTrackSelection = true,
            embeddedSubtitles = true,
            externalSubtitles = true,
            hdr = true,
            subtitleAppearance = true,
            subtitlePosition = true,
            audioEnhancements = AndroidAudioEnhancementController.CAPABILITIES,
        )

        fun support(media: VimivoMediaItem): SupportLevel {
            val uri = Uri.parse(media.uri)
            if (uri.scheme !in setOf("content", "file", "http", "https")) return SupportLevel.UNSUPPORTED
            if (uri.scheme == "http" || uri.scheme == "https") return SupportLevel.PREFERRED
            val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return if (extension.isEmpty() || extension in MEDIA3_EXTENSIONS) {
                SupportLevel.PREFERRED
            } else {
                SupportLevel.UNSUPPORTED
            }
        }
    }
}

class Media3PlayerEngineFactory(private val context: Context) : PlayerEngineFactory {
    override val id = EngineId.MEDIA3
    override fun supportFor(media: VimivoMediaItem) = Media3PlayerEngine.support(media)
    override fun create(): PlayerEngine = Media3PlayerEngine(context)
}

private fun Tracks.toTrackInfo(): List<TrackInfo> = buildList {
    groups.forEachIndexed { groupIndex, group ->
        val type = group.type.toTrackType() ?: return@forEachIndexed
        repeat(group.length) { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            add(
                TrackInfo(
                    id = trackId(group.type, groupIndex, trackIndex),
                    type = type,
                    label = format.label ?: format.language ?: "${type.name.lowercase()} ${trackIndex + 1}",
                    language = format.language,
                    mimeType = format.sampleMimeType,
                    codec = format.codecs,
                    channels = format.channelCount.takeIf { it != Format.NO_VALUE },
                    sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE },
                    width = format.width.takeIf { it != Format.NO_VALUE },
                    height = format.height.takeIf { it != Format.NO_VALUE },
                    frameRate = format.frameRate.takeIf { it != Format.NO_VALUE.toFloat() },
                    isSelected = group.isTrackSelected(trackIndex),
                    origin = if (format.id?.startsWith("external:") == true) {
                        TrackOrigin.EXTERNAL
                    } else {
                        TrackOrigin.EMBEDDED
                    },
                ),
            )
        }
    }
}

private fun Int.toTrackType(): TrackType? = when (this) {
    C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
    C.TRACK_TYPE_VIDEO -> TrackType.VIDEO
    C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
    else -> null
}

private fun trackId(type: Int, groupIndex: Int, trackIndex: Int) = "$type:$groupIndex:$trackIndex"

private fun MediaMetadata.toPlaybackMetadata(fallback: PlaybackMetadata) = PlaybackMetadata(
    title = title?.toString() ?: fallback.title,
    artist = artist?.toString() ?: fallback.artist,
    album = albumTitle?.toString() ?: fallback.album,
    artworkUri = artworkUri?.toString() ?: fallback.artworkUri,
)

private fun VimivoMediaItem.toPlaybackMetadata() = PlaybackMetadata(title, artist, album, artworkUri)
