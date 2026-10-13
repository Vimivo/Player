@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "NestedBlockDepth")

package dev.vimivo.platform.android.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaPermissionAccess
import dev.vimivo.core.model.MediaPermissionState
import dev.vimivo.core.model.MediaSource
import dev.vimivo.platform.android.database.LibraryDao
import dev.vimivo.platform.android.database.MediaEntity
import dev.vimivo.platform.android.database.ScanStateEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class MediaStoreScanner(
    private val context: Context,
    private val dao: LibraryDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun scan(permission: MediaPermissionState, full: Boolean, progress: (String, Int) -> Unit): Int {
        var count = 0
        volumes().forEach { volume ->
            if (permission.audio == MediaPermissionAccess.FULL) {
                count += scanCollection(volume, MediaKind.AUDIO, full, progress)
            }
            if (permission.video in setOf(MediaPermissionAccess.FULL, MediaPermissionAccess.PARTIAL)) {
                count += scanCollection(volume, MediaKind.VIDEO, full, progress)
            }
        }
        return count
    }

    private suspend fun scanCollection(
        volume: String,
        kind: MediaKind,
        forceFull: Boolean,
        progress: (String, Int) -> Unit,
    ): Int {
        val source = MediaSource.MEDIA_STORE.name
        val oldState = dao.scanState(source, volume, kind.name)
        val version = version(volume)
        val generation = generation(volume)
        val full = forceFull || oldState == null || oldState.mediaStoreVersion != version || generation == null
        val sinceGeneration = if (full) null else oldState.generation
        if (!full && generation == oldState.generation) return 0
        val scanToken = nowMillis()
        val collection = collection(volume, kind)
        val exclusions = dao.exclusions()
        var processed = 0
        val batch = ArrayList<MediaEntity>(BATCH_SIZE)
        query(collection, kind, sinceGeneration)?.use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val item = cursor.toEntity(collection, volume, kind, scanToken)
                if (item.folderPath?.let { path -> exclusions.any(path::startsWith) } != true) batch += item
                if (batch.size == BATCH_SIZE) {
                    upsertPreservingUserState(batch)
                    processed += batch.size
                    batch.clear()
                    progress("$volume ${kind.name.lowercase()}", processed)
                }
            }
        }
        if (batch.isNotEmpty()) {
            upsertPreservingUserState(batch)
            processed += batch.size
        }
        if (full) dao.markUnseenUnavailable(source, volume, kind.name, scanToken)
        dao.upsertScanState(
            ScanStateEntity(source, volume, kind.name, version, generation, nowMillis()),
        )
        progress("$volume ${kind.name.lowercase()}", processed)
        return processed
    }

    private suspend fun upsertPreservingUserState(items: List<MediaEntity>) {
        val userState = dao.userState(items.map(MediaEntity::id)).associateBy { it.id }
        dao.upsertMedia(
            items.map { item ->
                userState[item.id]?.let { state ->
                    item.copy(
                        isFavorite = state.isFavorite,
                        lastPlaybackPositionMillis = state.lastPlaybackPositionMillis,
                        lastPlayedAtEpochMillis = state.lastPlayedAtEpochMillis,
                    )
                } ?: item
            },
        )
    }

    private fun query(collection: Uri, kind: MediaKind, sinceGeneration: Long?): Cursor? {
        val selections = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) selections += "${MediaStore.MediaColumns.IS_PENDING} = 0"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && sinceGeneration != null) {
            selections += "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?"
            args += sinceGeneration.toString()
        }
        return context.contentResolver.query(
            collection,
            projection(kind),
            selections.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            "${MediaStore.MediaColumns.DATE_MODIFIED} ASC",
        )
    }

    private fun projection(kind: MediaKind): Array<String> = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.TITLE)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_ADDED)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.DURATION)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.MediaColumns.BITRATE)
            add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }
        if (kind == MediaKind.AUDIO) {
            add(MediaStore.Audio.AudioColumns.ARTIST)
            add(MediaStore.Audio.AudioColumns.ALBUM)
            add(MediaStore.Audio.AudioColumns.ALBUM_ID)
            add(MediaStore.Audio.AudioColumns.ALBUM_ARTIST)
            add(MediaStore.Audio.AudioColumns.CD_TRACK_NUMBER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.AudioColumns.GENRE)
                add(MediaStore.Audio.AudioColumns.DISC_NUMBER)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Video.VideoColumns.CAPTURE_FRAMERATE)
        }
    }.toTypedArray()

    private fun Cursor.toEntity(
        collection: Uri,
        volume: String,
        kind: MediaKind,
        scanToken: Long,
    ): MediaEntity {
        val mediaStoreId = long(MediaStore.MediaColumns._ID) ?: error("MediaStore row has no ID")
        val relativePath = string(MediaStore.MediaColumns.RELATIVE_PATH)?.trimEnd('/')
        val displayName = string(MediaStore.MediaColumns.DISPLAY_NAME)
        val title = string(MediaStore.MediaColumns.TITLE).cleanMetadata() ?: displayName ?: "Untitled"
        val albumId = long(MediaStore.Audio.AudioColumns.ALBUM_ID)
        return MediaEntity(
            id = "mediastore:$volume:${kind.name}:$mediaStoreId",
            uri = ContentUris.withAppendedId(collection, mediaStoreId).toString(),
            kind = kind.name,
            source = MediaSource.MEDIA_STORE.name,
            volumeName = volume,
            mediaStoreId = mediaStoreId,
            title = title,
            artist = string(MediaStore.Audio.AudioColumns.ARTIST).cleanMetadata(),
            album = string(MediaStore.Audio.AudioColumns.ALBUM).cleanMetadata(),
            albumArtist = string(MediaStore.Audio.AudioColumns.ALBUM_ARTIST).cleanMetadata(),
            genre = string(MediaStore.Audio.AudioColumns.GENRE).cleanMetadata(),
            durationMillis = long(MediaStore.MediaColumns.DURATION)?.takeIf { it >= 0 },
            sizeBytes = long(MediaStore.MediaColumns.SIZE)?.takeIf { it >= 0 },
            mimeType = string(MediaStore.MediaColumns.MIME_TYPE),
            dateAddedEpochSeconds = long(MediaStore.MediaColumns.DATE_ADDED),
            dateModifiedEpochSeconds = long(MediaStore.MediaColumns.DATE_MODIFIED),
            width = int(MediaStore.MediaColumns.WIDTH)?.takeIf { it > 0 },
            height = int(MediaStore.MediaColumns.HEIGHT)?.takeIf { it > 0 },
            frameRate = float(MediaStore.Video.VideoColumns.CAPTURE_FRAMERATE)?.takeIf { it > 0 },
            bitrate = int(MediaStore.MediaColumns.BITRATE)?.takeIf { it > 0 },
            trackNumber = string(MediaStore.Audio.AudioColumns.CD_TRACK_NUMBER).parseLeadingInt(),
            discNumber = int(MediaStore.Audio.AudioColumns.DISC_NUMBER)?.takeIf { it > 0 },
            artworkUri = albumId?.let { "content://media/external/audio/albumart/$it" },
            folderName = relativePath?.substringAfterLast('/'),
            folderPath = relativePath,
            scanToken = scanToken,
        )
    }

    private fun volumes(): Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.getExternalVolumeNames(context)
    } else {
        setOf(MediaStore.VOLUME_EXTERNAL)
    }

    private fun collection(volume: String, kind: MediaKind): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (kind == MediaKind.AUDIO) MediaStore.Audio.Media.getContentUri(volume) else MediaStore.Video.Media.getContentUri(volume)
    } else {
        if (kind == MediaKind.AUDIO) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    private fun version(volume: String): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.getVersion(context, volume)
    } else {
        null
    }

    private fun generation(volume: String): Long? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        MediaStore.getGeneration(context, volume)
    } else {
        null
    }

    companion object { private const val BATCH_SIZE = 250 }
}

private fun Cursor.index(column: String) = getColumnIndex(column).takeIf { it >= 0 }
private fun Cursor.string(column: String) = index(column)?.let { if (isNull(it)) null else getString(it) }
private fun Cursor.long(column: String) = index(column)?.let { if (isNull(it)) null else getLong(it) }
private fun Cursor.int(column: String) = index(column)?.let { if (isNull(it)) null else getInt(it) }
private fun Cursor.float(column: String) = index(column)?.let { if (isNull(it)) null else getFloat(it) }
private fun String?.cleanMetadata() = this?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
private fun String?.parseLeadingInt() = this?.substringBefore('/')?.toIntOrNull()?.takeIf { it > 0 }
