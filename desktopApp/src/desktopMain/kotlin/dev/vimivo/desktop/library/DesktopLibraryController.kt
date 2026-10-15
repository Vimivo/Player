@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "ImportOrdering", "Indentation",
    "MaximumLineLength", "MaxLineLength", "ParameterListWrapping", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.core.model.Playlist
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueItem
import java.io.File
import java.net.URI
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

internal data class DesktopLibraryUiState(
    val roots: List<LibraryRoot> = emptyList(),
    val audio: List<MediaItem> = emptyList(),
    val video: List<MediaItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val personalMedia: Map<MediaId, MediaItem> = emptyMap(),
    val favorites: List<MediaItem> = emptyList(),
    val history: List<DesktopHistoryItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val scanStates: Map<String, LibraryScanState> = emptyMap(),
    val search: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

internal data class DesktopPlaybackRequest(val media: MediaItem, val startPositionMillis: Long)
internal data class DesktopHistoryItem(val media: MediaItem, val history: PlaybackHistory)

internal class DesktopLibraryController(
    private val database: DesktopDatabase = DesktopDatabase.open(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = DesktopLibraryRepository(database)
    private val personalRepository = DesktopPersonalMediaRepository(database)
    private val queueRepository = DesktopPlaybackQueueRepository(database)
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

    fun createPlaylist(name: String) = mutatePersonalData { personalRepository.createPlaylist(name) }
    fun renamePlaylist(id: String, name: String) = mutatePersonalData { personalRepository.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = mutatePersonalData { personalRepository.deletePlaylist(id) }
    fun duplicatePlaylist(id: String) = mutatePersonalData { personalRepository.duplicatePlaylist(id) }
    fun addToPlaylist(playlistId: String, media: MediaItem) =
        mutatePersonalData { personalRepository.addToPlaylist(playlistId, media.id) }
    fun removeFromPlaylist(playlistId: String, mediaId: MediaId) =
        mutatePersonalData { personalRepository.removeFromPlaylist(playlistId, mediaId) }
    fun movePlaylistItem(playlistId: String, mediaId: MediaId, position: Int) =
        mutatePersonalData { personalRepository.movePlaylistItem(playlistId, mediaId, position) }
    fun toggleFavorite(media: MediaItem) = mutatePersonalData {
        personalRepository.setFavorite(FavoriteTarget.MEDIA, media.id.value, !media.isFavorite)
    }
    fun removeHistory(mediaId: MediaId) = mutatePersonalData { personalRepository.removeHistory(mediaId) }
    fun clearHistory() = mutatePersonalData { personalRepository.clearHistory() }
    fun addBookmark(mediaId: MediaId, positionMillis: Long, label: String?) =
        mutatePersonalData { personalRepository.addBookmark(mediaId, positionMillis, label) }
    fun renameBookmark(id: String, label: String) = mutatePersonalData { personalRepository.renameBookmark(id, label) }
    fun deleteBookmark(id: String) = mutatePersonalData { personalRepository.deleteBookmark(id) }

    /**
     * Persists indexed media only; transient file and network entries cannot be restored safely, so they are
     * filtered while preserving occurrence identities, ordering, and the nearest active entry.
     */
    suspend fun saveQueue(snapshot: PlaybackQueueSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val indexed = queueRepository.indexedMediaIds(snapshot.playbackItems.map { it.media.id })
            val persistable = snapshot.retainOnly { it.id in indexed }
            if (persistable.playbackItems.isEmpty()) queueRepository.clear() else queueRepository.save(persistable)
        }.onFailure(::report)
    }

    /**
     * Restores a paused queue. Temporarily unavailable media (for example an unmounted drive) is kept and marked
     * unavailable so the queue can skip it; media removed from the library is dropped. Corrupt state is discarded.
     */
    suspend fun restoredQueue(): PlaybackQueueSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val stored = queueRepository.load() ?: return@runCatching null
            val mediaById = repository.media(stored.items.map(StoredQueueItem::mediaId), includeUnavailable = true).associateBy { it.id }
            val entries = stored.items.map { storedItem -> storedItem to mediaById[storedItem.mediaId] }
            val queueItem = { pair: Pair<StoredQueueItem, MediaItem?> ->
                QueueItem(pair.first.queueId, pair.second ?: MediaItem(pair.first.mediaId, "", "", MediaKind.AUDIO, isAvailable = false))
            }
            val snapshot = PlaybackQueueSnapshot(
                sourceItems = entries.sortedBy { it.first.sourcePosition }.map(queueItem),
                playbackItems = entries.sortedBy { it.first.playbackPosition }.map(queueItem),
                currentQueueId = stored.currentQueueId,
                shuffleEnabled = stored.shuffleEnabled,
                repeatMode = stored.repeatMode,
                sourceLabel = stored.sourceLabel,
            ).retainOnly { it.id in mediaById }
            snapshot.takeIf { it.playbackItems.isNotEmpty() }
        }.getOrElse { error ->
            report(IllegalStateException("The saved playback queue could not be restored and was discarded", error))
            runCatching { queueRepository.clear() }
            null
        }
    }

    suspend fun playlistMedia(playlistId: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val playlist = personalRepository.playlists().firstOrNull { it.id == playlistId }
                ?: error("Playlist was not found")
            repository.media(playlist.items)
        }.onFailure(::report)
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
        var previousState: PlaybackState? = null
        playback.collect { playbackState ->
            val media = playbackState.media
            if (media?.id?.value != mediaId) {
                val leaving = previousState
                val leavingMedia = leaving?.media
                if (persistable && leavingMedia != null) {
                    withContext(Dispatchers.IO) {
                        personalRepository.savePosition(
                            leavingMedia.id,
                            leaving.positionMillis,
                            leaving.durationMillis,
                            leaving.status == PlaybackStatus.Completed,
                        )
                    }
                }
                mediaId = media?.id?.value
                persistable = media != null && withContext(Dispatchers.IO) { personalRepository.containsMedia(media.id) }
                historyRecorded = false
                completionSaved = false
                lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
            }
            if (media == null || !persistable) {
                previousState = playbackState
                return@collect
            }
            if (playbackState.status == PlaybackStatus.Playing && !historyRecorded) {
                withContext(Dispatchers.IO) {
                    personalRepository.recordPlayback(media.id, playbackState.positionMillis, completed = false)
                }
                historyRecorded = true
                refresh()
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
                if (completed) refresh()
            }
            previousState = playbackState
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val query = mutableState.value.search.trim()
            val roots = repository.roots()
            val playlists = personalRepository.playlists()
            val favoriteIds = personalRepository.favoriteIds(FavoriteTarget.MEDIA).map(::MediaId)
            val favorites = repository.media(favoriteIds).map { it.copy(isFavorite = true) }
            val favoriteIdSet = favoriteIds.mapTo(mutableSetOf(), MediaId::value)
            val history = personalRepository.history()
            val personalMedia = repository.media(
                playlists.flatMap(Playlist::items) + favoriteIds + history.map(PlaybackHistory::mediaId),
            ).associateBy { it.id }
            roots.forEach(::observeScan)
            mutableState.value = mutableState.value.copy(
                roots = roots,
                audio = repository.media(MediaKind.AUDIO, query).map { it.copy(isFavorite = it.id.value in favoriteIdSet) },
                video = repository.media(MediaKind.VIDEO, query).map { it.copy(isFavorite = it.id.value in favoriteIdSet) },
                playlists = playlists,
                personalMedia = personalMedia,
                favorites = favorites,
                history = history.mapNotNull { entry -> personalMedia[entry.mediaId]?.let { DesktopHistoryItem(it, entry) } },
                bookmarks = personalRepository.allBookmarks(),
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

    private fun mutatePersonalData(block: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { runCatching(block).onFailure(::report) }
            .onSuccess { refresh() }
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
