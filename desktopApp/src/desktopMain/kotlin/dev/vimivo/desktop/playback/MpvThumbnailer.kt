package dev.vimivo.desktop.playback

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Timeline hover previews (spec §3.1) from a second, headless libmpv handle: `vo=image` writes one small JPEG at
 * the requested position (`frames=1`), with no audio, subtitles or window. Documented client API only, no shell.
 * Requests run one at a time on their own worker; results are cached per file and position.
 */
internal class MpvThumbnailer(
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : AutoCloseable {
    private var native: MpvNative? = null
    private var handle: Pointer? = null
    private val outputDir: File by lazy {
        Files.createTempDirectory(
            "vimivo-previews"
        ).toFile().apply { deleteOnExit() }
    }
    private val cache = object : LinkedHashMap<String, ImageBitmap>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > CACHE_SIZE
    }

    @Volatile
    private var closed = false

    /** A preview of [path] at [positionMillis], or null when it cannot be decoded (a failure never surfaces). */
    suspend fun thumbnail(path: String, positionMillis: Long): ImageBitmap? = withContext(worker) {
        if (closed) return@withContext null
        val key = "$path@$positionMillis"
        cache[key] ?: runCatching { render(path, positionMillis) }.getOrNull()?.also { cache[key] = it }
    }

    private fun render(path: String, positionMillis: Long): ImageBitmap? {
        val (mpv, context) = ensureHandle() ?: return null
        outputDir.listFiles()?.forEach(File::delete)
        mpv.mpv_set_property_string(context, "start", (positionMillis / MILLIS_PER_SECOND).toString())
        mpv.mpv_command(context, StringArray(arrayOf("loadfile", path, "replace")))
        awaitEndOfFile(mpv, context)
        val frame = outputDir.listFiles { file -> file.extension == "jpg" }?.minByOrNull(File::getName) ?: return null
        return ImageIO.read(frame)?.toComposeImageBitmap()
    }

    /** Waits until libmpv reports the end of the file (one frame written) or the timeout passes. */
    private fun awaitEndOfFile(mpv: MpvNative, context: Pointer) {
        val deadline = System.nanoTime() + RENDER_TIMEOUT_NANOS
        var ended = false
        while (!ended && System.nanoTime() < deadline) {
            ended = mpv.mpv_wait_event(context, WAIT_SECONDS)?.getInt(0) == EVENT_END_FILE
        }
    }

    private fun ensureHandle(): Pair<MpvNative, Pointer>? {
        val existing = handle
        val loaded = native
        if (existing != null && loaded != null) return loaded to existing
        val mpv = MpvNative.load()
        return mpv.mpv_create()?.let { created -> initialize(mpv, created) }
    }

    private fun initialize(mpv: MpvNative, created: Pointer): Pair<MpvNative, Pointer>? {
        val options = listOf(
            "config" to "no",
            "terminal" to "no",
            "vo" to "image",
            "vo-image-format" to "jpg",
            "vo-image-jpeg-quality" to "75",
            "vo-image-outdir" to outputDir.absolutePath,
            "vf" to "scale=$PREVIEW_WIDTH:-2",
            "frames" to "1",
            "aid" to "no",
            "sid" to "no",
            "audio-display" to "no",
            "hwdec" to "no",
            "hr-seek" to "no",
            "idle" to "yes",
            "keep-open" to "no",
        )
        options.forEach { (name, value) -> mpv.mpv_set_option_string(created, name, value) }
        if (mpv.mpv_initialize(created) < 0) {
            mpv.mpv_terminate_destroy(created)
            return null
        }
        native = mpv
        handle = created
        return mpv to created
    }

    override fun close() {
        closed = true
        // Run on the worker so a render in progress finishes before the handle goes away.
        kotlinx.coroutines.runBlocking(worker) {
            handle?.let { native?.mpv_terminate_destroy(it) }
            handle = null
            native = null
            outputDir.listFiles()?.forEach(File::delete)
        }
    }

    private companion object {
        const val EVENT_END_FILE = 7
        const val WAIT_SECONDS = 0.1
        const val RENDER_TIMEOUT_NANOS = 3_000_000_000L
        const val MILLIS_PER_SECOND = 1_000.0
        const val PREVIEW_WIDTH = 320
        const val CACHE_SIZE = 120
        const val LOAD_FACTOR = 0.75f
    }
}
