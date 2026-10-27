@file:Suppress("MagicNumber") // Thumbnail timing heuristics.

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.playback.MpvMediaInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video metadata and thumbnails for the library, read with the bundled LGPL libmpv/FFmpeg (ADR-004) through
 * [MpvMediaInspector]: no external ffprobe/ffmpeg programs, bounded time per file, and a concurrency limit that keeps
 * library scans from competing with playback.
 */
internal object DesktopVideoInspector {
    private val concurrencyLimit = Semaphore(3)
    private val inspector = MpvMediaInspector()
    private const val THUMBNAIL_MAX_WIDTH = 640
    private const val BITS_PER_BYTE = 8L
    private const val MILLIS_PER_SECOND = 1_000L

    suspend fun inspect(
        media: MediaItem,
        file: File,
        cache: DesktopMediaCache,
    ): ExtractedMediaMetadata = concurrencyLimit.withPermit {
        withContext(Dispatchers.IO) {
            val inspection = inspector.inspect(file)
            val tracks = inspection.tracks.filterNot { it.albumArt }
            require(tracks.isNotEmpty()) { "No streams found in ${file.name}" }
            val video = tracks.firstOrNull { it.type == "video" }
            val audio = tracks.filter { it.type == "audio" }
            val durationMillis = inspection.durationMillis
            val bitrate = durationMillis?.takeIf { it > 0 }
                ?.let { (file.length() * BITS_PER_BYTE * MILLIS_PER_SECOND / it).coerceAtMost(Int.MAX_VALUE.toLong()) }
                ?.toInt()

            val cacheIdentity = "${media.id.value}:${media.sizeBytes}:${media.dateModifiedEpochSeconds}"
            val thumbnailUri = if (video != null) extractThumbnail(file, durationMillis, cache, cacheIdentity) else null

            ExtractedMediaMetadata(
                title = inspection.title ?: media.title,
                durationMillis = durationMillis,
                bitrate = bitrate,
                sampleRate = audio.firstOrNull()?.sampleRate,
                channels = audio.firstOrNull()?.channels,
                width = video?.width,
                height = video?.height,
                frameRate = video?.fps,
                videoCodec = video?.codec,
                audioCodec = audio.firstOrNull()?.codec,
                container = resolveContainer(inspection.fileFormat.orEmpty(), file.extension),
                audioTrackCount = audio.size,
                subtitleTrackCount = tracks.count { it.type == "sub" },
                thumbnailUri = thumbnailUri,
                artworkUri = thumbnailUri,
            )
        }
    }

    private fun extractThumbnail(
        file: File,
        durationMillis: Long?,
        cache: DesktopMediaCache,
        cacheIdentity: String,
    ): String? {
        cache.getThumbnail(cacheIdentity)?.let { return it }
        val targetSeconds = when {
            durationMillis != null && durationMillis > 2_000L -> (durationMillis * 0.1 / 1_000.0).coerceIn(0.2, 5.0)
            durationMillis != null && durationMillis > 200L -> 0.05
            else -> 0.0
        }
        // A frame a little into the video avoids black intros; very short files fall back to the first frame.
        val bytes = captureOrNull(file, targetSeconds)
            ?: if (targetSeconds > 0.0) captureOrNull(file, 0.0) else null
        return bytes?.let { frame -> cache.storeThumbnail(cacheIdentity, frame).getOrNull() }
    }

    private fun captureOrNull(file: File, seconds: Double): ByteArray? =
        runCatching { inspector.captureFrame(file, seconds, THUMBNAIL_MAX_WIDTH) }.getOrNull()

    private fun resolveContainer(formatName: String, extension: String): String = when {
        formatName.contains("mp4") || formatName.contains("mov") -> "MP4"
        formatName.contains("matroska") || formatName.contains("webm") -> {
            if (extension.equals("webm", ignoreCase = true)) "WEBM" else "MKV"
        }
        formatName.contains("avi") -> "AVI"
        else -> extension.uppercase()
    }
}
