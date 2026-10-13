package dev.vimivo.platform.android.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidMediaStoreRepository(private val context: Context) : MediaRepository {
    private val library = MutableStateFlow<List<MediaItem>>(emptyList())
    override fun observeLibrary() = library.asStateFlow()

    override suspend fun refresh(): Result<Unit> = runCatching {
        check(hasMediaPermission()) { "Media permission is required before scanning the library" }
        library.value = withContext(Dispatchers.IO) { queryAudio() + queryVideo() }
    }

    private fun hasMediaPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun queryAudio(): List<MediaItem> = query(
        collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        kind = MediaKind.AUDIO,
        projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
        ),
    )

    private fun queryVideo(): List<MediaItem> = query(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        kind = MediaKind.VIDEO,
        projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
        ),
    )

    private fun query(
        collection: android.net.Uri,
        kind: MediaKind,
        projection: Array<String>,
    ): List<MediaItem> = buildList {
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(
                        MediaItem(
                            id = MediaId("$kind:$id"),
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            title = cursor.getString(titleColumn) ?: "Untitled",
                            kind = kind,
                            durationMillis = cursor.getLong(durationColumn).takeIf { it > 0 },
                            artist = artistColumn.takeIf { it >= 0 }?.let(cursor::getString),
                            album = albumColumn.takeIf { it >= 0 }?.let(cursor::getString),
                        ),
                    )
                }
            }
    }
}
