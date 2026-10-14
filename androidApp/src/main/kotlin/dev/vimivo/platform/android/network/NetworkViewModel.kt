@file:Suppress("ArgumentListWrapping", "MaxLineLength", "MaximumLineLength", "TooManyFunctions", "Wrapping")

package dev.vimivo.platform.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.platform.android.playback.PlaybackController
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

private val Context.networkDataStore by preferencesDataStore("vimivo_network")

data class NetworkHistory(val recent: List<String>, val favorites: Set<String>)

class NetworkHistoryStore(private val context: Context) {
    suspend fun load(): NetworkHistory {
        val preferences = context.networkDataStore.data.first()
        return NetworkHistory(
            recent = preferences[RECENT_ORDERED]?.decodeUrlList()
                ?: preferences[LEGACY_RECENT].orEmpty().sorted(),
            favorites = preferences[FAVORITES].orEmpty(),
        )
    }

    suspend fun save(value: NetworkHistory) {
        context.networkDataStore.edit {
            it[RECENT_ORDERED] = value.recent.encodeUrlList()
            it[FAVORITES] = value.favorites
            it.remove(LEGACY_RECENT)
        }
    }

    private companion object {
        val RECENT_ORDERED = stringPreferencesKey("recent_streams_ordered")
        val LEGACY_RECENT = stringSetPreferencesKey("recent_streams")
        val FAVORITES = stringSetPreferencesKey("favorite_streams")
    }
}

enum class StreamPlaybackStatus { IDLE, CONNECTING, BUFFERING, PLAYING, PAUSED, ERROR }

data class NetworkUiState(
    val url: String = "",
    val recent: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val status: StreamPlaybackStatus = StreamPlaybackStatus.IDLE,
    val currentUrl: String? = null,
    val isLive: Boolean = false,
    val isSeekable: Boolean = false,
    val retryAttempt: Int = 0,
    val errorMessage: String? = null,
)

class NetworkViewModel(
    context: Context,
    private val playback: PlaybackController,
    settingsRepository: SettingsRepository,
    private val historyStore: NetworkHistoryStore,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val mutableState = MutableStateFlow(NetworkUiState())
    private var observedPlayer: Player? = null
    private var reconnectJob: Job? = null
    private var reconnectPending = false
    private var autoReconnect = true
    val state: StateFlow<NetworkUiState> = mutableState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
        override fun onPlayerError(error: PlaybackException) {
            mutableState.update { it.copy(status = StreamPlaybackStatus.ERROR, errorMessage = error.userMessage()) }
            if (autoReconnect && error.isReconnectable()) scheduleReconnect()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (reconnectPending) scheduleReconnect(immediate = true)
        }

        override fun onLost(network: Network) {
            if (state.value.currentUrl != null) {
                mutableState.update { it.copy(errorMessage = "Network connection was interrupted") }
            }
        }
    }

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { settingsRepository.settings.collect { autoReconnect = it.autoReconnectStreams } }
        viewModelScope.launch {
            playback.controller.collect { player ->
                observedPlayer?.removeListener(playerListener)
                observedPlayer = player
                player?.addListener(playerListener)
                if (player != null) publish(player)
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
    }

    fun setUrl(value: String) = mutableState.update { it.copy(url = value, errorMessage = null) }

    fun open(url: String = state.value.url) {
        val normalized = validateNetworkStreamUrl(url).getOrElse { error ->
            mutableState.update { it.copy(errorMessage = error.message) }
            return
        }
        reconnectJob?.cancel()
        reconnectPending = false
        mutableState.update {
            it.copy(
                url = normalized,
                currentUrl = normalized,
                status = StreamPlaybackStatus.CONNECTING,
                retryAttempt = 0,
                errorMessage = null,
            )
        }
        playback.play(normalized.toMediaItem())
        viewModelScope.launch {
            val recent = (listOf(normalized) + state.value.recent).distinct().take(MAX_RECENT)
            mutableState.update { it.copy(recent = recent) }
            persist(recent, state.value.favorites)
        }
    }

    fun retry() {
        reconnectJob?.cancel()
        reconnectPending = false
        mutableState.update { it.copy(status = StreamPlaybackStatus.CONNECTING, errorMessage = null) }
        playback.retry()
    }

    fun togglePlayback() {
        observedPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun toggleFavorite(url: String) = viewModelScope.launch {
        val favorites = state.value.favorites.toMutableSet().apply { if (!add(url)) remove(url) }
        mutableState.update { it.copy(favorites = favorites) }
        persist(state.value.recent, favorites)
    }

    fun removeRecent(url: String) = viewModelScope.launch {
        val recent = state.value.recent.filterNot { it == url }
        mutableState.update { it.copy(recent = recent) }
        persist(recent, state.value.favorites)
    }

    fun clearHistory() = viewModelScope.launch {
        mutableState.update { it.copy(recent = emptyList()) }
        persist(emptyList(), state.value.favorites)
    }

    private fun publish(player: Player) {
        val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (current?.let(::isNetworkUrl) != true) return
        mutableState.update {
            it.copy(
                currentUrl = current,
                status = when {
                    player.playerError != null -> StreamPlaybackStatus.ERROR
                    player.playbackState == Player.STATE_BUFFERING -> StreamPlaybackStatus.BUFFERING
                    player.playbackState == Player.STATE_IDLE -> StreamPlaybackStatus.CONNECTING
                    player.isPlaying -> StreamPlaybackStatus.PLAYING
                    else -> StreamPlaybackStatus.PAUSED
                },
                isLive = player.isCurrentMediaItemLive || (
                    player.duration == C.TIME_UNSET &&
                        !player.isCurrentMediaItemSeekable &&
                        player.playbackState == Player.STATE_READY
                    ),
                isSeekable = player.isCurrentMediaItemSeekable,
                errorMessage = player.playerError?.userMessage(),
            )
        }
    }

    private fun scheduleReconnect(immediate: Boolean = false) {
        if (state.value.retryAttempt >= MAX_RECONNECT_ATTEMPTS) return
        if (!isConnected()) {
            reconnectPending = true
            return
        }
        reconnectPending = false
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val attempt = state.value.retryAttempt + 1
            mutableState.update { it.copy(retryAttempt = attempt) }
            if (!immediate) delay(RECONNECT_BASE_DELAY_MILLIS * attempt)
            playback.retry()
        }
    }

    private fun isConnected(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun load() {
        val history = historyStore.load()
        mutableState.update { it.copy(recent = history.recent, favorites = history.favorites) }
    }

    private suspend fun persist(recent: List<String>, favorites: Set<String>) {
        historyStore.save(NetworkHistory(recent, favorites))
    }

    override fun onCleared() {
        observedPlayer?.removeListener(playerListener)
        reconnectJob?.cancel()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
    }

    private companion object {
        const val MAX_RECENT = 50
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MILLIS = 1_500L
    }
}

internal fun validateNetworkStreamUrl(input: String): Result<String> = runCatching {
    val value = input.trim()
    require(value.isNotEmpty()) { "Enter a media URL" }
    val uri = java.net.URI(value)
    require(uri.scheme?.lowercase() in setOf("http", "https", "rtsp", "rtsps")) { "Use an HTTP, HTTPS, RTSP, or RTSPS media URL" }
    require(!uri.host.isNullOrBlank()) { "The URL must include a host" }
    require(uri.rawUserInfo.isNullOrBlank()) { "URLs containing embedded credentials are not stored" }
    value
}

private fun String.toMediaItem() = MediaItem(
    id = MediaId(this),
    uri = this,
    title = this,
    kind = MediaKind.STREAM,
    mimeType = inferredStreamMimeType(this),
)

internal fun inferredStreamMimeType(url: String): String? = when {
    url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
    url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "application/dash+xml"
    else -> null
}

private fun PlaybackException.isReconnectable() = isReconnectableErrorCode(errorCode)

internal fun isReconnectableErrorCode(errorCode: Int) = errorCode in setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
)

private fun PlaybackException.userMessage(): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Could not connect to the stream"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "The stream connection timed out"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server rejected the stream request"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "The stream manifest is invalid"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "This stream manifest is unsupported"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The stream is unavailable or has expired"
    else -> message ?: "Stream playback failed"
}

private fun isNetworkUrl(value: String) = value.startsWith("https://") || value.startsWith("http://") || value.startsWith("rtsp://") || value.startsWith("rtsps://")
private fun List<String>.encodeUrlList() = JSONArray(this).toString()
private fun String.decodeUrlList(): List<String> = runCatching {
    JSONArray(this).let { array -> List(array.length()) { index -> array.getString(index) } }
}.getOrDefault(emptyList())
