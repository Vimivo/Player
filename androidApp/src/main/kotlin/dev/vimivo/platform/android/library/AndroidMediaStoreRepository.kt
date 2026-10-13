@file:Suppress("ArgumentListWrapping", "Indentation", "LongParameterList", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.library

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.vimivo.core.model.LibraryCategory
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibraryPage
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.LibraryStatus
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaPermissionAccess
import dev.vimivo.core.model.MediaPermissionState
import dev.vimivo.core.model.MediaSource
import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.platform.android.database.CategoryRow
import dev.vimivo.platform.android.database.FolderExclusionEntity
import dev.vimivo.platform.android.database.LibraryDao
import dev.vimivo.platform.android.database.MediaEntity
import dev.vimivo.platform.android.database.PlaybackHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidMediaStoreRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safScanner: SafMediaScanner,
    private val permissions: MediaPermissionPolicy,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : MediaRepository {
    private val mutableStatus = MutableStateFlow<LibraryStatus>(LibraryStatus.Idle)
    private val mutableChanges = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)
    private var observerScan: Job? = null
    private val scanMutex = Mutex()
    override val status: Flow<LibraryStatus> = mutableStatus.asStateFlow()
    override val changes: Flow<Long> = mutableChanges.asSharedFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            observerScan?.cancel()
            observerScan = scope.launch {
                delay(OBSERVER_DEBOUNCE_MILLIS)
                scan(full = false)
            }
        }
    }

    init {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        mutableChanges.tryEmit(0)
    }

    override fun observeLibrary(): Flow<List<MediaItem>> = dao.observePreview().map { rows -> rows.map(MediaEntity::toModel) }

    override suspend fun refresh(): Result<Unit> = scan(full = true)

    private suspend fun scan(full: Boolean): Result<Unit> = runCatching {
        scanMutex.withLock {
            withContext(Dispatchers.IO) {
            val permission = permissions.current()
            if (permission.audio == MediaPermissionAccess.DENIED && permission.video == MediaPermissionAccess.DENIED) {
                mutableStatus.value = LibraryStatus.PermissionRequired(permission)
            }
            mediaStoreScanner.scan(permission, full) { source, processed ->
                mutableStatus.value = LibraryStatus.Scanning(source, processed)
            }
            safScanner.rescanPersistedTrees { source, processed ->
                mutableStatus.value = LibraryStatus.Scanning(source, processed)
            }
            val count = dao.availableCount()
            val finishedAt = nowMillis()
            mutableStatus.value = LibraryStatus.Ready(count, finishedAt)
            mutableChanges.emit(finishedAt)
            }
        }
    }.onFailure { error ->
        mutableStatus.value = LibraryStatus.Failed(error.message ?: "Library scan failed")
    }

    override suspend fun page(query: LibraryQuery): Result<LibraryPage> = runCatching {
        withContext(Dispatchers.IO) {
            val items = dao.page(LibraryQueryBuilder.page(query)).map(MediaEntity::toModel)
            LibraryPage(items, query.offset, dao.count(LibraryQueryBuilder.count(query)))
        }
    }

    override suspend fun categories(
        kind: MediaKind,
        field: LibraryCategoryField,
    ): Result<List<LibraryCategory>> = runCatching {
        withContext(Dispatchers.IO) {
            dao.categories(LibraryQueryBuilder.categories(kind, field)).map(CategoryRow::toModel)
        }
    }

    override suspend fun permissionState(): MediaPermissionState = permissions.current()

    override suspend fun importFile(uri: String): Result<MediaItem> = runCatching {
        withContext(Dispatchers.IO) {
            safScanner.importFile(Uri.parse(uri)).toModel().also { mutableChanges.emit(nowMillis()) }
        }
    }

    override suspend fun importFolder(uri: String): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            safScanner.importTree(Uri.parse(uri)) { count ->
                mutableStatus.value = LibraryStatus.Scanning("Selected folder", count)
            }.also {
                val finishedAt = nowMillis()
                mutableStatus.value = LibraryStatus.Ready(dao.availableCount(), finishedAt)
                mutableChanges.emit(finishedAt)
            }
        }
    }

    override suspend fun removeMissing(): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            dao.mediaBySource(MediaSource.SAF_FILE.name).forEach { media ->
                val available = runCatching {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(media.uri), "r")?.use { true } ?: false
                }.getOrDefault(false)
                if (!available) dao.markUnavailable(media.id)
            }
            scan(full = true).getOrThrow()
            dao.deleteUnavailable().also { mutableChanges.emit(nowMillis()) }
        }
    }

    override suspend fun setFavorite(id: MediaId, favorite: Boolean): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { dao.setFavorite(id.value, favorite) }
        mutableChanges.emit(nowMillis())
    }

    override suspend fun recordPlayback(
        id: MediaId,
        positionMillis: Long,
        completed: Boolean,
    ): Result<Unit> = runCatching {
        require(positionMillis >= 0)
        withContext(Dispatchers.IO) {
            val now = nowMillis()
            val previous = dao.history(id.value)
            val resetPosition = if (completed) 0 else positionMillis
            dao.updatePlayback(id.value, resetPosition, now)
            dao.upsertHistory(
                PlaybackHistoryEntity(
                    mediaId = id.value,
                    positionMillis = resetPosition,
                    lastPlayedAtEpochMillis = now,
                    playCount = (previous?.playCount ?: 0) + if (completed) 1 else 0,
                    completed = completed,
                ),
            )
            mutableChanges.emit(now)
        }
    }

    override suspend fun excludeFolder(path: String): Result<Unit> = runCatching {
        require(path.isNotBlank())
        withContext(Dispatchers.IO) {
            dao.upsertExclusion(FolderExclusionEntity(path))
            refresh().getOrThrow()
        }
    }

    override suspend fun includeFolder(path: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            dao.deleteExclusion(path)
            refresh().getOrThrow()
        }
    }

    companion object { private const val OBSERVER_DEBOUNCE_MILLIS = 750L }
}

private fun MediaEntity.toModel() = MediaItem(
    id = MediaId(id), uri = uri, title = title, kind = MediaKind.valueOf(kind), durationMillis = durationMillis,
    artist = artist, album = album, artworkUri = artworkUri, albumArtist = albumArtist, genre = genre,
    sizeBytes = sizeBytes, mimeType = mimeType, dateAddedEpochSeconds = dateAddedEpochSeconds,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds, width = width, height = height, frameRate = frameRate,
    bitrate = bitrate, trackNumber = trackNumber, discNumber = discNumber, folderName = folderName,
    folderPath = folderPath, source = MediaSource.valueOf(source), volumeName = volumeName, isFavorite = isFavorite,
    isAvailable = isAvailable, lastPlaybackPositionMillis = lastPlaybackPositionMillis,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
)

private fun CategoryRow.toModel() = LibraryCategory(name, itemCount, artworkUri)
