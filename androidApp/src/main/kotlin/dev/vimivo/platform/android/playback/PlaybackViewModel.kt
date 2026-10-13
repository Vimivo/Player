@file:Suppress("UnstableApiUsage")

package dev.vimivo.platform.android.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
)

class PlaybackViewModel(private val connection: PlaybackController) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaybackUiState())
    private var player: Player? = null
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

    private fun publish(player: Player) {
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
        )
    }

    override fun onCleared() {
        player?.removeListener(listener)
    }
}
