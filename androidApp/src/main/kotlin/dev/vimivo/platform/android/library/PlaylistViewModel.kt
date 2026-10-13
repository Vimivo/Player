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
    private val repository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    private val documents: PlaylistDocuments,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val mutableSelectedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val selectedItems = mutableSelectedItems.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableSearchResults = MutableStateFlow<List<Playlist>>(emptyList())
    val searchResults = mutableSearchResults.asStateFlow()

    fun load(playlist: Playlist) {
        viewModelScope.launch {
            mutableSelectedItems.value = mediaRepository.media(playlist.items).getOrDefault(emptyList())
        }
    }

    fun create(name: String, items: List<MediaItem> = emptyList()) = launchMutation {
        repository.create(name, items.map(MediaItem::id)).map { Unit }
    }

    fun rename(playlist: Playlist, name: String) = launchMutation { repository.rename(playlist.id, name) }
    fun delete(playlist: Playlist) = launchMutation { repository.delete(playlist.id) }
    fun add(playlist: Playlist, items: List<MediaItem>) = launchMutation {
        repository.add(playlist.id, items.map(MediaItem::id))
    }
    fun remove(playlist: Playlist, position: Int) = launchMutation { repository.remove(playlist.id, position) }
    fun move(playlist: Playlist, from: Int, to: Int) = launchMutation { repository.move(playlist.id, from, to) }
    fun duplicate(playlist: Playlist, name: String) = launchMutation { repository.duplicate(playlist.id, name).map { Unit } }
    fun import(uri: android.net.Uri) = launchMutation { documents.import(uri).map { Unit } }
    fun export(uri: android.net.Uri, playlist: Playlist) = launchMutation { documents.export(uri, playlist) }

    fun search(text: String) {
        if (text.isBlank()) {
            mutableSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            repository.findByName(text).fold(
                onSuccess = { mutableSearchResults.value = it },
                onFailure = { mutableError.value = it.message ?: "Playlist search failed" },
            )
        }
    }

    private fun launchMutation(block: suspend () -> Result<Unit>) = viewModelScope.launch {
        mutableError.value = block().exceptionOrNull()?.message
    }
}
