package dev.vimivo.platform.android.playback

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.vimivo.playback.SnapshotFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Saves snapshots to Pictures/Vimivo through the media store (Android 10 and later need no permission for that), and
 * to the app's own pictures folder on Android 8 and 9, where saving to the shared folder would need a storage
 * permission Vimivo does not ask for.
 */
class AndroidSnapshotStore(private val context: Context) {
    /** Writes [bitmap] as [fileName] and returns where it went; [format] picks PNG or JPEG. */
    suspend fun save(bitmap: Bitmap, fileName: String, format: SnapshotFormat): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val png = format == SnapshotFormat.PNG
                val compress = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(bitmap, fileName, compress, if (png) "image/png" else "image/jpeg")
                } else {
                    saveToAppFolder(bitmap, fileName, compress)
                }
            }
        }

    private fun saveToMediaStore(bitmap: Bitmap, fileName: String, compress: Bitmap.CompressFormat, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(resolver.insert(collection, values)) { "The picture could not be added to the gallery" }
        try {
            val out = checkNotNull(resolver.openOutputStream(uri)) { "The picture could not be written" }
            out.use { check(bitmap.compress(compress, QUALITY, it)) { "The picture could not be encoded" } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (error: IOException) {
            resolver.delete(uri, null, null)
            throw error
        } catch (error: IllegalStateException) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    private fun saveToAppFolder(bitmap: Bitmap, fileName: String, compress: Bitmap.CompressFormat): Uri {
        val pictures = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) { "No storage" }
        val folder = File(pictures, FOLDER)
        check(folder.isDirectory || folder.mkdirs()) { "The snapshot folder could not be created" }
        val file = File(folder, fileName)
        file.outputStream().use { check(bitmap.compress(compress, QUALITY, it)) { "The picture could not be encoded" } }
        return Uri.fromFile(file)
    }

    private companion object {
        const val FOLDER = "Vimivo"
        const val QUALITY = 95
    }
}
