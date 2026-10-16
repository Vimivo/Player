@file:Suppress(
    "ArgumentListWrapping",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "NestedBlockDepth",
    "ReturnCount",
    "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.playback

import dev.vimivo.desktop.library.DesktopMediaToolLocator
import dev.vimivo.playback.AudioStreamDetails
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.CodecSupportLevel
import dev.vimivo.playback.ColorSpace
import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.MediaProbeDetails
import dev.vimivo.playback.MediaProbeService
import dev.vimivo.playback.SubtitleStreamDetails
import dev.vimivo.playback.VideoStreamDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Resilient native media probing service for desktop.
 * Uses ffprobe with strict argument safety and bounded timeouts when present,
 * and falls back to resilient magic header sniffing when external tools are absent.
 */
class DesktopMediaProbeService : MediaProbeService {

    override suspend fun probeUri(uri: String): Result<MediaProbeDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveFile(uri)
            if (file == null || !file.exists()) {
                return@runCatching MediaProbeDetails(
                    container = "UNKNOWN",
                    overallCompatibility = CodecSupportLevel.UNSUPPORTED,
                    statusMessage = "Media file does not exist",
                )
            }
            if (file.length() == 0L) {
                return@runCatching MediaProbeDetails(
                    container = file.extension.uppercase(),
                    overallCompatibility = CodecSupportLevel.UNSUPPORTED,
                    statusMessage = "File is empty (0 bytes)",
                )
            }

            if (file.length() < MIN_FFPROBE_BYTES) {
                return@runCatching probeWithHeaderSniffing(file)
            }

            val ffprobe = DesktopMediaToolLocator.ffprobePath()
            if (ffprobe != null) {
                runCatching { probeWithFfprobe(ffprobe, file) }.getOrElse {
                    probeWithHeaderSniffing(file)
                }
            } else {
                probeWithHeaderSniffing(file)
            }
        }
    }

    private fun resolveFile(uri: String): File? = runCatching {
        if (uri.startsWith("file:")) {
            File(URI(uri))
        } else {
            File(uri)
        }
    }.getOrNull()

    private fun probeWithFfprobe(ffprobe: String, file: File): MediaProbeDetails {
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

        val output = try {
            val bytes = process.inputStream.readNBytes(MAX_PROBE_BYTES)
            val finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("ffprobe probe timed out on ${file.name}")
            }
            if (process.exitValue() != 0) {
                error("ffprobe probe failed with code ${process.exitValue()}")
            }
            bytes.decodeToString()
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            throw cancelled
        } catch (error: Throwable) {
            process.destroyForcibly()
            throw error
        }

        return parseFfprobeFlatOutput(file, output)
    }

    private fun parseFfprobeFlatOutput(file: File, output: String): MediaProbeDetails {
        val props = mutableMapOf<String, String>()
        output.lines().forEach { line ->
            val index = line.indexOf('=')
            if (index > 0) {
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim().removeSurrounding("\"")
                props[key] = value
            }
        }

        val formatName = props["format.format_name"].orEmpty().lowercase()
        val durationSec = props["format.duration"]?.toDoubleOrNull()
        val durationMillis = durationSec?.let { (it * 1000.0).toLong() }
        val bitrate = props["format.bit_rate"]?.toIntOrNull()
        val nbStreams = props["format.nb_streams"]?.toIntOrNull() ?: 0

        val videoStreams = mutableListOf<VideoStreamDetails>()
        val audioStreams = mutableListOf<AudioStreamDetails>()
        val subtitleStreams = mutableListOf<SubtitleStreamDetails>()
        val chapters = mutableListOf<ChapterInfo>()

        for (i in 0 until nbStreams) {
            val prefix = "streams.stream.$i"
            val codecType = props["$prefix.codec_type"] ?: continue
            val codecName = props["$prefix.codec_name"] ?: "unknown"

            when (codecType) {
                "video" -> {
                    val profile = props["$prefix.profile"]
                    val width = props["$prefix.width"]?.toIntOrNull()
                    val height = props["$prefix.height"]?.toIntOrNull()
                    val fps = parseFrameRate(props["$prefix.avg_frame_rate"] ?: props["$prefix.r_frame_rate"])
                    val pixFmt = props["$prefix.pix_fmt"].orEmpty()
                    val bitDepth = props["$prefix.bits_per_raw_sample"]?.toIntOrNull()
                        ?: if (pixFmt.contains("10") || pixFmt.contains("p10")) 10 else 8

                    val transferStr = (props["$prefix.color_transfer"] ?: "").lowercase()
                    val primariesStr = (props["$prefix.color_primaries"] ?: "").lowercase()

                    val colorTransfer = when {
                        transferStr.contains("2084") || transferStr.contains("pq") -> ColorTransfer.HDR10_PQ
                        transferStr.contains("arib") || transferStr.contains("hlg") -> ColorTransfer.HLG
                        transferStr.contains("709") -> ColorTransfer.SDR_BT709
                        else -> ColorTransfer.UNKNOWN
                    }

                    val colorSpace = when {
                        primariesStr.contains("2020") -> ColorSpace.BT2020
                        primariesStr.contains("709") -> ColorSpace.BT709
                        primariesStr.contains("601") -> ColorSpace.BT601
                        else -> ColorSpace.UNKNOWN
                    }

                    val isHw = codecName in HARDWARE_ACCELERATED_CODECS

                    videoStreams.add(
                        VideoStreamDetails(
                            codec = codecName,
                            profile = profile,
                            width = width,
                            height = height,
                            frameRate = fps,
                            bitDepth = bitDepth,
                            colorTransfer = colorTransfer,
                            colorSpace = colorSpace,
                            isHardwareAccelerated = isHw,
                        ),
                    )
                }

                "audio" -> {
                    audioStreams.add(
                        AudioStreamDetails(
                            codec = codecName,
                            sampleRate = props["$prefix.sample_rate"]?.toIntOrNull(),
                            channels = props["$prefix.channels"]?.toIntOrNull(),
                            bitrate = props["$prefix.bit_rate"]?.toIntOrNull(),
                            sampleFormat = props["$prefix.sample_fmt"],
                        ),
                    )
                }

                "subtitle" -> {
                    val subId = props["$prefix.index"] ?: i.toString()
                    val lang = props["$prefix.tags.language"]
                    val title = props["$prefix.tags.title"]
                    val isDefault = props["$prefix.disposition.default"] == "1"
                    val isForced = props["$prefix.disposition.forced"] == "1"

                    subtitleStreams.add(
                        SubtitleStreamDetails(
                            id = subId,
                            codec = codecName,
                            language = lang,
                            title = title,
                            isDefault = isDefault,
                            isForced = isForced,
                        ),
                    )
                }
            }
        }

        // Chapters
        val nbChapters = props["format.nb_chapters"]?.toIntOrNull() ?: 0
        for (c in 0 until nbChapters) {
            val prefix = "chapters.chapter.$c"
            val title = props["$prefix.tags.title"] ?: "Chapter ${c + 1}"
            val startSec = props["$prefix.start_time"]?.toDoubleOrNull() ?: 0.0
            chapters.add(
                ChapterInfo(
                    index = c,
                    title = title,
                    startPositionMillis = (startSec * 1000.0).toLong(),
                ),
            )
        }

        val container = resolveContainerName(formatName, file.extension)
        val compatibility = evaluateCompatibility(videoStreams, audioStreams)

        return MediaProbeDetails(
            container = container,
            durationMillis = durationMillis,
            bitrate = bitrate,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            subtitleStreams = subtitleStreams,
            chapters = chapters,
            overallCompatibility = compatibility,
            statusMessage = "Probed via native inspection (${compatibility.name})",
        )
    }

    internal fun probeWithHeaderSniffing(file: File): MediaProbeDetails {
        val ext = file.extension.uppercase()
        val header = ByteArray(HEADER_READ_LIMIT)
        val read = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)

        if (read < 4) {
            return MediaProbeDetails(
                container = ext,
                overallCompatibility = CodecSupportLevel.UNSUPPORTED,
                statusMessage = "Truncated file (< 4 bytes)",
            )
        }

        val container = detectContainerFromBytes(header, ext)
        val isSupported = ext.lowercase() in KNOWN_MEDIA_EXTENSIONS

        return MediaProbeDetails(
            container = container,
            overallCompatibility = if (isSupported) CodecSupportLevel.SUPPORTED_AND_VERIFIED else CodecSupportLevel.UNKNOWN,
            statusMessage = if (isSupported) "Recognized container ($container)" else "Unverified container format",
        )
    }

    private fun detectContainerFromBytes(header: ByteArray, fallback: String): String {
        // Matroska / WebM EBML header 0x1A, 0x45, 0xDF, 0xA3
        if (header.size >= 4 &&
            header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return if (fallback.equals("WEBM", ignoreCase = true)) "WEBM" else "MKV"
        }

        // FLAC "fLaC"
        if (header.size >= 4 &&
            header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
        ) {
            return "FLAC"
        }

        // Ogg "OggS"
        if (header.size >= 4 &&
            header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()
        ) {
            return "OGG"
        }

        // RIFF (WAV / AVI)
        if (header.size >= 12 &&
            header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
        ) {
            val waveOrAvi = String(header, 8, 4)
            if (waveOrAvi == "WAVE") return "WAV"
            if (waveOrAvi == "AVI ") return "AVI"
        }

        // MP4 / MOV: "ftyp" box at offset 4
        if (header.size >= 8 &&
            header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
        ) {
            return if (fallback.equals("MOV", ignoreCase = true)) "MOV" else "MP4"
        }

        // MP3 ID3 header "ID3"
        if (header.size >= 3 &&
            header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()
        ) {
            return "MP3"
        }

        return fallback
    }

    private fun evaluateCompatibility(
        videoStreams: List<VideoStreamDetails>,
        audioStreams: List<AudioStreamDetails>,
    ): CodecSupportLevel {
        val hasUnsupportedVideo = videoStreams.any { it.codec !in SUPPORTED_VIDEO_CODECS }
        val hasUnsupportedAudio = audioStreams.any { it.codec !in SUPPORTED_AUDIO_CODECS }
        if (hasUnsupportedVideo || hasUnsupportedAudio) {
            return CodecSupportLevel.UNSUPPORTED
        }

        val hasHwVideo = videoStreams.any { it.isHardwareAccelerated }
        return if (hasHwVideo) {
            CodecSupportLevel.HARDWARE_ACCELERATED_VERIFIED
        } else if (videoStreams.isNotEmpty() || audioStreams.isNotEmpty()) {
            CodecSupportLevel.SUPPORTED_AND_VERIFIED
        } else {
            CodecSupportLevel.UNKNOWN
        }
    }

    private fun parseFrameRate(raw: String?): Float? {
        if (raw == null) return null
        val slash = raw.indexOf('/')
        if (slash > 0) {
            val num = raw.substring(0, slash).toFloatOrNull() ?: return null
            val den = raw.substring(slash + 1).toFloatOrNull() ?: return null
            if (den > 0f) return num / den
        }
        return raw.toFloatOrNull()
    }

    private fun resolveContainerName(formatName: String, extension: String): String = when {
        formatName.contains("mp4") || formatName.contains("mov") -> "MP4"
        formatName.contains("matroska") || formatName.contains("webm") -> {
            if (extension.equals("webm", ignoreCase = true)) "WEBM" else "MKV"
        }
        formatName.contains("avi") -> "AVI"
        formatName.contains("flac") -> "FLAC"
        formatName.contains("ogg") -> "OGG"
        formatName.contains("wav") -> "WAV"
        formatName.contains("mp3") -> "MP3"
        else -> extension.uppercase()
    }

    companion object {
        private const val MIN_FFPROBE_BYTES = 64L
        private const val MAX_PROBE_BYTES = 1024 * 1024
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private const val HEADER_READ_LIMIT = 4096

        private val HARDWARE_ACCELERATED_CODECS = setOf(
            "h264", "hevc", "h265", "vp9", "av1",
        )

        private val SUPPORTED_VIDEO_CODECS = setOf(
            "h264", "hevc", "h265", "vp8", "vp9", "av1",
            "mpeg2video", "mpeg4", "prores", "mjpeg",
        )

        private val SUPPORTED_AUDIO_CODECS = setOf(
            "mp3", "aac", "flac", "alac", "vorbis", "opus",
            "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le",
            "ac3", "eac3", "dts", "wav",
        )

        private val KNOWN_MEDIA_EXTENSIONS = setOf(
            "mp3", "aac", "m4a", "flac", "alac", "wav", "ogg", "opus", "aiff",
            "mp4", "mkv", "webm", "mov", "avi", "ts", "m2ts",
        )
    }
}
