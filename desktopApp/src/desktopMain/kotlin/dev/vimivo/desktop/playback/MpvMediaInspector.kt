package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Reads media properties and grabs single frames with short-lived, headless libmpv handles: the bundled LGPL
 * libmpv/FFmpeg (ADR-004) instead of external ffprobe/ffmpeg programs, which users do not have. Documented client API
 * only: no shell, no filename interpolation. Every call is bounded by [TIMEOUT_NANOS]; failures are exceptions.
 */
internal class MpvMediaInspector(private val loadNative: () -> MpvNative = MpvNative::load) {

    data class Track(
        val id: Int?,
        val type: String,
        val codec: String?,
        val profile: String? = null,
        val albumArt: Boolean = false,
        val width: Int? = null,
        val height: Int? = null,
        val fps: Float? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val bitrate: Int? = null,
        val language: String? = null,
        val title: String? = null,
        val isDefault: Boolean = false,
        val isForced: Boolean = false,
        val isExternal: Boolean = false,
    )

    data class Chapter(val title: String?, val startMillis: Long)

    /** The decoded picture's format, known once libmpv has decoded the first frame. */
    data class VideoParams(val pixelFormat: String?, val primaries: String?, val transfer: String?)

    data class Inspection(
        val durationMillis: Long?,
        val fileFormat: String?,
        val title: String?,
        val tracks: List<Track>,
        val chapters: List<Chapter> = emptyList(),
        val videoParams: VideoParams? = null,
    )

    /** Container, duration, title and every track of [file], read while the file is loaded but paused. */
    fun inspect(file: File): Inspection = withHandle(INSPECT_OPTIONS) { mpv, handle ->
        mpv.mpv_command(handle, StringArray(arrayOf("loadfile", file.absolutePath, "replace")))
        val outcome = awaitEvent(mpv, handle, EVENT_FILE_LOADED)
        check(outcome == null) { "libmpv could not open ${file.name}: $outcome" }
        val count = mpv.string(handle, "track-list/count")?.toIntOrNull() ?: 0
        val tracks = (0 until count).map { index -> track(mpv, handle, "track-list/$index") }
        val chapterCount = mpv.string(handle, "chapter-list/count")?.toIntOrNull() ?: 0
        Inspection(
            durationMillis = mpv.string(handle, "duration")?.toDoubleOrNull()?.let { (it * MILLIS).toLong() },
            fileFormat = mpv.string(handle, "file-format"),
            title = mpv.string(handle, "metadata/by-key/title")?.takeIf(String::isNotBlank),
            tracks = tracks,
            chapters = (0 until chapterCount).map { index ->
                Chapter(
                    title = mpv.string(handle, "chapter-list/$index/title")?.takeIf(String::isNotBlank),
                    startMillis = ((mpv.string(handle, "chapter-list/$index/time")?.toDoubleOrNull() ?: 0.0) * MILLIS)
                        .toLong(),
                )
            },
            videoParams = if (tracks.any { it.type == "video" && !it.albumArt }) videoParams(mpv, handle) else null,
        )
    }

    /** Waits briefly for the first decoded frame (paused, null output) and reads its colour parameters. */
    private fun videoParams(mpv: MpvNative, handle: Pointer): VideoParams? {
        val deadline = System.nanoTime() + VIDEO_PARAMS_TIMEOUT_NANOS
        var pixelFormat = mpv.string(handle, "video-params/pixelformat")
        while (pixelFormat == null && System.nanoTime() < deadline) {
            mpv.mpv_wait_event(handle, WAIT_SECONDS)
            pixelFormat = mpv.string(handle, "video-params/pixelformat")
        }
        if (pixelFormat == null) return null
        return VideoParams(
            pixelFormat = pixelFormat,
            primaries = mpv.string(handle, "video-params/primaries"),
            transfer = mpv.string(handle, "video-params/gamma"),
        )
    }

    /** The non-black rectangle of a frame, in pixels of the decoded picture. */
    data class ContentBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val frameWidth: Int,
        val frameHeight: Int,
    )

    /**
     * The picture area inside black bars, from FFmpeg's `bbox` filter at several positions (the union over the
     * samples, so a dark scene cannot crop real picture). Null when no sample produced a result.
     */
    fun detectContentBounds(file: File, durationSeconds: Double): ContentBounds? {
        val samples = SAMPLE_FRACTIONS.map { it * durationSeconds }
        val bounds = samples.mapNotNull { position -> runCatching { boundsAt(file, position) }.getOrNull() }
        if (bounds.isEmpty()) return null
        val left = bounds.minOf { it.x }
        val top = bounds.minOf { it.y }
        val right = bounds.maxOf { it.x + it.width }
        val bottom = bounds.maxOf { it.y + it.height }
        val first = bounds.first()
        return ContentBounds(left, top, right - left, bottom - top, first.frameWidth, first.frameHeight)
    }

    private fun boundsAt(file: File, positionSeconds: Double): ContentBounds? {
        val options = listOf(
            "vo" to "null",
            "ao" to "null",
            "aid" to "no",
            "sid" to "no",
            "hwdec" to "no",
            "pause" to "yes",
            "vf" to "@bars:lavfi=[bbox=min_val=$BLACK_LEVEL]",
            "start" to String.format(Locale.US, "%.3f", positionSeconds),
        )
        return withHandle(options) { mpv, handle ->
            mpv.mpv_command(handle, StringArray(arrayOf("loadfile", file.absolutePath, "replace")))
            check(awaitEvent(mpv, handle, EVENT_FILE_LOADED) == null) { "libmpv could not open ${file.name}" }
            // Step one frame so the filter has seen a decoded picture, then read the metadata it attached.
            mpv.mpv_command(handle, StringArray(arrayOf("frame-step")))
            val deadline = System.nanoTime() + TIMEOUT_NANOS
            var x1 = mpv.string(handle, "vf-metadata/bars/lavfi.bbox.x1")
            while (x1 == null && System.nanoTime() < deadline) {
                mpv.mpv_wait_event(handle, WAIT_SECONDS)
                x1 = mpv.string(handle, "vf-metadata/bars/lavfi.bbox.x1")
            }
            val left = x1?.toIntOrNull() ?: return@withHandle null
            val top = mpv.string(handle, "vf-metadata/bars/lavfi.bbox.y1")?.toIntOrNull() ?: return@withHandle null
            val right = mpv.string(handle, "vf-metadata/bars/lavfi.bbox.x2")?.toIntOrNull() ?: return@withHandle null
            val bottom = mpv.string(handle, "vf-metadata/bars/lavfi.bbox.y2")?.toIntOrNull() ?: return@withHandle null
            val frameWidth = mpv.string(handle, "video-params/w")?.toIntOrNull() ?: return@withHandle null
            val frameHeight = mpv.string(handle, "video-params/h")?.toIntOrNull() ?: return@withHandle null
            ContentBounds(left, top, right - left + 1, bottom - top + 1, frameWidth, frameHeight)
        }
    }

    /**
     * One JPEG frame of [file] at [positionSeconds], at most [maxWidth] wide, or null when none was decoded.
     * [filtersBeforeScale] is an optional libmpv `vf` chain applied first (the tests use it to compare deinterlacers).
     */
    fun captureFrame(
        file: File,
        positionSeconds: Double,
        maxWidth: Int,
        filtersBeforeScale: String? = null,
    ): ByteArray? {
        val outputDir = Files.createTempDirectory("vimivo-frame").toFile()
        return try {
            val options = listOf(
                "vo" to "image",
                "vo-image-format" to "jpg",
                "vo-image-jpeg-quality" to "85",
                "vo-image-outdir" to outputDir.absolutePath,
                "vf" to listOfNotNull(filtersBeforeScale, "lavfi=[scale=w='min($maxWidth,iw)':h=-2]")
                    .joinToString(","),
                "frames" to "1",
                "ao" to "null",
                "aid" to "no",
                "sid" to "no",
                "start" to String.format(Locale.US, "%.3f", positionSeconds),
            )
            withHandle(options) { mpv, handle ->
                mpv.mpv_command(handle, StringArray(arrayOf("loadfile", file.absolutePath, "replace")))
                awaitEvent(mpv, handle, EVENT_END_FILE)
                outputDir.listFiles { candidate -> candidate.extension == "jpg" }
                    ?.minByOrNull(File::getName)?.readBytes()?.takeIf(ByteArray::isNotEmpty)
            }
        } finally {
            outputDir.listFiles()?.forEach(File::delete)
            outputDir.delete()
        }
    }

    private fun <T> withHandle(options: List<Pair<String, String>>, block: (MpvNative, Pointer) -> T): T {
        val mpv = try {
            loadNative()
        } catch (error: LinkageError) {
            throw IllegalStateException("libmpv is not available: ${error.message}", error)
        }
        MpvLocale.ensureCNumeric()
        val handle = checkNotNull(mpv.mpv_create()) { "libmpv could not create a context" }
        try {
            (BASE_OPTIONS + options).forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            check(mpv.mpv_initialize(handle) >= 0) { "libmpv could not be initialized" }
            return block(mpv, handle)
        } finally {
            mpv.mpv_terminate_destroy(handle)
        }
    }

    /**
     * Null once [expected] arrives; otherwise why not: the file ended first (with libmpv's error) or time ran out.
     * `mpv_event` is { int event_id; int error; uint64 reply_userdata; void *data }, and END_FILE's data starts with
     * { int reason; int error }.
     */
    private fun awaitEvent(mpv: MpvNative, handle: Pointer, expected: Int): String? {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        var outcome: String? = "timed out"
        var waiting = true
        while (waiting && System.nanoTime() < deadline) {
            val event = mpv.mpv_wait_event(handle, WAIT_SECONDS) ?: continue
            val id = event.getInt(0)
            waiting = id != expected && id != EVENT_END_FILE && id != EVENT_SHUTDOWN
            outcome = when (id) {
                expected -> null
                EVENT_SHUTDOWN -> "libmpv shut down"
                EVENT_END_FILE -> {
                    val endFile = event.getPointer(EVENT_DATA_OFFSET)
                    val error = endFile?.getInt(Int.SIZE_BYTES.toLong()) ?: 0
                    "ended early (reason ${endFile?.getInt(0)}, ${mpv.mpv_error_string(error)})"
                }
                else -> outcome
            }
        }
        return outcome
    }

    private fun track(mpv: MpvNative, handle: Pointer, prefix: String) = Track(
        id = mpv.string(handle, "$prefix/id")?.toIntOrNull(),
        type = mpv.string(handle, "$prefix/type").orEmpty(),
        codec = mpv.string(handle, "$prefix/codec"),
        profile = mpv.string(handle, "$prefix/codec-profile"),
        albumArt = mpv.flag(handle, "$prefix/albumart"),
        width = mpv.string(handle, "$prefix/demux-w")?.toIntOrNull(),
        height = mpv.string(handle, "$prefix/demux-h")?.toIntOrNull(),
        fps = mpv.string(handle, "$prefix/demux-fps")?.toFloatOrNull(),
        sampleRate = mpv.string(handle, "$prefix/demux-samplerate")?.toIntOrNull(),
        channels = mpv.string(handle, "$prefix/demux-channel-count")?.toIntOrNull(),
        bitrate = mpv.string(handle, "$prefix/demux-bitrate")?.toIntOrNull(),
        language = mpv.string(handle, "$prefix/lang"),
        title = mpv.string(handle, "$prefix/title"),
        isDefault = mpv.flag(handle, "$prefix/default"),
        isForced = mpv.flag(handle, "$prefix/forced"),
        isExternal = mpv.flag(handle, "$prefix/external"),
    )

    private fun MpvNative.flag(handle: Pointer, property: String) = string(handle, property) == "yes"

    private fun MpvNative.string(handle: Pointer, property: String): String? {
        val pointer = mpv_get_property_string(handle, property) ?: return null
        return try {
            pointer.getString(0, "UTF-8")
        } finally {
            mpv_free(pointer)
        }
    }

    private companion object {
        const val EVENT_SHUTDOWN = 1
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_DATA_OFFSET = 16L
        const val WAIT_SECONDS = 0.1
        const val TIMEOUT_NANOS = 5_000_000_000L
        const val VIDEO_PARAMS_TIMEOUT_NANOS = 1_500_000_000L

        /** Where black-bar detection samples the video, as fractions of its duration. */
        val SAMPLE_FRACTIONS = listOf(0.2, 0.4, 0.6, 0.8)

        /** Luma at or below this counts as black (limited-range black is 16). */
        const val BLACK_LEVEL = 24
        const val MILLIS = 1_000.0

        // No configuration, scripts, terminal, network helpers or hardware decoding: deterministic and headless.
        val BASE_OPTIONS = listOf(
            "config" to "no",
            "terminal" to "no",
            "idle" to "yes",
            "hwdec" to "no",
            "audio-display" to "no",
            "keep-open" to "no",
        )

        // Paused, with null outputs, so the file stays loaded while its properties are read. Streams stay selected:
        // with none selected libmpv may end the file ("nothing to play") before reporting it loaded.
        val INSPECT_OPTIONS = listOf(
            "vo" to "null",
            "ao" to "null",
            "sid" to "no",
            "pause" to "yes",
        )
    }
}
