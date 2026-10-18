package dev.vimivo.desktop.library

import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * What the player persists: resume positions, play history, the saved queue, bookmarks and player settings.
 * It reads and writes the database directly and never indexes, scans or loads the library, so opening a file
 * from the OS does not pay for the library. The library window refreshes itself from [changes].
 */
internal class DesktopPlaybackStore(database: DesktopDatabase) {
    private val repository = DesktopLibraryRepository(database)
    private val personalRepository = DesktopPersonalMediaRepository(database)
    private val queueRepository = DesktopPlaybackQueueRepository(database)
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGE_BUFFER)
    private val mutableErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = CHANGE_BUFFER)

    /** Emits after anything the library shows (history, completion, bookmarks, settings) changed. */
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    /** Failures, for the library window's error banner; also recorded in the local diagnostic buffer. */
    val errors: SharedFlow<Throwable> = mutableErrors.asSharedFlow()

    fun uiSetting(key: String): String? = personalRepository.setting(key)

    suspend fun setUiSetting(key: String, value: String) = mutate { personalRepository.putSetting(key, value) }

    fun bookmarks(mediaId: MediaId): List<Bookmark> = personalRepository.bookmarks(mediaId)

    suspend fun addBookmark(mediaId: MediaId, positionMillis: Long, label: String?) =
        mutate { personalRepository.addBookmark(mediaId, positionMillis, label) }

    suspend fun renameBookmark(id: String, label: String) = mutate { personalRepository.renameBookmark(id, label) }

    suspend fun deleteBookmark(id: String) = mutate { personalRepository.deleteBookmark(id) }

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
            val ids = stored.items.map(StoredQueueItem::mediaId)
            val mediaById = repository.media(ids, includeUnavailable = true).associateBy { it.id }
            val entries = stored.items.map { storedItem -> storedItem to mediaById[storedItem.mediaId] }
            val queueItem = { pair: Pair<StoredQueueItem, MediaItem?> ->
                val media = pair.second ?: MediaItem(pair.first.mediaId, "", "", MediaKind.AUDIO, isAvailable = false)
                QueueItem(pair.first.queueId, media)
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

    suspend fun preparePlayback(media: MediaItem): Result<DesktopPlaybackRequest> = withContext(Dispatchers.IO) {
        runCatching {
            require(media.uri.startsWith("file:")) { "Only local files can resume" }
            require(File(URI(media.uri)).isFile) { "The media file is unavailable" }
            val resumeEnabled = personalRepository.setting("resume_playback")?.toBooleanStrictOrNull() ?: true
            val pref = personalRepository.mediaPreference(media.id)
            val pos = if (resumeEnabled && (pref?.resumeEligible != false)) {
                val saved = personalRepository.position(media.id) ?: personalRepository.externalPosition(media.uri)
                DesktopLibraryController.resumePositionMillis(media, saved)
            } else {
                0L
            }
            DesktopPlaybackRequest(media, pos)
        }.onFailure(::report)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // Moved unchanged from DesktopLibraryController.
    suspend fun observePlayback(playback: StateFlow<PlaybackState>) {
        var mediaId: String? = null
        var persistable = false
        var external = false
        var historyRecorded = false
        var completionSaved = false
        var lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
        var previousState: PlaybackState? = null
        playback.collect { playbackState ->
            val media = playbackState.media
            if (media?.id?.value != mediaId) {
                val leaving = previousState
                val leavingMedia = leaving?.media
                if ((persistable || external) && leavingMedia != null) {
                    withContext(Dispatchers.IO) {
                        savePlaybackPosition(
                            leavingMedia,
                            leaving,
                            leaving.status == PlaybackStatus.Completed,
                            external
                        )
                    }
                }
                mediaId = media?.id?.value
                persistable = media != null &&
                    withContext(Dispatchers.IO) { personalRepository.containsMedia(media.id) }
                // Files opened outside the library still resume, but stay out of the library's history.
                external = !persistable && media?.uri?.startsWith("file:") == true
                historyRecorded = false
                completionSaved = false
                lastSavedPosition = -PLAYBACK_SAVE_INTERVAL_MILLIS
            }
            if (media == null || (!persistable && !external)) {
                previousState = playbackState
                return@collect
            }
            if (persistable && playbackState.status == PlaybackStatus.Playing && !historyRecorded) {
                withContext(Dispatchers.IO) {
                    personalRepository.recordPlayback(media.id, playbackState.positionMillis, completed = false)
                }
                historyRecorded = true
                mutableChanges.tryEmit(Unit)
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
                    savePlaybackPosition(media, playbackState, completed, external)
                    if (completed && !external) {
                        personalRepository.markPlaybackCompleted(
                            media.id,
                            playbackState.positionMillis
                        )
                    }
                }
                lastSavedPosition = playbackState.positionMillis
                completionSaved = completed
                if (completed) mutableChanges.tryEmit(Unit)
            }
            previousState = playbackState
        }
    }

    private fun savePlaybackPosition(media: MediaItem, playback: PlaybackState, completed: Boolean, external: Boolean) {
        if (external) {
            personalRepository.saveExternalPosition(
                media.uri,
                playback.positionMillis,
                playback.durationMillis,
                completed
            )
        } else {
            personalRepository.savePosition(media.id, playback.positionMillis, playback.durationMillis, completed)
        }
    }

    private suspend fun mutate(block: () -> Unit) = withContext(Dispatchers.IO) {
        runCatching(block).onSuccess { mutableChanges.tryEmit(Unit) }.onFailure(::report)
    }

    private fun report(error: Throwable) {
        DesktopDiagnosticExporter.defaultBuffer.recordError("PLAYBACK_STORE", error)
        mutableErrors.tryEmit(error)
    }

    private companion object {
        const val PLAYBACK_SAVE_INTERVAL_MILLIS = 10_000L
        const val CHANGE_BUFFER = 16
    }
}
