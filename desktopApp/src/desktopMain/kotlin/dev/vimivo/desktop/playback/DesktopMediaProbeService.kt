@file:Suppress("ArgumentListWrapping", "MagicNumber", "MaxLineLength", "MaximumLineLength") // Header magic numbers.

package dev.vimivo.desktop.playback

import dev.vimivo.playback.AudioStreamDetails
import dev.vimivo.playback.ChapterInfo
import dev.vimivo.playback.CodecSupportLevel
import dev.vimivo.playback.ColorSpace
import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.MediaProbeDetails
import dev.vimivo.playback.MediaProbeService
import dev.vimivo.playback.SubtitleStreamDetails
import dev.vimivo.playback.VideoStreamDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * Media Info for desktop: streams, colour, chapters and compatibility read with the bundled LGPL libmpv/FFmpeg
 * ([MpvMediaInspector], ADR-004); no external programs. Files libmpv cannot open fall back to container sniffing
 * from the first bytes, so damaged or truncated files still get a useful answer.
 */
class DesktopMediaProbeService internal constructor(
    private val inspector: MpvMediaInspector,
) : MediaProbeService {
    constructor() : this(MpvMediaInspector())

    override suspend fun probeUri(uri: String): Result<MediaProbeDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveFile(uri)
            when {
                file == null || !file.exists() -> MediaProbeDetails(
                    container = "UNKNOWN",
                    overallCompatibility = CodecSupportLevel.UNSUPPORTED,
                    statusMessage = "Media file does not exist",
                )
                file.length() == 0L -> MediaProbeDetails(
                    container = file.extension.uppercase(),
                    overallCompatibility = CodecSupportLevel.UNSUPPORTED,
                    statusMessage = "File is empty (0 bytes)",
                )
                file.length() < MIN_INSPECTABLE_BYTES -> probeWithHeaderSniffing(file)
                else -> runCatching { describe(file, inspector.inspect(file)) }
                    .getOrElse { probeWithHeaderSniffing(file) }
            }
        }
    }

    private fun resolveFile(uri: String): File? = runCatching {
        if (uri.startsWith("file:")) File(URI(uri)) else File(uri)
    }.getOrNull()

    private fun describe(file: File, inspection: MpvMediaInspector.Inspection): MediaProbeDetails {
        val tracks = inspection.tracks.filterNot { it.albumArt }
        val params = inspection.videoParams
        val videoStreams = tracks.filter { it.type == "video" }.map { track ->
            val codec = track.codec ?: "unknown"
            VideoStreamDetails(
                codec = codec,
                profile = track.profile,
                width = track.width,
                height = track.height,
                frameRate = track.fps,
                bitDepth = bitDepth(params?.pixelFormat),
                colorTransfer = colorTransfer(params?.transfer),
                colorSpace = colorSpace(params?.primaries),
                isHardwareAccelerated = codec in HARDWARE_ACCELERATED_CODECS,
            )
        }
        val audioStreams = tracks.filter { it.type == "audio" }.map { track ->
            AudioStreamDetails(
                codec = track.codec ?: "unknown",
                sampleRate = track.sampleRate,
                channels = track.channels,
                bitrate = track.bitrate,
            )
        }
        val subtitleStreams = tracks.filter { it.type == "sub" }.map { track ->
            SubtitleStreamDetails(
                id = track.id?.toString() ?: "",
                codec = track.codec,
                language = track.language,
                title = track.title,
                isDefault = track.isDefault,
                isForced = track.isForced,
                isExternal = track.isExternal,
            )
        }
        val chapters = inspection.chapters.mapIndexed { index, chapter ->
            ChapterInfo(
                index = index,
                title = chapter.title ?: "Chapter ${index + 1}",
                startPositionMillis = chapter.startMillis,
            )
        }
        val durationMillis = inspection.durationMillis
        val bitrate = durationMillis?.takeIf { it > 0 }
            ?.let { (file.length() * BITS_PER_BYTE * MILLIS_PER_SECOND / it).coerceAtMost(Int.MAX_VALUE.toLong()) }
            ?.toInt()
        val compatibility = evaluateCompatibility(videoStreams, audioStreams)
        return MediaProbeDetails(
            container = resolveContainerName(inspection.fileFormat.orEmpty(), file.extension),
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

    /** Bit depth from libmpv's pixel format name (yuv420p10, p010, yuv444p12, …); 8 otherwise. */
    private fun bitDepth(pixelFormat: String?): Int {
        val name = pixelFormat.orEmpty().lowercase()
        return when {
            name == "p010" || name.contains("p10") -> 10
            name.contains("p12") -> 12
            name.contains("p16") -> 16
            else -> 8
        }
    }

    /** libmpv's `video-params/gamma` names (pq, hlg, bt.1886, …). */
    private fun colorTransfer(transfer: String?): ColorTransfer = when (transfer?.lowercase()) {
        "pq" -> ColorTransfer.HDR10_PQ
        "hlg" -> ColorTransfer.HLG
        "bt.1886", "srgb", "linear", "gamma2.2" -> ColorTransfer.SDR_BT709
        else -> ColorTransfer.UNKNOWN
    }

    /** libmpv's `video-params/primaries` names (bt.709, bt.2020, bt.601-525, bt.601-625, …). */
    private fun colorSpace(primaries: String?): ColorSpace {
        val name = primaries.orEmpty().lowercase()
        return when {
            name.contains("2020") -> ColorSpace.BT2020
            name.contains("709") -> ColorSpace.BT709
            name.contains("601") -> ColorSpace.BT601
            else -> ColorSpace.UNKNOWN
        }
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
        fun has(offset: Int, magic: String) = header.size >= offset + magic.length &&
            magic.indices.all { header[offset + it] == magic[it].code.toByte() }
        val ebml = header.size >= 4 && header.copyOfRange(0, 4).contentEquals(EBML_MAGIC)
        return when {
            ebml -> if (fallback.equals("WEBM", ignoreCase = true)) "WEBM" else "MKV"
            has(0, "fLaC") -> "FLAC"
            has(0, "OggS") -> "OGG"
            has(0, "RIFF") -> RIFF_TYPES.entries.firstOrNull { (tag, _) -> has(8, tag) }?.value ?: fallback
            has(4, "ftyp") -> if (fallback.equals("MOV", ignoreCase = true)) "MOV" else "MP4"
            has(0, "ID3") -> "MP3"
            else -> fallback
        }
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
        private const val MIN_INSPECTABLE_BYTES = 64L
        private const val HEADER_READ_LIMIT = 4096
        private val EBML_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        private val RIFF_TYPES = mapOf("WAVE" to "WAV", "AVI " to "AVI")
        private const val BITS_PER_BYTE = 8L
        private const val MILLIS_PER_SECOND = 1_000L

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
