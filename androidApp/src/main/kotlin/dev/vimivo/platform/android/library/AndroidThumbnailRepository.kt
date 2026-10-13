@file:Suppress("MultiLineIfElse")

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

    suspend fun dominantColor(uri: String, artworkUri: String? = null): Int? = load(uri, artworkUri)?.let { bitmap ->
        withContext(Dispatchers.Default) {
            var red = 0L
            var green = 0L
            var blue = 0L
            var samples = 0
            val stepX = (bitmap.width / 12).coerceAtLeast(1)
            val stepY = (bitmap.height / 12).coerceAtLeast(1)
            for (x in 0 until bitmap.width step stepX) for (y in 0 until bitmap.height step stepY) {
                val color = bitmap.getPixel(x, y)
                red += android.graphics.Color.red(color)
                green += android.graphics.Color.green(color)
                blue += android.graphics.Color.blue(color)
                samples++
            }
            if (samples == 0) null else android.graphics.Color.rgb(
                (red / samples).toInt(),
                (green / samples).toInt(),
                (blue / samples).toInt(),
            )
        }
    }

    fun clear() = cache.evictAll()

    private fun loadBitmap(value: String): Bitmap? = runCatching {
        val uri = Uri.parse(value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), CancellationSignal())
        } else {
            decodeUri(uri)
                ?: MediaMetadataRetriever().let { retriever ->
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.embeddedPicture?.let(::decodeBytes)
                            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.bounded()
                    } finally {
                        retriever.release()
                    }
                }
        }
    }.getOrNull()

    private fun decodeUri(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun Bitmap.bounded(): Bitmap {
        if (width <= THUMBNAIL_SIZE && height <= THUMBNAIL_SIZE) return this
        val scale = minOf(THUMBNAIL_SIZE.toFloat() / width, THUMBNAIL_SIZE.toFloat() / height)
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true).also {
            if (it !== this) recycle()
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > THUMBNAIL_SIZE * 2 || height / sample > THUMBNAIL_SIZE * 2) sample *= 2
        return sample
    }

    companion object {
        private const val MAX_CACHE_KIB = 16 * 1024
        private const val THUMBNAIL_SIZE = 256
    }
}
