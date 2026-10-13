package dev.vimivo.platform.android.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.Playlist
import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    repository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val mutableSelectedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val selectedItems = mutableSelectedItems.asStateFlow()

    fun load(playlist: Playlist) {
        viewModelScope.launch {
            mutableSelectedItems.value = mediaRepository.media(playlist.items).getOrDefault(emptyList())
        }
    }
}
