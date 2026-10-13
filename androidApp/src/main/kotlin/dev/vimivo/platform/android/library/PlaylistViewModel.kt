package dev.vimivo.platform.android.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimivo.core.model.Playlist
import dev.vimivo.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlaylistViewModel(repository: PlaylistRepository) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}
