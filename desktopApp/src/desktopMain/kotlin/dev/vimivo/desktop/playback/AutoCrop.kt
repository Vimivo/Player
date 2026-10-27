package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.CropRect
import dev.vimivo.playback.VideoCropMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/** Finds black bars of the current local video so they can be cropped away (V1 #12). */
internal class AutoCrop(private val inspector: MpvMediaInspector = MpvMediaInspector()) {
    /**
     * The crop that removes the bars: success(null) when there are none worth removing, failure when the media is not a
     * local file or libmpv could not analyse it.
     */
    suspend fun detect(media: MediaItem, durationMillis: Long?): Result<CropRect?> = withContext(Dispatchers.IO) {
        runCatching {
            val file = localFile(media) ?: error("Black-bar detection needs a local video file")
            val seconds = (durationMillis ?: DEFAULT_DURATION_MILLIS) / MILLIS_PER_SECOND
            val bounds = checkNotNull(inspector.detectContentBounds(file, seconds)) { "No picture could be analysed" }
            VideoCropMath.fromContent(
                bounds.frameWidth,
                bounds.frameHeight,
                CropRect(bounds.x, bounds.y, bounds.width, bounds.height),
            )
        }
    }

    private fun localFile(media: MediaItem): File? =
        media.uri.takeIf { it.startsWith("file:") }
            ?.let { runCatching { File(URI(it)) }.getOrNull() }
            ?.takeIf(File::isFile)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
        const val DEFAULT_DURATION_MILLIS = 10_000L
    }
}
