@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "ImportOrdering",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "TooManyFunctions",
    "UnstableApiUsage",
)

package dev.vimivo.platform.android.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.domain.repository.OrganizationRepository
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.TrackInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class PlaybackUiState(
    val connected: Boolean = false,
    val current: MediaItem? = null,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val bufferedPositionMillis: Long = 0,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false,
    val engine: EngineId? = null,
    val capabilities: EngineCapabilities? = null,
    val audioTracks: List<TrackInfo> = emptyList(),
    val videoTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val externalSubtitleCandidates: List<ExternalSubtitle> = emptyList(),
    val trackErrorMessage: String? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val lyrics: LyricsDocument? = null,
    val currentLyricIndex: Int = -1,
    val abRepeatStartMillis: Long? = null,
    val abRepeatEndMillis: Long? = null,
)

class PlaybackViewModel(
    private val connection: PlaybackController,
    private val mediaRepository: MediaRepository,
    private val diagnostics: PlaybackDiagnostics,
    private val subtitles: AndroidSubtitleRepository,
    private val organization: OrganizationRepository,
    private val sleepTimer: SleepTimerController,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaybackUiState())
    private var player: Player? = null
    private var loadedFavoriteId: String? = null
    private var detectedSubtitleMediaId: String? = null
    private var bookmarkMediaId: String? = null
    private var lyricMediaId: String? = null
    private var bookmarkJob: Job? = null
    private var abRepeatJob: Job? = null
    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()
    val sleepTimerState = sleepTimer.state
    val audioEnhancementState = diagnostics.audio

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    init {
        viewModelScope.launch {
            connection.controller.collect { connected ->
                player?.removeListener(listener)
                player = connected
                connected?.addListener(listener)
                if (connected == null) mutableState.value = PlaybackUiState() else publish(connected)
            }
        }
        viewModelScope.launch {
            // Player.Listener does not fire for continuous position changes, so poll while playing.
            while (true) {
                delay(POSITION_TICK_MILLIS)
                val current = player ?: continue
                if (current.isPlaying) {
                    mutableState.update {
                        it.copy(
                            positionMillis = current.currentPosition.coerceAtLeast(0),
                            bufferedPositionMillis = current.bufferedPosition.coerceAtLeast(0),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            diagnostics.state.collect { engineState ->
                mutableState.value = mutableState.value.copy(
                    engine = engineState.engine,
                    capabilities = engineState.engine?.let { engine ->
                        if (engineState.media == null) null else diagnosticsCapabilities(engine)
                    },
                    audioTracks = engineState.audioTracks,
                    videoTracks = engineState.videoTracks,
                    subtitleTracks = engineState.subtitleTracks,
                )
            }
        }
    }

    fun togglePlayback() = player?.let {
        when {
            it.isPlaying -> it.pause()
            it.playbackState == Player.STATE_ENDED -> {
                it.seekTo(0)
                it.play()
            }
            else -> it.play()
        }
    }
    fun stop() = player?.stop()
    fun next() = player?.seekToNext()
    fun previous() = player?.seekToPrevious()
    fun seekTo(positionMillis: Long) = player?.seekTo(positionMillis)
    fun playQueueIndex(index: Int) = player?.seekToDefaultPosition(index)
    fun removeQueueItem(index: Int) = player?.removeMediaItem(index)
    fun moveQueueItem(from: Int, to: Int) = player?.moveMediaItem(from, to)
    fun toggleShuffle() { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        player?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun setSpeed(speed: Float) { player?.setPlaybackSpeed(speed) }
    fun setVolume(volume: Float) { player?.volume = volume.coerceIn(0f, 1f) }
    fun setAbRepeatStart() {
        val position = state.value.positionMillis
        mutableState.value = state.value.copy(abRepeatStartMillis = position, abRepeatEndMillis = null)
    }

    fun setAbRepeatEnd() {
        val start = state.value.abRepeatStartMillis ?: return
        val end = state.value.positionMillis
        if (end <= start + MIN_AB_REPEAT_MILLIS) {
            mutableState.value = state.value.copy(trackErrorMessage = "Choose an end point at least one second after A")
            return
        }
        mutableState.value = state.value.copy(abRepeatEndMillis = end, trackErrorMessage = null)
        observeAbRepeat()
    }

    fun clearAbRepeat() {
        abRepeatJob?.cancel()
        abRepeatJob = null
        mutableState.value = state.value.copy(abRepeatStartMillis = null, abRepeatEndMillis = null)
    }
    fun startSleepTimer(minutes: Int, fadeOutSeconds: Int = 0) = sleepTimer.schedule(minutes, fadeOutSeconds)
    fun stopAfterCurrent(fadeOutSeconds: Int = 0) = sleepTimer.afterCurrent(fadeOutSeconds)
    fun cancelSleepTimer() = sleepTimer.cancel()
    fun loadLyrics(uri: android.net.Uri) {
        viewModelScope.launch {
            lyricsRepository.load(uri).fold(
                onSuccess = { lyrics -> mutableState.value = state.value.copy(lyrics = lyrics) },
                onFailure = { error -> mutableState.value = state.value.copy(trackErrorMessage = error.message) },
            )
        }
    }
    fun selectAudioTrack(id: String?) { diagnostics.selectAudioTrack(id) }
    fun selectSubtitleTrack(id: String?) { diagnostics.selectSubtitleTrack(id) }
    fun setSubtitleDelay(delayMillis: Long) = reportTrackResult(diagnostics.setSubtitleDelay(delayMillis))
    fun setAudioDelay(delayMillis: Long) = reportTrackResult(diagnostics.setAudioDelay(delayMillis))
    fun loadExternalSubtitle(uri: android.net.Uri) {
        viewModelScope.launch {
            subtitles.inspect(uri).fold(
                onSuccess = { subtitle ->
                    reportTrackResult(diagnostics.addExternalSubtitle(subtitle))
                    mutableState.value = state.value.copy(
                        externalSubtitleCandidates = (state.value.externalSubtitleCandidates + subtitle)
                            .distinctBy(ExternalSubtitle::uri),
                    )
                },
                onFailure = { error ->
                    mutableState.value = state.value.copy(
                        trackErrorMessage = error.message ?: "Subtitle loading failed",
                    )
                },
            )
        }
    }
    fun loadExternalSubtitle(subtitle: ExternalSubtitle) = reportTrackResult(diagnostics.addExternalSubtitle(subtitle))
    fun clearTrackError() { mutableState.value = state.value.copy(trackErrorMessage = null) }
    fun toggleFavorite() {
        val id = state.value.current?.mediaId ?: return
        val favorite = !state.value.isFavorite
        mutableState.value = state.value.copy(isFavorite = favorite)
        viewModelScope.launch { mediaRepository.setFavorite(MediaId(id), favorite) }
    }

    fun addBookmark(name: String = "Bookmark") {
        val id = state.value.current?.mediaId ?: return
        viewModelScope.launch {
            organization.saveBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    mediaId = MediaId(id),
                    name = name,
                    positionMillis = state.value.positionMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            ).onFailure { error -> mutableState.value = state.value.copy(errorMessage = error.message) }
        }
    }

    fun renameBookmark(bookmark: Bookmark, name: String) = viewModelScope.launch {
        organization.renameBookmark(bookmark.id, name)
            .onFailure { error -> mutableState.value = state.value.copy(errorMessage = error.message) }
    }

    fun deleteBookmark(bookmark: Bookmark) = viewModelScope.launch {
        organization.deleteBookmark(bookmark.id)
            .onFailure { error -> mutableState.value = state.value.copy(errorMessage = error.message) }
    }

    fun seekToBookmark(bookmark: Bookmark) = seekTo(bookmark.positionMillis)

    private fun publish(player: Player) {
        val previous = mutableState.value
        mutableState.value = PlaybackUiState(
            connected = true,
            current = player.currentMediaItem,
            queue = List(player.mediaItemCount, player::getMediaItemAt),
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            isPlaying = player.isPlaying,
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration.takeIf { it > 0 } ?: 0,
            bufferedPositionMillis = player.bufferedPosition.coerceAtLeast(0),
            volume = player.volume.coerceIn(0f, 1f),
            speed = player.playbackParameters.speed,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            errorMessage = player.playerError?.message,
            isFavorite = previous.isFavorite
                .takeIf { previous.current?.mediaId == player.currentMediaItem?.mediaId }
                ?: false,
            engine = previous.engine,
            capabilities = previous.capabilities,
            audioTracks = previous.audioTracks,
            videoTracks = previous.videoTracks,
            subtitleTracks = previous.subtitleTracks,
            externalSubtitleCandidates = previous.externalSubtitleCandidates,
            trackErrorMessage = previous.trackErrorMessage,
            bookmarks = previous.bookmarks,
            lyrics = previous.lyrics.takeIf { previous.current?.mediaId == player.currentMediaItem?.mediaId },
            currentLyricIndex = previous.lyrics.takeIf {
                previous.current?.mediaId == player.currentMediaItem?.mediaId
            }?.lines?.indexOfLast {
                it.timeMillis != null && it.timeMillis <= player.currentPosition
            } ?: -1,
            abRepeatStartMillis = previous.abRepeatStartMillis,
            abRepeatEndMillis = previous.abRepeatEndMillis,
        )
        val id = player.currentMediaItem?.mediaId
        if (id != bookmarkMediaId) {
            clearAbRepeat()
            bookmarkMediaId = id
            bookmarkJob?.cancel()
            if (id == null) {
                mutableState.value = state.value.copy(bookmarks = emptyList())
            } else {
                bookmarkJob = viewModelScope.launch {
                    organization.observeBookmarks(MediaId(id)).collect { bookmarks ->
                        if (state.value.current?.mediaId == id) {
                            mutableState.value = state.value.copy(bookmarks = bookmarks)
                        }
                    }
                }
            }
        }
        if (id != null && id != loadedFavoriteId) {
            loadedFavoriteId = id
            viewModelScope.launch {
                mediaRepository.media(listOf(MediaId(id))).getOrNull()?.firstOrNull()?.let { media ->
                    if (state.value.current?.mediaId == id) {
                        mutableState.value = state.value.copy(isFavorite = media.isFavorite)
                    }
                }
            }
        }
        if (id != lyricMediaId) {
            lyricMediaId = id
            val uri = player.currentMediaItem?.localConfiguration?.uri
            if (id != null && uri != null) viewModelScope.launch {
                lyricsRepository.autoDetect(uri)?.let { detected ->
                    if (state.value.current?.mediaId == id && state.value.lyrics == null) {
                        mutableState.value = state.value.copy(lyrics = detected)
                    }
                }
            }
        }
        if (id != null && id != detectedSubtitleMediaId) {
            detectedSubtitleMediaId = id
            val uri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
            val title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: id
            viewModelScope.launch {
                val candidates = subtitles.matching(
                    dev.vimivo.core.model.MediaItem(
                        id = MediaId(id),
                        uri = uri,
                        title = title,
                        kind = dev.vimivo.core.model.MediaKind.VIDEO,
                    ),
                )
                if (state.value.current?.mediaId == id) {
                    mutableState.value = state.value.copy(externalSubtitleCandidates = candidates)
                }
            }
        }
    }

    private fun reportTrackResult(result: Result<Unit>) {
        mutableState.value = state.value.copy(trackErrorMessage = result.exceptionOrNull()?.message)
    }

    private fun diagnosticsCapabilities(engine: EngineId): EngineCapabilities = when (engine) {
        EngineId.MEDIA3 -> Media3PlayerEngine.CAPABILITIES
        EngineId.MPV -> LibmpvPlayerEngine.CAPABILITIES
    }

    private fun observeAbRepeat() {
        abRepeatJob?.cancel()
        abRepeatJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val playback = state.value
                val start = playback.abRepeatStartMillis ?: break
                val end = playback.abRepeatEndMillis ?: break
                if (playback.isPlaying && playback.positionMillis >= end) seekTo(start)
            }
        }
    }

    override fun onCleared() {
        player?.removeListener(listener)
        abRepeatJob?.cancel()
        bookmarkJob?.cancel()
    }

    private companion object {
        const val MIN_AB_REPEAT_MILLIS = 1_000L
    }
}

private const val POSITION_TICK_MILLIS = 250L
