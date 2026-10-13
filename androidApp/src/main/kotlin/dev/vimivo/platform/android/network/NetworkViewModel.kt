package dev.vimivo.platform.android.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.platform.android.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.networkDataStore by preferencesDataStore("vimivo_network")

data class NetworkUiState(
    val url: String = "",
    val recent: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

class NetworkViewModel(
    private val context: Context,
    private val playback: PlaybackController,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = mutableState.asStateFlow()

    init { viewModelScope.launch { load() } }

    fun setUrl(value: String) = mutableState.update { it.copy(url = value, errorMessage = null) }

    fun open(url: String = state.value.url) {
        val normalized = url.trim()
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            mutableState.update { it.copy(errorMessage = "Enter an HTTP or HTTPS media URL") }
            return
        }
        playback.play(MediaItem(MediaId(normalized), normalized, normalized, MediaKind.STREAM))
        viewModelScope.launch {
            val recent = (listOf(normalized) + state.value.recent).distinct().take(20)
            mutableState.update { it.copy(recent = recent, url = "") }
            persist(recent, state.value.favorites)
        }
    }

    fun toggleFavorite(url: String) = viewModelScope.launch {
        val favorites = state.value.favorites.toMutableSet().apply { if (!add(url)) remove(url) }
        mutableState.update { it.copy(favorites = favorites) }
        persist(state.value.recent, favorites)
    }

    private suspend fun load() {
        val preferences = context.networkDataStore.data.first()
        mutableState.value = NetworkUiState(
            recent = preferences[RECENT].orEmpty().sorted(),
            favorites = preferences[FAVORITES].orEmpty(),
        )
    }

    private suspend fun persist(recent: List<String>, favorites: Set<String>) {
        context.networkDataStore.edit {
            it[RECENT] = recent.toSet()
            it[FAVORITES] = favorites
        }
    }

    private companion object {
        val RECENT = stringSetPreferencesKey("recent_streams")
        val FAVORITES = stringSetPreferencesKey("favorite_streams")
    }
}
