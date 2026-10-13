package dev.vimivo.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimivo.core.model.MediaItem
import dev.vimivo.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val continueWatching: List<MediaItem> = emptyList(),
    val recentlyPlayed: List<MediaItem> = emptyList(),
    val recentlyAddedMusic: List<MediaItem> = emptyList(),
    val recentlyAddedVideos: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel(private val repository: MediaRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibrary().collect { media ->
                mutableState.update {
                    it.copy(
                        continueWatching = media.filter { item ->
                            item.kind == dev.vimivo.core.model.MediaKind.VIDEO && item.lastPlaybackPositionMillis > 0
                        }.sortedByDescending(MediaItem::lastPlayedAtEpochMillis).take(12),
                        recentlyPlayed = media.filter { item -> item.lastPlayedAtEpochMillis != null }
                            .sortedByDescending(MediaItem::lastPlayedAtEpochMillis).take(12),
                        recentlyAddedMusic = media
                            .filter { item -> item.kind == dev.vimivo.core.model.MediaKind.AUDIO }
                            .sortedByDescending(MediaItem::dateAddedEpochSeconds).take(12),
                        recentlyAddedVideos = media
                            .filter { item -> item.kind == dev.vimivo.core.model.MediaKind.VIDEO }
                            .sortedByDescending(MediaItem::dateAddedEpochSeconds).take(12),
                        favorites = media.filter(MediaItem::isFavorite).take(12),
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.refresh().fold(
                onSuccess = { mutableState.update { it.copy(isLoading = false) } },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Library scan failed",
                        )
                    }
                },
            )
        }
    }
}
