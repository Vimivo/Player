package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Converts or copies a media file with libmpv's own encoder path (`--o`, `--of`, `--oac`, `--ovc`), so it needs no
 * ffmpeg program on the user's computer. Documented client API only: no shell, no filename interpolation.
 * The LGPL build has no x264/x265, so "convert" targets are audio and stream-copy (remux); a target whose codec the
 * bundled FFmpeg lacks fails with libmpv's own message instead of producing a broken file.
 */
internal class MpvTranscoder(private val loadNative: () -> MpvNative = MpvNative::load) {

    enum class Target(val label: String, val extension: String, val summary: String, internal val options: List<Pair<String, String>>) {
        M4A("M4A audio (AAC)", "m4a", "Music only, plays almost everywhere", listOf("vid" to "no", "of" to "ipod", "oac" to "aac", "oacopts" to "b=192k")),
        FLAC("FLAC audio (lossless)", "flac", "Music only, no quality loss", listOf("vid" to "no", "of" to "flac", "oac" to "flac")),
        WAV("WAV audio", "wav", "Music only, uncompressed", listOf("vid" to "no", "of" to "wav", "oac" to "pcm_s16le")),
        MP3("MP3 audio", "mp3", "Music only, if this build includes the MP3 encoder", listOf("vid" to "no", "of" to "mp3", "oac" to "libmp3lame", "oacopts" to "b=192k")),
        MKV_COPY("MKV, same quality", "mkv", "Rewrap video and audio without re-encoding", listOf("of" to "matroska", "ovc" to "copy", "oac" to "copy")),
        MP4_COPY("MP4, same quality", "mp4", "Rewrap video and audio without re-encoding", listOf("of" to "mp4", "ovc" to "copy", "oac" to "copy")),
    }

    /**
     * Writes [source] converted to [target] next to [outputDirectory] (a free name is chosen) and returns the new file.
     * [onProgress] gets 0..1 when the duration is known. Return true from [cancelled] to stop; the partial file is removed.
     */
    fun convert(
        source: File,
        target: Target,
        outputDirectory: File,
        onProgress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): Result<File> = runCatching {
        require(source.isFile) { "The source file is not available" }
        outputDirectory.mkdirs()
        val finalFile = freeName(outputDirectory, source.nameWithoutExtension, target.extension)
        val partial = File(outputDirectory, ".${finalFile.name}.part")
        try {
            run(source, partial, target, onProgress, cancelled)
            check(partial.isFile && partial.length() > 0) { "libmpv produced no output" }
            Files.move(partial.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            finalFile
        } finally {
            partial.delete()
        }
    }

    private fun run(source: File, partial: File, target: Target, onProgress: (Float) -> Unit, cancelled: () -> Boolean) {
        val mpv = try {
            loadNative()
        } catch (error: LinkageError) {
            throw IllegalStateException("libmpv is not available: ${error.message}", error)
        }
        MpvLocale.ensureCNumeric()
        val handle = checkNotNull(mpv.mpv_create()) { "libmpv could not create a context" }
        try {
            val options = BASE_OPTIONS + target.options + ("o" to partial.absolutePath)
            options.forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            check(mpv.mpv_initialize(handle) >= 0) { "libmpv could not be initialized" }
            mpv.mpv_command(handle, StringArray(arrayOf("loadfile", source.absolutePath, "replace")))
            waitForEnd(mpv, handle, onProgress, cancelled)
        } finally {
            mpv.mpv_terminate_destroy(handle)
        }
    }

    private fun waitForEnd(mpv: MpvNative, handle: Pointer, onProgress: (Float) -> Unit, cancelled: () -> Boolean) {
        while (true) {
            if (cancelled()) throw java.util.concurrent.CancellationException("Conversion cancelled")
            val event = mpv.mpv_wait_event(handle, POLL_SECONDS) ?: continue
            when (event.getInt(0)) {
                EVENT_SHUTDOWN -> error("libmpv shut down")
                EVENT_END_FILE -> {
                    val data = event.getPointer(EVENT_DATA_OFFSET)
                    val reason = data?.getInt(0) ?: 0
                    val code = data?.getInt(Int.SIZE_BYTES.toLong()) ?: 0
                    check(reason == END_REASON_EOF) { "Conversion failed: ${mpv.mpv_error_string(code)}" }
                    onProgress(1f)
                    return
                }
                else -> reportProgress(mpv, handle, onProgress)
            }
        }
    }

    private fun reportProgress(mpv: MpvNative, handle: Pointer, onProgress: (Float) -> Unit) {
        val position = mpv.string(handle, "time-pos")?.toDoubleOrNull() ?: return
        val duration = mpv.string(handle, "duration")?.toDoubleOrNull()?.takeIf { it > 0 } ?: return
        onProgress((position / duration).toFloat().coerceIn(0f, 0.99f))
    }

    private fun MpvNative.string(handle: Pointer, property: String): String? {
        val pointer = mpv_get_property_string(handle, property) ?: return null
        return try {
            pointer.getString(0, "UTF-8")
        } finally {
            mpv_free(pointer)
        }
    }

    companion object {
        /** `name.ext`, then `name (1).ext`, ... the first that does not exist in [directory]. */
        fun freeName(directory: File, stem: String, extension: String): File {
            var candidate = File(directory, "$stem.$extension")
            var n = 1
            while (candidate.exists()) candidate = File(directory, "$stem ($n).$extension").also { n++ }
            return candidate
        }

        private const val POLL_SECONDS = 0.25
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_END_FILE = 7
        private const val EVENT_DATA_OFFSET = 16L
        private const val END_REASON_EOF = 0

        // Headless: no config, scripts, terminal or hardware decoding; encode as fast as the input can be read.
        private val BASE_OPTIONS = listOf(
            "config" to "no",
            "terminal" to "no",
            "idle" to "yes",
            "hwdec" to "no",
            "audio-display" to "no",
            "keep-open" to "no",
            "ao" to "null",
            "vo" to "null",
            "sid" to "no",
        )
    }
}
