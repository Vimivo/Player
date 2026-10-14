@file:Suppress("MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

internal data class DesktopLibraryUiState(
    val roots: List<LibraryRoot> = emptyList(),
    val audio: List<MediaItem> = emptyList(),
    val video: List<MediaItem> = emptyList(),
    val scanStates: Map<String, LibraryScanState> = emptyMap(),
    val search: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

internal data class DesktopPlaybackRequest(val media: MediaItem, val startPositionMillis: Long)

internal class DesktopLibraryController(
    private val database: DesktopDatabase = DesktopDatabase.open(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = DesktopLibraryRepository(database)
    private val personalRepository = DesktopPersonalMediaRepository(database)
    private val coordinator = LibraryScanCoordinator(
        repository,
        DesktopMediaScanner(database),
        DesktopMetadataCoordinator(DesktopMetadataRepository(database), DesktopMediaMetadataExtractor(), scope),
        scope,
    )
    private val mutableState = MutableStateFlow(DesktopLibraryUiState())
    private val scanObservers = mutableMapOf<String, Job>()
    val state: StateFlow<DesktopLibraryUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            repository.generation.collectLatest { refresh() }
        }
    }

    fun setSearch(query: String) {
        mutableState.value = mutableState.value.copy(search = query)
        scope.launch { refresh() }
    }

    fun addFolder() = scope.launch {
        val selected = withContext(Dispatchers.IO) { DesktopFolderPicker.chooseDirectory().getOrThrow() } ?: return@launch
        repository.addRoot(selected.toPath()).onSuccess { root ->
            observeScan(root)
            coordinator.start(root.id)
        }.onFailure(::report)
    }

    fun scan(rootId: String) = coordinator.start(rootId)
    fun cancelScan(rootId: String) = coordinator.cancel(rootId)

    fun removeRoot(rootId: String) = scope.launch {
        coordinator.cancel(rootId)
        repository.removeRoot(rootId).onFailure(::report)
    }

    fun refreshAvailability() = scope.launch {
        repository.refreshAvailability().onFailure(::report)
    }

    suspend fun preparePlayback(media: MediaItem): Result<DesktopPlaybackRequest> = withContext(Dispatchers.IO) {
        runCatching {
            require(media.uri.startsWith("file:")) { "Only indexed local files can be opened from the library" }
            require(File(URI(media.uri)).isFile) { "The indexed media file is unavailable" }
            DesktopPlaybackRequest(media, resumePositionMillis(media, personalRepository.position(media.id)))
        }.onFailure(::report)
    }

    suspend fun observePlayback(playback: StateFlow<PlaybackState>) {
        var mediaId: String? = null
        var persistable = false
        var historyRecorded = false
        var completionSaved = false
        var lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
        playback.collect { playbackState ->
            val media = playbackState.media
            if (media?.id?.value != mediaId) {
                mediaId = media?.id?.value
                persistable = media != null && withContext(Dispatchers.IO) { personalRepository.containsMedia(media.id) }
                historyRecorded = false
                completionSaved = false
                lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
            }
            if (media == null || !persistable) return@collect
            if (playbackState.status == PlaybackStatus.Playing && !historyRecorded) {
                withContext(Dispatchers.IO) {
                    personalRepository.recordPlayback(media.id, playbackState.positionMillis, completed = false)
                }
                historyRecorded = true
            }
            val completed = playbackState.status == PlaybackStatus.Completed
            val shouldPersist = when {
                completed && !completionSaved -> true
                playbackState.status == PlaybackStatus.Paused -> playbackState.positionMillis != lastSavedPosition
                playbackState.status == PlaybackStatus.Playing ->
                    playbackState.positionMillis - lastSavedPosition >= PLAYBACK_SAVE_INTERVAL_MILLIS
                else -> false
            }
            if (shouldPersist) {
                withContext(Dispatchers.IO) {
                    personalRepository.savePosition(
                        media.id,
                        playbackState.positionMillis,
                        playbackState.durationMillis,
                        completed,
                    )
                    if (completed) personalRepository.markPlaybackCompleted(media.id, playbackState.positionMillis)
                }
                lastSavedPosition = playbackState.positionMillis
                completionSaved = completed
            }
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val query = mutableState.value.search.trim()
            val roots = repository.roots()
            roots.forEach(::observeScan)
            mutableState.value = mutableState.value.copy(
                roots = roots,
                audio = repository.media(MediaKind.AUDIO, query),
                video = repository.media(MediaKind.VIDEO, query),
                loading = false,
                error = null,
            )
        }.onFailure(::report)
    }

    private fun observeScan(root: LibraryRoot) {
        if (scanObservers[root.id]?.isActive == true) return
        scanObservers[root.id] = scope.launch {
            coordinator.state(root.id).collectLatest { scanState ->
                mutableState.value = mutableState.value.copy(
                    scanStates = mutableState.value.scanStates + (root.id to scanState),
                )
            }
        }
    }

    private fun report(error: Throwable) {
        mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: "Library operation failed")
    }

    override fun close() {
        scope.cancel()
        database.close()
    }

    companion object {
        private const val PLAYBACK_SAVE_INTERVAL_MILLIS = 10_000L

        internal fun resumePositionMillis(media: MediaItem, saved: ResumePosition?): Long {
            if (saved == null || saved.completed || saved.positionMillis < 10_000) return 0
            val duration = saved.durationMillis ?: media.durationMillis
            if (duration != null && (saved.positionMillis >= duration - 15_000 || saved.positionMillis * 100 >= duration * 95)) return 0
            val isLongAudio = media.kind == MediaKind.AUDIO && (duration ?: 0) >= 20 * 60_000
            return if (media.kind == MediaKind.VIDEO || isLongAudio) saved.positionMillis else 0
        }
    }
}
