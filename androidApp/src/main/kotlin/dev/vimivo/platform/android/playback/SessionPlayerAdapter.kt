@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress(
    "ArgumentListWrapping",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "UnstableApiUsage",
    "Wrapping",
)

package dev.vimivo.platform.android.playback

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.QueueContext
import dev.vimivo.settings.RepeatBehavior
import dev.vimivo.settings.ShuffleBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import dev.vimivo.core.model.MediaItem as VimivoMediaItem

/** Media3 session-facing player that keeps Media3 and LibVLC behind one system session. */
class SessionPlayerAdapter(
    private val manager: PlaybackEngineManager,
    private val scope: CoroutineScope,
    private val store: PlaybackStateStore,
    private val sleepTimer: SleepTimerController,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var playlist = emptyList<MediaItem>()
    private var currentIndex = 0
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var released = false
    private var restorePosition = 0L
    private var persistJob: Job? = null
    private var videoOutput: Any? = null

    // Engines are recreated for every opened item, so track the attached instance rather than its id.
    private var attachedVideoEngine: PlayerEngine? = null

    init {
        scope.launch {
            manager.state.collectLatest { state ->
                attachVideoOutputIfNeeded()
                if (state.status == PlaybackStatus.Completed) {
                    if (sleepTimer.consumeStopAfterCurrent()) manager.stop() else advanceAfterCompletion()
                }
                persistDebounced(state)
                invalidateState()
            }
        }
    }

    suspend fun restoreWithoutPlaying() {
        val restored = store.restore() ?: return
        playlist = restored.items
        currentIndex = restored.currentIndex
        repeatMode = restored.repeatMode
        shuffle = restored.shuffleEnabled
        restorePosition = restored.positionMillis
        invalidateState()
    }

    fun applyDefaults(shuffleBehavior: ShuffleBehavior, repeatBehavior: RepeatBehavior) {
        if (shuffleBehavior != ShuffleBehavior.REMEMBER) shuffle = shuffleBehavior == ShuffleBehavior.ON
        if (repeatBehavior != RepeatBehavior.REMEMBER) {
            repeatMode = when (repeatBehavior) {
                RepeatBehavior.ONE -> Player.REPEAT_MODE_ONE
                RepeatBehavior.ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
        invalidateState()
    }

    suspend fun persist() {
        store.save(playlist, currentIndex, manager.state.value.positionMillis, repeatMode, shuffle)
    }

    override fun getState(): State {
        val playback = manager.state.value
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_RELEASE,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_SET_VIDEO_SURFACE,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                // Without the GET_* commands Media3 strips the timeline, current item, metadata, and
                // duration from what controllers receive, leaving the in-app UI with an empty queue.
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_GET_TEXT,
            )
            .build()
        val items = playlist.mapIndexed { index, item ->
            MediaItemData.Builder("$index:${item.mediaId}")
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setIsSeekable(playback.isSeekable)
                .setDurationUs(if (index == currentIndex) (playback.durationMillis ?: C.TIME_UNSET).toUs() else C.TIME_UNSET)
                .build()
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) C.INDEX_UNSET else currentIndex.coerceIn(playlist.indices))
            .setPlayWhenReady(playback.status == PlaybackStatus.Playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playback.status.toMedia3State())
            .setIsLoading(playback.status == PlaybackStatus.Buffering || playback.status == PlaybackStatus.Preparing)
            .setContentPositionMs(
                when {
                    playlist.isEmpty() -> PositionSupplier.getConstant(C.TIME_UNSET)
                    playback.media == null -> PositionSupplier.getConstant(restorePosition)
                    playback.status == PlaybackStatus.Playing ->
                        PositionSupplier.getExtrapolating(playback.positionMillis, playback.speed)
                    else -> PositionSupplier.getConstant(playback.positionMillis)
                },
            )
            .setContentBufferedPositionMs(PositionSupplier.getConstant(playback.bufferedPositionMillis))
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffle)
            .setPlaybackParameters(PlaybackParameters(playback.speed))
            .setVolume(playback.volume)
            .setVideoSize(VideoSize(playback.videoTracks.firstOrNull()?.width ?: 0, playback.videoTracks.firstOrNull()?.height ?: 0))
            .setCurrentCues(
                (manager.activeEngine.value as? Media3PlayerEngine)?.player?.currentCues ?: CueGroup.EMPTY_TIME_ZERO,
            )
            .setPlayerError((playback.status as? PlaybackStatus.Error)?.error?.toPlaybackException())
            .build()
    }

    override fun handleSetMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        playlist = items
        currentIndex = startIndex.takeUnless { it == C.INDEX_UNSET }?.coerceIn(playlist.indices) ?: 0
        openCurrent(startPositionMs.takeUnless { it == C.TIME_UNSET } ?: 0)
        return done()
    }

    override fun handleAddMediaItems(index: Int, items: List<MediaItem>): ListenableFuture<*> {
        playlist = playlist.toMutableList().apply { addAll(index.coerceIn(0, size), items) }
        persistNow()
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removedCurrent = currentIndex in fromIndex until toIndex
        playlist = playlist.toMutableList().apply { subList(fromIndex, toIndex).clear() }
        currentIndex = currentIndex.coerceAtMost((playlist.size - 1).coerceAtLeast(0))
        if (removedCurrent && playlist.isNotEmpty()) openCurrent(0) else if (playlist.isEmpty()) manager.stop()
        persistNow()
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val mutable = playlist.toMutableList()
        val moved = mutable.subList(fromIndex, toIndex).toList()
        mutable.subList(fromIndex, toIndex).clear()
        mutable.addAll(newIndex.coerceIn(0, mutable.size), moved)
        val currentId = playlist.getOrNull(currentIndex)?.mediaId
        playlist = mutable
        currentIndex = playlist.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
        persistNow()
        return done()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            if (manager.state.value.media == null && playlist.isNotEmpty()) openCurrent(restorePosition, play = true)
            else manager.play()
        } else manager.pause()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (manager.state.value.media == null && playlist.isNotEmpty()) openCurrent(restorePosition)
        return done()
    }

    override fun handleStop(): ListenableFuture<*> = done().also { manager.stop(); persistNow() }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != currentIndex) {
            currentIndex = mediaItemIndex.coerceIn(playlist.indices)
            openCurrent(positionMs.takeUnless { it == C.TIME_UNSET } ?: 0, manager.state.value.status == PlaybackStatus.Playing)
        } else if (positionMs != C.TIME_UNSET) manager.seekTo(positionMs)
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> = done().also {
        this.repeatMode = repeatMode
        persistNow()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> = done().also {
        shuffle = shuffleModeEnabled
        persistNow()
    }

    override fun handleSetPlaybackParameters(parameters: PlaybackParameters): ListenableFuture<*> = done().also {
        manager.setPlaybackSpeed(parameters.speed)
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> = done().also {
        manager.setVolume(volume)
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> = done().also {
        this.videoOutput = videoOutput
        attachedVideoEngine = null
        attachVideoOutputIfNeeded()
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> = done().also {
        (manager.activeEngine.value as? Media3PlayerEngine)?.player?.clearVideoSurface()
        (manager.activeEngine.value as? LibVlcPlayerEngine)?.clearVideoSurface()
        this.videoOutput = null
        attachedVideoEngine = null
    }

    private fun attachVideoOutputIfNeeded() {
        val output = videoOutput
        val engine = manager.activeEngine.value
        if (output == null || engine == null || attachedVideoEngine === engine) return
        when (engine) {
            is Media3PlayerEngine -> when (output) {
                is Surface -> engine.player.setVideoSurface(output)
                is SurfaceHolder -> engine.player.setVideoSurfaceHolder(output)
            }
            is LibVlcPlayerEngine -> if (output is SurfaceHolder) engine.setVideoSurfaceHolder(output)
            else -> return
        }
        attachedVideoEngine = engine
    }

    override fun handleRelease(): ListenableFuture<*> = done().also {
        if (!released) {
            released = true
            persistNow()
            manager.close()
        }
    }

    private fun openCurrent(position: Long, play: Boolean = false) {
        val queue = playlist.map(MediaItem::toVimivo)
        if (queue.isEmpty()) return
        scope.launch {
            manager.open(QueueContext(queue, currentIndex), position).onSuccess { if (play) manager.play() }
        }
    }

    private fun advanceAfterCompletion() {
        if (playlist.isEmpty()) return
        val next = when {
            repeatMode == Player.REPEAT_MODE_ONE -> currentIndex
            shuffle && playlist.size > 1 -> playlist.indices.filterNot { it == currentIndex }.random()
            currentIndex < playlist.lastIndex -> currentIndex + 1
            repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> return
        }
        currentIndex = next
        openCurrent(0, play = true)
    }

    private fun persistDebounced(state: PlaybackState) {
        persistJob?.cancel()
        persistJob = scope.launch {
            kotlinx.coroutines.delay(1_000)
            store.save(playlist, currentIndex, state.positionMillis, repeatMode, shuffle)
        }
    }

    private fun persistNow() {
        scope.launch { store.save(playlist, currentIndex, manager.state.value.positionMillis, repeatMode, shuffle) }
    }

    private fun done() = Futures.immediateVoidFuture()
}

private fun Long.toUs() = if (this == C.TIME_UNSET) C.TIME_UNSET else milliseconds.inWholeMicroseconds

private fun PlaybackStatus.toMedia3State() = when (this) {
    PlaybackStatus.Idle -> Player.STATE_IDLE
    PlaybackStatus.Preparing, PlaybackStatus.Buffering -> Player.STATE_BUFFERING
    PlaybackStatus.Ready, PlaybackStatus.Playing, PlaybackStatus.Paused -> Player.STATE_READY
    PlaybackStatus.Completed -> Player.STATE_ENDED
    is PlaybackStatus.Error -> Player.STATE_IDLE
}

private fun dev.vimivo.playback.PlaybackError.toPlaybackException() = PlaybackException(
    message,
    cause,
    when (code) {
        PlaybackErrorCode.SOURCE_NOT_FOUND -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        PlaybackErrorCode.ACCESS_DENIED -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        PlaybackErrorCode.NETWORK -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        PlaybackErrorCode.DECODER_INITIALIZATION -> PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        PlaybackErrorCode.DECODING_FAILED -> PlaybackException.ERROR_CODE_DECODING_FAILED
        PlaybackErrorCode.UNSUPPORTED_CONTAINER -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        else -> PlaybackException.ERROR_CODE_UNSPECIFIED
    },
)

private fun MediaItem.toVimivo() = VimivoMediaItem(
    id = MediaId(mediaId),
    uri = checkNotNull(localConfiguration?.uri).toString(),
    title = mediaMetadata.title?.toString() ?: mediaId,
    kind = when {
        localConfiguration?.mimeType?.startsWith("video/") == true -> MediaKind.VIDEO
        localConfiguration?.uri?.scheme in setOf("http", "https", "rtsp", "rtsps", "smb", "upnp") -> MediaKind.STREAM
        else -> MediaKind.AUDIO
    },
    artist = mediaMetadata.artist?.toString(),
    album = mediaMetadata.albumTitle?.toString(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
    mimeType = localConfiguration?.mimeType,
)
