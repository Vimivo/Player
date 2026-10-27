package dev.vimivo.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.playback.SiblingQueue
import dev.vimivo.playback.SiblingQueues
import java.io.File

/**
 * Builds the "Open with" queue: the opened file, then the files of the same kind in its folder, in natural order
 * (same rule as desktop, [SiblingQueues]). The folder is found through MediaStore, which needs no extra permission
 * beyond the media access Vimivo already asks for; document and file-manager URIs are mapped to MediaStore with
 * [MediaStore.getMediaUri]. When the folder cannot be read, the file plays alone.
 */
internal class AndroidFolderSiblings(private val context: Context) {
    fun queueFor(opened: MediaItem): SiblingQueue<MediaItem> {
        val uri = Uri.parse(opened.uri)
        val siblings = runCatching {
            when (uri.scheme) {
                "file" -> fileSiblings(uri, opened.kind)
                else -> mediaStoreUri(uri)?.let { mediaStoreSiblings(it, opened) }
            }
        }.onFailure { error -> Log.w(TAG, "Could not list the opened file's folder", error) }.getOrNull()
        val self = siblings?.self ?: return SiblingQueue(listOf(opened), 0)
        // The opened item keeps the URI the other app granted; its siblings use MediaStore URIs.
        val items = siblings.items.map { if (it.uri == self.uri) opened else it }
        return SiblingQueues.build(opened, items, name = { it.fileName ?: it.title }) { a, b -> a.uri == b.uri }
    }

    /** The folder's playable files of one kind, and the opened file among them (null if MediaStore hid it). */
    private class Listing(val items: List<MediaItem>, val self: MediaItem?)

    private fun mediaStoreUri(uri: Uri): Uri? = when {
        uri.authority == MediaStore.AUTHORITY -> uri
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DocumentsContract.isDocumentUri(context, uri) ->
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        else -> null
    }

    private fun mediaStoreSiblings(item: Uri, opened: MediaItem): Listing? {
        val id = runCatching { ContentUris.parseId(item) }.getOrNull()?.takeIf { it >= 0 } ?: return null
        val collection = collectionFor(opened.kind, volumeOf(item))
        val folder = folderOf(collection, id) ?: return null
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val items = mutableListOf<MediaItem>()
        var self: MediaItem? = null
        context.contentResolver.query(collection, projection, folder.selection, folder.args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val siblingId = cursor.getLong(0)
                val name = cursor.getString(1) ?: continue
                val uri = ContentUris.withAppendedId(collection, siblingId).toString()
                val media = mediaItem(uri, name, cursor.getString(2), opened.kind, MediaSource.MEDIA_STORE)
                items += media
                if (siblingId == id) self = media
            }
        }
        return Listing(items, self)
    }

    /** Which rows share the opened file's folder: `RELATIVE_PATH` from Android 10, the file path before that. */
    private class Folder(val selection: String, val args: Array<String>)

    private fun folderOf(collection: Uri, id: Long): Folder? {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        @Suppress("DEPRECATION") // DATA is the only folder column before Android 10.
        val column = if (modern) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val value = context.contentResolver.query(
            ContentUris.withAppendedId(collection, id), arrayOf(column), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        return if (modern) {
            Folder("${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(value))
        } else {
            val directory = File(value).parent ?: return null
            @Suppress("DEPRECATION")
            Folder(
                "${MediaStore.MediaColumns.DATA} LIKE ? AND ${MediaStore.MediaColumns.DATA} NOT LIKE ?",
                arrayOf("$directory/%", "$directory/%/%"),
            )
        }
    }

    private fun volumeOf(uri: Uri): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getVolumeName(uri) else "external"

    private fun collectionFor(kind: MediaKind, volume: String): Uri = when (kind) {
        MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
        else -> MediaStore.Video.Media.getContentUri(volume)
    }

    private fun fileSiblings(uri: Uri, kind: MediaKind): Listing? {
        val file = File(uri.path ?: return null)
        val files = file.parentFile?.listFiles()
            ?.filter { it.isFile && !it.isHidden && kindOf(it) == kind }
            ?: return null
        val items = files.map { sibling ->
            mediaItem(Uri.fromFile(sibling).toString(), sibling.name, mimeOf(sibling), kind, MediaSource.SAF_FILE)
        }
        return Listing(items, items.firstOrNull { it.uri == uri.toString() })
    }

    private fun kindOf(file: File): MediaKind? = mimeOf(file)?.let {
        when {
            it.startsWith("video/") -> MediaKind.VIDEO
            it.startsWith("audio/") -> MediaKind.AUDIO
            else -> null
        }
    }

    private fun mimeOf(file: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())

    private fun mediaItem(uri: String, name: String, mime: String?, kind: MediaKind, source: MediaSource) = MediaItem(
        id = MediaId("external:$uri"),
        uri = uri,
        title = name.substringBeforeLast('.').ifBlank { name },
        kind = kind,
        mimeType = mime,
        fileName = name,
        source = source,
    )

    private companion object {
        const val TAG = "VimivoFolderSiblings"
    }
}
