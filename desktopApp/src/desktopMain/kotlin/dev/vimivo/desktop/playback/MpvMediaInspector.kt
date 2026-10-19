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
        val type: String,
        val codec: String?,
        val albumArt: Boolean,
        val width: Int?,
        val height: Int?,
        val fps: Float?,
        val sampleRate: Int?,
        val channels: Int?,
    )

    data class Inspection(
        val durationMillis: Long?,
        val fileFormat: String?,
        val title: String?,
        val tracks: List<Track>,
    )

    /** Container, duration, title and every track of [file], read while the file is loaded but paused. */
    fun inspect(file: File): Inspection = withHandle(INSPECT_OPTIONS) { mpv, handle ->
        mpv.mpv_command(handle, StringArray(arrayOf("loadfile", file.absolutePath, "replace")))
        val outcome = awaitEvent(mpv, handle, EVENT_FILE_LOADED)
        check(outcome == null) { "libmpv could not open ${file.name}: $outcome" }
        val count = mpv.string(handle, "track-list/count")?.toIntOrNull() ?: 0
        Inspection(
            durationMillis = mpv.string(handle, "duration")?.toDoubleOrNull()?.let { (it * MILLIS).toLong() },
            fileFormat = mpv.string(handle, "file-format"),
            title = mpv.string(handle, "metadata/by-key/title")?.takeIf(String::isNotBlank),
            tracks = (0 until count).map { index -> track(mpv, handle, "track-list/$index") },
        )
    }

    /** One JPEG frame of [file] at [positionSeconds], at most [maxWidth] wide, or null when none was decoded. */
    fun captureFrame(file: File, positionSeconds: Double, maxWidth: Int): ByteArray? {
        val outputDir = Files.createTempDirectory("vimivo-frame").toFile()
        return try {
            val options = listOf(
                "vo" to "image",
                "vo-image-format" to "jpg",
                "vo-image-jpeg-quality" to "85",
                "vo-image-outdir" to outputDir.absolutePath,
                "vf" to "lavfi=[scale=w='min($maxWidth,iw)':h=-2]",
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
        type = mpv.string(handle, "$prefix/type").orEmpty(),
        codec = mpv.string(handle, "$prefix/codec"),
        albumArt = mpv.string(handle, "$prefix/albumart") == "yes",
        width = mpv.string(handle, "$prefix/demux-w")?.toIntOrNull(),
        height = mpv.string(handle, "$prefix/demux-h")?.toIntOrNull(),
        fps = mpv.string(handle, "$prefix/demux-fps")?.toFloatOrNull(),
        sampleRate = mpv.string(handle, "$prefix/demux-samplerate")?.toIntOrNull(),
        channels = mpv.string(handle, "$prefix/demux-channel-count")?.toIntOrNull(),
    )

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
