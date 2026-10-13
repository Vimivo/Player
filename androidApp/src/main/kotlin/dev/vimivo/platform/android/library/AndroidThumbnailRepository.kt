package dev.vimivo.platform.android.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidThumbnailRepository(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    suspend fun load(uri: String, artworkUri: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        val key = artworkUri ?: uri
        cache[key] ?: loadBitmap(key).also { bitmap -> if (bitmap != null) cache.put(key, bitmap) }
    }

    private fun loadBitmap(value: String): Bitmap? = runCatching {
        val uri = Uri.parse(value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), CancellationSignal())
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
        }
    }.getOrNull()

    companion object {
        private const val MAX_CACHE_KIB = 16 * 1024
        private const val THUMBNAIL_SIZE = 256
    }
}
