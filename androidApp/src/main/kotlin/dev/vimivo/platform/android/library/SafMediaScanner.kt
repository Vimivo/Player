@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "ImportOrdering",
    "MaximumLineLength",
    "MaxLineLength",
    "PropertyWrapping",
    "StringTemplate",
)

package dev.vimivo.platform.android.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.platform.android.database.LibraryDao
import dev.vimivo.platform.android.database.MediaEntity
import dev.vimivo.platform.android.database.SafGrantEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import dev.vimivo.settings.HiddenMediaBehavior
import dev.vimivo.settings.SettingsRepository

class SafMediaScanner(
    private val context: Context,
    private val dao: LibraryDao,
    private val settingsRepository: SettingsRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun importFile(uri: Uri): MediaEntity {
        val document = requireNotNull(DocumentFile.fromSingleUri(context, uri)) { "The selected file is unavailable" }
        require(document.isFile) { "The selected document is not a file" }
        val entity = document.toMediaEntity(parentPath = null, source = MediaSource.SAF_FILE, volumeName = null)
        upsertPreservingState(listOf(entity))
        dao.upsertSafGrant(SafGrantEntity(uri.toString(), document.name ?: "Selected file", false, nowMillis()))
        return entity
    }

    suspend fun importTree(uri: Uri, progress: (Int) -> Unit): Int {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "The selected folder is unavailable" }
        require(root.isDirectory) { "The selected document is not a folder" }
        val scanToken = nowMillis()
        val exclusions = dao.exclusions()
        val showHidden = settingsRepository.settings.first().hiddenMediaBehavior == HiddenMediaBehavior.SHOW
        val pending = ArrayDeque<Pair<DocumentFile, String>>()
        pending += root to (root.name ?: "Selected folder")
        val batch = ArrayList<MediaEntity>(BATCH_SIZE)
        var count = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (folder, path) = pending.removeLast()
            if (exclusions.any(path::startsWith)) continue
            folder.listFiles().forEach { child ->
                currentCoroutineContext().ensureActive()
                if (!showHidden && child.name?.startsWith('.') == true) return@forEach
                when {
                    child.isDirectory -> pending += child to "$path/${child.name ?: "Folder"}"
                    child.isFile && child.isSupportedMedia() -> batch += child.toMediaEntity(
                        parentPath = path,
                        source = MediaSource.SAF_TREE,
                        volumeName = uri.toString(),
                        scanToken = scanToken,
                    )
                }
                if (batch.size == BATCH_SIZE) {
                    upsertPreservingState(batch)
                    count += batch.size
                    batch.clear()
                    progress(count)
                }
            }
        }
        if (batch.isNotEmpty()) {
            upsertPreservingState(batch)
            count += batch.size
        }
        dao.markUnseenUnavailable(MediaSource.SAF_TREE.name, uri.toString(), MediaKind.AUDIO.name, scanToken)
        dao.markUnseenUnavailable(MediaSource.SAF_TREE.name, uri.toString(), MediaKind.VIDEO.name, scanToken)
        dao.upsertSafGrant(SafGrantEntity(uri.toString(), root.name ?: "Selected folder", true, nowMillis()))
        progress(count)
        return count
    }

    suspend fun rescanPersistedTrees(progress: (String, Int) -> Unit): Int {
        var total = 0
        dao.safGrants().filter(SafGrantEntity::isTree).forEach { grant ->
            if (context.contentResolver.persistedUriPermissions.any { it.uri.toString() == grant.uri && it.isReadPermission }) {
                total += importTree(Uri.parse(grant.uri)) { count -> progress(grant.displayName, count) }
            }
        }
        return total
    }

    private fun DocumentFile.toMediaEntity(
        parentPath: String?,
        source: MediaSource,
        volumeName: String?,
        scanToken: Long = nowMillis(),
    ): MediaEntity {
        val metadata = extractMetadata(uri)
        val mime = type ?: context.contentResolver.getType(uri)
        val kind = if (mime?.startsWith("video/") == true || name.extension() in VIDEO_EXTENSIONS) {
            MediaKind.VIDEO
        } else {
            MediaKind.AUDIO
        }
        return MediaEntity(
            id = "saf:${uri}",
            uri = uri.toString(),
            kind = kind.name,
            source = source.name,
            volumeName = volumeName,
            mediaStoreId = null,
            title = metadata.title ?: name?.substringBeforeLast('.') ?: "Untitled",
            fileName = name,
            artist = metadata.artist,
            album = metadata.album,
            albumArtist = metadata.albumArtist,
            genre = metadata.genre,
            durationMillis = metadata.durationMillis,
            sizeBytes = length().takeIf { it >= 0 },
            mimeType = mime,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = lastModified().takeIf { it > 0 }?.div(1_000),
            width = metadata.width,
            height = metadata.height,
            frameRate = metadata.frameRate,
            bitrate = metadata.bitrate,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            releaseYear = metadata.releaseYear,
            artworkUri = null,
            folderName = parentPath?.substringAfterLast('/'),
            folderPath = parentPath,
            scanToken = scanToken,
        )
    }

    private fun extractMetadata(uri: Uri): ExtractedMetadata = runCatching {
        MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(context, uri)
                ExtractedMetadata(
                    title = retriever.text(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.text(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    genre = retriever.text(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    durationMillis = retriever.long(MediaMetadataRetriever.METADATA_KEY_DURATION),
                    width = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                    height = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                    frameRate = retriever.float(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE),
                    bitrate = retriever.int(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                    trackNumber = retriever.int(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    discNumber = retriever.int(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                    releaseYear = retriever.int(MediaMetadataRetriever.METADATA_KEY_YEAR),
                )
            } finally {
                retriever.release()
            }
        }
    }.getOrDefault(ExtractedMetadata())

    private suspend fun upsertPreservingState(items: List<MediaEntity>) {
        val state = dao.userState(items.map(MediaEntity::id)).associateBy { it.id }
        dao.upsertMedia(
            items.map { item ->
                state[item.id]?.let {
                    item.copy(
                        isFavorite = it.isFavorite,
                        lastPlaybackPositionMillis = it.lastPlaybackPositionMillis,
                        lastPlayedAtEpochMillis = it.lastPlayedAtEpochMillis,
                    )
                } ?: item
            },
        )
    }

    companion object {
        private const val BATCH_SIZE = 100
        private val AUDIO_EXTENSIONS = setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "alac", "wma")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "mpeg", "mpg", "flv", "ts", "m2ts", "3gp")
    }

    private fun DocumentFile.isSupportedMedia(): Boolean {
        val mime = type.orEmpty()
        return mime.startsWith("audio/") || mime.startsWith("video/") || name.extension() in AUDIO_EXTENSIONS + VIDEO_EXTENSIONS
    }
}

private data class ExtractedMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val durationMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val releaseYear: Int? = null,
)

private fun MediaMetadataRetriever.text(key: Int) = extractMetadata(key)?.takeUnless(String::isBlank)
private fun MediaMetadataRetriever.long(key: Int) = extractMetadata(key)?.toLongOrNull()?.takeIf { it >= 0 }
private fun MediaMetadataRetriever.int(key: Int) = extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }
private fun MediaMetadataRetriever.float(key: Int) = extractMetadata(key)?.toFloatOrNull()?.takeIf { it > 0 }
private fun String?.extension() = this?.substringAfterLast('.', "")?.lowercase().orEmpty()
