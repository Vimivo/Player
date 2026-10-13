@file:Suppress("UnstableApiUsage")

package dev.vimivo.platform.android.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.vimivo.core.model.MediaId
import dev.vimivo.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val connected: Boolean = false,
    val current: MediaItem? = null,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val bufferedPositionMillis: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false,
)

class PlaybackViewModel(
    private val connection: PlaybackController,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaybackUiState())
    private var player: Player? = null
    private var loadedFavoriteId: String? = null
    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

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
    }

    fun togglePlayback() = player?.let { if (it.isPlaying) it.pause() else it.play() }
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
    fun toggleFavorite() {
        val id = state.value.current?.mediaId ?: return
        val favorite = !state.value.isFavorite
        mutableState.value = state.value.copy(isFavorite = favorite)
        viewModelScope.launch { mediaRepository.setFavorite(MediaId(id), favorite) }
    }

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
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            errorMessage = player.playerError?.message,
            isFavorite = previous.isFavorite
                .takeIf { previous.current?.mediaId == player.currentMediaItem?.mediaId }
                ?: false,
        )
        val id = player.currentMediaItem?.mediaId
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
    }

    override fun onCleared() {
        player?.removeListener(listener)
    }
}
