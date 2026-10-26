package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import dev.vimivo.audio.waveform.PeakAccumulator
import dev.vimivo.audio.waveform.PeakCodec
import dev.vimivo.audio.waveform.WaveformKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * The real peaks of a track for the timeline. The audio is decoded in the background by a second, headless libmpv
 * handle (`ao=pcm`, which runs faster than real time, writing mono 8 kHz samples to a temporary file) through the
 * documented client API, then turned into peaks by the shared accumulator. Peaks are cached by path, size and modified
 * time under the user cache folder, so a changed file is decoded again and an unchanged one never is.
 */
internal class MpvWaveform(
    private val cacheDirectory: Path,
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    /** The peaks of [path], or null when it cannot be decoded, is silent or libmpv is unavailable. */
    suspend fun peaks(path: String): FloatArray? = withContext(worker) {
        val file = File(path)
        val cacheFile = cacheKey(file)?.let { cacheDirectory.resolve("$it.peaks").toFile() }
        val cached = cacheFile?.takeIf(File::isFile)?.let { PeakCodec.decode(it.readBytes()) }
        if (cached != null) return@withContext cached
        val peaks = decode(path)
        if (peaks != null && cacheFile != null) {
            runCatching {
                Files.createDirectories(cacheDirectory)
                cacheFile.writeBytes(PeakCodec.encode(peaks))
            }
        }
        peaks
    }

    internal fun cacheKey(file: File): String? {
        val usable = file.isFile && file.length() > 0
        return if (usable) WaveformKey.of(file.toURI().toString(), file.length(), file.lastModified()) else null
    }

    private suspend fun decode(path: String): FloatArray? {
        val mpv = runCatching { MpvNative.load() }.getOrNull()
        val context = mpv?.let {
            MpvLocale.ensureCNumeric()
            it.mpv_create()
        }
        if (mpv == null || context == null) return null
        val output = Files.createTempFile("vimivo-waveform", ".pcm").toFile()
        try {
            val decoded = configure(mpv, context, output) && run {
                mpv.mpv_command(context, StringArray(arrayOf("loadfile", path, "replace")))
                awaitEndOfFile(mpv, context)
            }
            return if (decoded) peaksOf(output) else null
        } finally {
            mpv.mpv_terminate_destroy(context)
            output.delete()
        }
    }

    private fun configure(mpv: MpvNative, context: Pointer, output: File): Boolean {
        val options = listOf(
            "config" to "no",
            "terminal" to "no",
            "vo" to "null",
            "vid" to "no",
            "sid" to "no",
            "ao" to "pcm",
            "ao-pcm-file" to output.absolutePath,
            "ao-pcm-waveheader" to "no",
            "audio-channels" to "mono",
            "audio-format" to "s16",
            "audio-samplerate" to SAMPLE_RATE.toString(),
            "idle" to "yes",
            "keep-open" to "no",
        )
        options.forEach { (name, value) -> mpv.mpv_set_option_string(context, name, value) }
        return mpv.mpv_initialize(context) >= 0
    }

    /** Waits for the end-of-file event; false when the file failed to load or the work was cancelled. */
    private suspend fun awaitEndOfFile(mpv: MpvNative, context: Pointer): Boolean {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (currentCoroutineContext().isActive && System.nanoTime() < deadline) {
            val event = mpv.mpv_wait_event(context, WAIT_SECONDS) ?: continue
            if (event.getInt(0) == EVENT_END_FILE) return event.getPointer(DATA_OFFSET)?.getInt(0) == REASON_EOF
        }
        return false
    }

    private fun peaksOf(output: File): FloatArray? {
        val accumulator = PeakAccumulator(chunkFrames = CHUNK_FRAMES)
        val block = ShortArray(BLOCK_SAMPLES)
        output.inputStream().buffered().use { input ->
            val bytes = ByteArray(BLOCK_SAMPLES * Short.SIZE_BYTES)
            while (true) {
                val read = input.read(bytes)
                if (read <= 0) break
                val count = read / Short.SIZE_BYTES
                val samples = ByteBuffer.wrap(bytes, 0, count * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                samples.asShortBuffer().get(block, 0, count)
                accumulator.add(block, count, 1)
            }
        }
        return accumulator.finish()
    }

    private companion object {
        const val EVENT_END_FILE = 7

        // mpv_event is { event_id, error, reply_userdata, data }: `data` points at mpv_event_end_file, whose first
        // field is the reason.
        const val DATA_OFFSET = 16L
        const val REASON_EOF = 0
        const val WAIT_SECONDS = 0.1
        const val TIMEOUT_NANOS = 600_000_000_000L
        const val SAMPLE_RATE = 8_000
        const val CHUNK_FRAMES = 64
        const val BLOCK_SAMPLES = 16_384
    }
}
