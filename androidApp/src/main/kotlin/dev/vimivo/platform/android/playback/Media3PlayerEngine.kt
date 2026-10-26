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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AbRegionWatcher
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.PlaybackDiagnosticsSnapshot
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
import kotlin.math.roundToLong
import dev.vimivo.core.model.MediaItem as VimivoMediaItem

class Media3PlayerEngine(
    context: Context,
    /** Receives the PCM after the effects, for tests that measure what is really played; null in the app. */
    private val audioTap: TeeAudioProcessor.AudioBufferSink? = null,
) : PlayerEngine {
    override val id = EngineId.MEDIA3
    override val capabilities = CAPABILITIES
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    private var positionJob: Job? = null
    private var released = false
    private val audioEnhancements = AndroidAudioEnhancementController(context.applicationContext)
    override val audioEnhancementState = audioEnhancements.state

    private var cachedAudioTracks: List<TrackInfo> = emptyList()
    private var cachedVideoTracks: List<TrackInfo> = emptyList()
    private var cachedSubtitleTracks: List<TrackInfo> = emptyList()

    /** The item queued after the current one for a gapless start; null when none is prepared. */
    private var preparedNext: VimivoMediaItem? = null
    private var requestedVolume = 1f

    /** Wraps playback back to A at B: ExoPlayer has no A–B loop, so the shared watcher seeks (exactly) at the end. */
    private val abWatcher = AbRegionWatcher(
        scope,
        positionMillis = { player.currentPosition },
        isPaused = { !player.playWhenReady || player.playbackState != Player.STATE_READY },
        seekTo = { millis ->
            player.setSeekParameters(SeekParameters.EXACT)
            player.seekTo(millis)
        },
    )

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishState()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishState()
            if (isPlaying) {
                startPositionUpdates()
            } else {
                positionJob?.cancel()
            }
        }
        override fun onIsLoadingChanged(isLoading: Boolean) = publishState()
        override fun onTracksChanged(tracks: Tracks) {
            val trackInfoList = tracks.toTrackInfo()
            cachedAudioTracks = trackInfoList.filter { it.type == TrackType.AUDIO }
            cachedVideoTracks = trackInfoList.filter { it.type == TrackType.VIDEO }
            cachedSubtitleTracks = trackInfoList.filter { it.type == TrackType.SUBTITLE }
            audioEnhancements.setReplayGainTags(tracks.replayGainTags())
            publishState()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val next = preparedNext ?: return
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || mediaItem?.mediaId != next.id.value) return
            adoptPreparedNext(next)
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = publishState()
        override fun onVideoSizeChanged(videoSize: VideoSize) = publishState()
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = audioEnhancements.refreshRoute()

        override fun onPlayerError(error: PlaybackException) {
            positionJob?.cancel()
            val mapped = error.toPlaybackError(id)
            mutableState.value = snapshot().copy(status = PlaybackStatus.Error(mapped))
        }
    }

    /** Plays through the audio pipeline stage that runs Vimivo's equalizer, boost, ReplayGain and limiter. */
    private val renderers = object : DefaultRenderersFactory(context.applicationContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(
                listOfNotNull(audioEnhancements.processor, audioTap?.let(::TeeAudioProcessor)).toTypedArray(),
            )
            .build()
    }

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderers).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
        addListener(listener)
    }
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    override fun supportFor(media: VimivoMediaItem): SupportLevel = support(media)

    override suspend fun open(media: VimivoMediaItem, startPositionMillis: Long): Result<Unit> = runCatching {
        checkOpen()
        require(supportFor(media) != SupportLevel.UNSUPPORTED) { "Media3 does not support this URI or container" }
        cachedAudioTracks = emptyList()
        cachedVideoTracks = emptyList()
        cachedSubtitleTracks = emptyList()
        preparedNext = null
        abWatcher.stop()
        audioEnhancements.setReplayGainTags(ReplayGainTags())
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

    /**
     * Queues [media] in ExoPlayer's playlist after the current item, which ExoPlayer starts without a gap. When it
     * does, [adoptPreparedNext] publishes it as the current media. Audio only.
     */
    override fun prepareNext(media: VimivoMediaItem?): Result<Unit> = runCatching {
        checkOpen()
        if (media != null) {
            require(mutableState.value.media?.kind == MediaKind.AUDIO && media.kind == MediaKind.AUDIO) {
                "Gapless is for audio"
            }
            require(supportFor(media) != SupportLevel.UNSUPPORTED) { "Media3 does not support this URI or container" }
        }
        val playing = player.currentMediaItemIndex
        if (player.mediaItemCount > playing + 1) player.removeMediaItems(playing + 1, player.mediaItemCount)
        preparedNext = null
        if (media != null) {
            player.addMediaItem(MediaItem.Builder().setMediaId(media.id.value).setUri(Uri.parse(media.uri)).build())
            preparedNext = media
        }
    }

    private fun adoptPreparedNext(next: VimivoMediaItem) {
        preparedNext = null
        cachedAudioTracks = emptyList()
        cachedVideoTracks = emptyList()
        cachedSubtitleTracks = emptyList()
        audioEnhancements.setReplayGainTags(ReplayGainTags())
        abWatcher.stop()
        mutableState.value = mutableState.value.copy(
            media = next,
            metadata = next.toPlaybackMetadata(),
            abRepeat = ABRepeatRange(),
            positionMillis = 0,
            audioTracks = emptyList(),
            videoTracks = emptyList(),
            subtitleTracks = emptyList(),
        )
        // The finished item is no longer needed; the playing one becomes index 0 again.
        if (player.currentMediaItemIndex > 0) player.removeMediaItems(0, player.currentMediaItemIndex)
        publishState()
    }

    override fun play(): Result<Unit> = command { player.play() }
    override fun pause(): Result<Unit> = command { player.pause() }
    override fun stop(): Result<Unit> = command { player.stop() }
    override fun seekTo(positionMillis: Long): Result<Unit> = seekTo(positionMillis, exact = true)

    override fun seekTo(positionMillis: Long, exact: Boolean): Result<Unit> = command {
        require(positionMillis >= 0) { "Position cannot be negative" }
        // A tap or a step needs the frame asked for; scrubbing is happy with the nearest keyframe (much faster).
        player.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        player.seekTo(positionMillis)
    }

    override fun setABRepeat(range: ABRepeatRange): Result<Unit> = command {
        abWatcher.watch(range)
        mutableState.value = mutableState.value.copy(abRepeat = range)
    }

    /**
     * ExoPlayer has no frame-step call, so a step is an exact seek by one frame's duration while paused: the frame on
     * screen at the new position is the next (or previous) one. The duration comes from the track's frame rate.
     */
    override fun frameStep(forward: Boolean): Result<Unit> = command {
        val reported = checkNotNull(player.videoFormat) { "There is no video to step through" }.frameRate
        val frameRate = reported.takeIf { it > 0f && it != Format.NO_VALUE.toFloat() } ?: DEFAULT_FRAME_RATE
        val frameMillis = 1_000f / frameRate
        player.pause()
        player.setSeekParameters(SeekParameters.EXACT)
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val step = if (forward) frameMillis else -frameMillis
        player.seekTo((player.currentPosition + step).roundToLong().coerceIn(0, duration))
    }

    /** The video output the session attaches; also the source of snapshots. */
    val video = Media3VideoOutput(player)

    override fun getDiagnosticsSnapshot(): PlaybackDiagnosticsSnapshot? {
        if (released) return null
        val currentMedia = mutableState.value.media ?: return null
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat
        val colorTransfer = when (videoFormat?.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> ColorTransfer.HDR10_PQ
            C.COLOR_TRANSFER_HLG -> ColorTransfer.HLG
            C.COLOR_TRANSFER_SDR -> ColorTransfer.SDR_BT709
            else -> ColorTransfer.UNKNOWN
        }
        return PlaybackDiagnosticsSnapshot(
            engineId = id,
            mediaUri = currentMedia.uri,
            activeVideoCodec = videoFormat?.sampleMimeType,
            activeAudioCodec = audioFormat?.sampleMimeType,
            activeDecoder = videoFormat?.codecs ?: "MediaCodec",
            hwdecActive = true,
            hwdecMode = "mediacodec",
            activeRenderer = "Media3/SurfaceView",
            droppedFrames = 0L,
            totalFrames = 0L,
            avDriftMillis = 0.0,
            estimatedFps = videoFormat?.frameRate?.takeIf { it > 0 } ?: 0f,
            cacheBufferBytes = 0L,
            colorTransfer = colorTransfer,
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration.takeIf { it > 0 },
        )
    }

    override fun setPlaybackSpeed(speed: Float): Result<Unit> = command {
        require(speed in capabilities.supportedSpeedRange) { "Playback speed is outside the supported range" }
        player.setPlaybackSpeed(speed)
    }

    /** Up to 100 % is ExoPlayer's own volume; above that the gain (with a limiter) is applied in the audio pipeline. */
    override fun setVolume(volume: Float): Result<Unit> = command {
        require(volume in 0f..capabilities.maxVolume) { "Volume must be between 0 and ${capabilities.maxVolume}" }
        requestedVolume = volume
        player.volume = volume.coerceAtMost(1f)
        audioEnhancements.setBoost(volume.coerceAtLeast(1f))
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

    /** The group index is the position among all of the player's groups, the same as in [toTrackInfo]'s ids. */
    private fun findTrack(id: String, type: Int): Pair<Tracks.Group, Int>? {
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
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
        return previous.copy(
            engine = id,
            status = status(),
            positionMillis = player.currentPosition.coerceAtLeast(0),
            bufferedPositionMillis = player.bufferedPosition.coerceAtLeast(0),
            durationMillis = player.duration.takeUnless { it == C.TIME_UNSET || it < 0 },
            speed = player.playbackParameters.speed,
            volume = requestedVolume,
            isSeekable = player.isCurrentMediaItemSeekable,
            metadata = player.mediaMetadata.toPlaybackMetadata(previous.metadata),
            audioTracks = cachedAudioTracks,
            videoTracks = cachedVideoTracks,
            subtitleTracks = cachedSubtitleTracks,
        )
    }

    private fun status(): PlaybackStatus = when {
        player.playerError != null -> PlaybackStatus.Error(checkNotNull(player.playerError).toPlaybackError(id))
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
        cachedAudioTracks = emptyList()
        cachedVideoTracks = emptyList()
        cachedSubtitleTracks = emptyList()
        player.removeListener(listener)
        player.release()
        audioEnhancements.close()
        scope.cancel()
        mutableState.value = PlaybackState(engine = id)
    }

    companion object {
        private const val POSITION_UPDATE_MILLIS = 250L
        private const val DEFAULT_FRAME_RATE = 30f
        internal val FALLBACK_ERRORS = setOf(
            PlaybackErrorCode.UNSUPPORTED_CONTAINER,
            PlaybackErrorCode.UNSUPPORTED_CODEC,
            PlaybackErrorCode.UNSUPPORTED_PROFILE,
            PlaybackErrorCode.HARDWARE_ACCELERATION_FAILED,
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
            frameStepping = true,
            screenshots = true,
            abRepeat = true,
            maxVolume = AndroidAudioEnhancementController.MAX_BOOST,
            audioEnhancements = AndroidAudioEnhancementController.CAPABILITIES,
        )

        fun support(media: VimivoMediaItem): SupportLevel {
            val uri = Uri.parse(media.uri)
            if (uri.scheme !in setOf("content", "file", "http", "https", "rtsp", "rtsps")) {
                return SupportLevel.UNSUPPORTED
            }
            if (uri.scheme in setOf("http", "https", "rtsp", "rtsps")) return SupportLevel.PREFERRED
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

/** ReplayGain tags from the selected audio tracks' container metadata (ID3, Vorbis comments, iTunes atoms). */
private fun Tracks.replayGainTags(): ReplayGainTags = groups
    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).metadata } }
    .map(ReplayGainTagReader::read)
    .firstOrNull { it != ReplayGainTags() } ?: ReplayGainTags()

private fun PlaybackException.toPlaybackError(engine: EngineId): PlaybackError {
    val code = when (errorCode) {
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
        // The device has a decoder for the codec but not for this profile, level or size (for example HEVC
        // Main 10 on a device without it): the software fallback engine can still play the file.
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> PlaybackErrorCode.UNSUPPORTED_CODEC
        else -> PlaybackErrorCode.UNKNOWN
    }
    return PlaybackError(
        code = code,
        message = message ?: "Media3 playback failed",
        engine = engine,
        recoverableByFallback = code in Media3PlayerEngine.FALLBACK_ERRORS,
        cause = this,
    )
}
