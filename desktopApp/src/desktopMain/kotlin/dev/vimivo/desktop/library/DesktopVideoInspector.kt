@file:Suppress(
    "ArgumentListWrapping", "ComplexCondition", "CyclomaticComplexMethod", "MagicNumber",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Isolated native media inspector and video thumbnail generator.
 * Uses bounded timeouts, argument-safe ProcessBuilder execution without shell interpolation,
 * and concurrency limits to safeguard UI and playback performance.
 */
internal object DesktopVideoInspector {
    private val concurrencyLimit = Semaphore(3)
    private const val PROCESS_TIMEOUT_SECONDS = 5L
    private const val MAX_OUTPUT_BYTES = 1024 * 1024
    private const val MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024

    suspend fun inspect(
        media: MediaItem,
        file: File,
        cache: DesktopMediaCache,
    ): ExtractedMediaMetadata = concurrencyLimit.withPermit {
        withContext(Dispatchers.IO) {
            val ffprobe = DesktopMediaToolLocator.ffprobePath()
                ?: error("Native inspection tool ffprobe is not available")
            val output = executeProbe(ffprobe, file)
            val properties = parseProperties(output)
            require(properties.isNotEmpty()) { "Could not inspect video format for ${file.name}" }

            val nbStreams = properties["format.nb_streams"]?.toIntOrNull() ?: 0
            require(nbStreams > 0) { "No streams found in ${file.name}" }

            var videoWidth: Int? = null
            var videoHeight: Int? = null
            var frameRate: Float? = null
            var videoCodec: String? = null
            var audioCodec: String? = null
            var sampleRate: Int? = null
            var channels: Int? = null
            var audioTracks = 0
            var subtitleTracks = 0

            for (i in 0 until nbStreams) {
                val prefix = "streams.stream.$i"
                val codecType = properties["$prefix.codec_type"]
                if (codecType == "video" && videoCodec == null) {
                    videoCodec = properties["$prefix.codec_name"]
                    videoWidth = properties["$prefix.width"]?.toIntOrNull()
                    videoHeight = properties["$prefix.height"]?.toIntOrNull()
                    val fps = properties["$prefix.avg_frame_rate"] ?: properties["$prefix.r_frame_rate"]
                    frameRate = fps?.let(::parseFrameRate)
                } else if (codecType == "audio") {
                    audioTracks++
                    if (audioCodec == null) {
                        audioCodec = properties["$prefix.codec_name"]
                        sampleRate = properties["$prefix.sample_rate"]?.toIntOrNull()
                        channels = properties["$prefix.channels"]?.toIntOrNull()
                    }
                } else if (codecType == "subtitle") {
                    subtitleTracks++
                }
            }

            val durationSeconds = properties["format.duration"]?.toDoubleOrNull()
                ?: (0 until nbStreams).firstNotNullOfOrNull { properties["streams.stream.$it.duration"]?.toDoubleOrNull() }
            val durationMillis = durationSeconds?.let { (it * 1_000).toLong() }
            val bitrate = properties["format.bit_rate"]?.toIntOrNull()
            val formatName = properties["format.format_name"].orEmpty()
            val container = resolveContainer(formatName, file.extension)
            val title = properties["format.tags.title"] ?: media.title

            val cacheIdentity = "${media.id.value}:${media.sizeBytes}:${media.dateModifiedEpochSeconds}"
            val thumbnailUri = extractThumbnail(file, durationMillis, cache, cacheIdentity)

            ExtractedMediaMetadata(
                title = title,
                durationMillis = durationMillis,
                bitrate = bitrate,
                sampleRate = sampleRate,
                channels = channels,
                width = videoWidth,
                height = videoHeight,
                frameRate = frameRate,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                container = container,
                audioTrackCount = audioTracks,
                subtitleTrackCount = subtitleTracks,
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
        val ffmpeg = DesktopMediaToolLocator.ffmpegPath() ?: return null

        val targetTimestamp = when {
            durationMillis != null && durationMillis > 2_000L -> (durationMillis * 0.1 / 1_000.0).coerceIn(0.2, 5.0)
            durationMillis != null && durationMillis > 200L -> 0.05
            else -> 0.0
        }

        val bytes = captureFrame(ffmpeg, file, targetTimestamp)
            ?: if (targetTimestamp > 0.0) captureFrame(ffmpeg, file, 0.0) else null

        return bytes?.takeIf(ByteArray::isNotEmpty)?.let { frameBytes ->
            cache.storeThumbnail(cacheIdentity, frameBytes).getOrNull()
        }
    }

    private fun captureFrame(ffmpeg: String, file: File, timestamp: Double): ByteArray? {
        val arguments = listOf(
            ffmpeg,
            "-nostdin",
            "-v", "error",
            "-ss", "%.3f".format(Locale.US, timestamp),
            "-i", file.absolutePath,
            "-frames:v", "1",
            "-vf", "scale='min(640,iw)':-2",
            "-f", "image2pipe",
            "-vcodec", "mjpeg",
            "-",
        )
        val process = ProcessBuilder(arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        return try {
            val bytes = process.inputStream.readNBytes(MAX_THUMBNAIL_BYTES)
            val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0 && bytes.isNotEmpty()) {
                bytes
            } else null
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            throw cancelled
        } catch (_: Throwable) {
            process.destroyForcibly()
            null
        }
    }

    private fun executeProbe(ffprobe: String, file: File): String {
        val arguments = listOf(
            ffprobe,
            "-v", "quiet",
            "-print_format", "flat",
            "-show_format",
            "-show_streams",
            "-show_chapters",
            file.absolutePath,
        )
        val process = ProcessBuilder(arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        return try {
            val output = process.inputStream.readNBytes(MAX_OUTPUT_BYTES).decodeToString()
            val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("ffprobe timed out on ${file.name}")
            }
            if (process.exitValue() != 0) {
                error("ffprobe failed with exit code ${process.exitValue()} on ${file.name}")
            }
            output
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            throw cancelled
        } catch (error: Throwable) {
            process.destroyForcibly()
            throw error
        }
    }

    private fun parseProperties(output: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        output.lines().forEach { line ->
            val index = line.indexOf('=')
            if (index > 0) {
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim().removeSurrounding("\"")
                result[key] = value
            }
        }
        return result
    }

    private fun parseFrameRate(raw: String): Float? {
        val slash = raw.indexOf('/')
        if (slash > 0) {
            val num = raw.substring(0, slash).toFloatOrNull() ?: return null
            val den = raw.substring(slash + 1).toFloatOrNull() ?: return null
            if (den > 0f) return num / den
        }
        return raw.toFloatOrNull()
    }

    private fun resolveContainer(formatName: String, extension: String): String = when {
        formatName.contains("mp4") || formatName.contains("mov") -> "MP4"
        formatName.contains("matroska") || formatName.contains("webm") -> {
            if (extension.equals("webm", ignoreCase = true)) "WEBM" else "MKV"
        }
        formatName.contains("avi") -> "AVI"
        else -> extension.uppercase()
    }
}
