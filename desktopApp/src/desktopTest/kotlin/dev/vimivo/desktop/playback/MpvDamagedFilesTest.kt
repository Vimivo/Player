package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 #3 with the real libmpv: damaged and incomplete files either play as far as they can or fail with a clear error
 * that reaches the UI state; they never hang silently. Fixtures are made from the checked-in test media.
 * Skipped where libmpv or the media is unavailable.
 */
class MpvDamagedFilesTest {
    private val mp4 = File("../testmedia/video_landscape.mp4").absoluteFile
    private val mkv = File("../testmedia/video_sample.mkv").absoluteFile
    private val folder = Files.createTempDirectory("vimivo-damaged").toFile().apply { deleteOnExit() }

    @Test
    fun aTruncatedMp4WithoutItsIndexFailsWithAClearError() = withEngine { engine ->
        val keep = (mp4.length() * TRUNCATED_FRACTION).toInt()
        val truncated = File(folder, "truncated.mp4").apply { writeBytes(mp4.readBytes().copyOf(keep)) }
        val started = System.nanoTime()
        runBlocking { engine.open(item(truncated)).getOrThrow() }
        engine.play().getOrThrow()
        val failed = awaitState(engine) { it.status is PlaybackStatus.Error }
        val elapsedMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertTrue(elapsedMillis < ERROR_BUDGET_MILLIS, "the error took $elapsedMillis ms to surface")
        val error = (failed.status as PlaybackStatus.Error).error
        assertEquals(PlaybackErrorCode.UNSUPPORTED_CONTAINER, error.code)
        assertTrue("damaged" in error.message, error.message)
    }

    @Test
    fun aFileWithADamagedMiddlePlaysOnAndNeverReportsAnError() = withEngine { engine ->
        val bytes = mkv.readBytes()
        val damaged = File(folder, "damaged-middle.mkv").apply {
            // Zero 8 KiB in the middle: a lost cluster, with the header and the tail intact.
            writeBytes(bytes.copyOf().also { java.util.Arrays.fill(it, bytes.size / 2, bytes.size / 2 + 8_192, 0) })
        }
        runBlocking { engine.open(item(damaged)).getOrThrow() }
        engine.play().getOrThrow()
        val moving = awaitState(engine) { it.positionMillis > 500 }
        assertTrue(moving.status !is PlaybackStatus.Error)
        engine.seekTo(((moving.durationMillis ?: 5_000) * 0.8).toLong()).getOrThrow()
        val later = awaitState(engine) { it.status is PlaybackStatus.Completed || it.positionMillis > 3_000 }
        assertTrue(later.status !is PlaybackStatus.Error, "${later.status}")
    }

    @Test
    fun aFileOfRandomBytesFailsInsteadOfHanging() = withEngine { engine ->
        val noise = File(folder, "noise.mkv").apply { writeBytes(ByteArray(64 * 1024) { (it * 7919).toByte() }) }
        runBlocking { engine.open(item(noise)).getOrThrow() }
        engine.play().getOrThrow()
        val failed = awaitState(engine) { it.status is PlaybackStatus.Error }
        assertEquals(PlaybackErrorCode.UNSUPPORTED_CONTAINER, (failed.status as PlaybackStatus.Error).error.code)
    }

    @Test
    fun aFileThatIsStillBeingWrittenKeepsPlayingAsItGrows() = withEngine { engine ->
        val bytes = mkv.readBytes()
        val growing = File(folder, "growing.mkv").apply { writeBytes(bytes.copyOf(INITIAL_BYTES)) }
        val writer = Thread {
            java.io.FileOutputStream(growing, true).use { out ->
                var written = INITIAL_BYTES
                while (written < bytes.size) {
                    val next = minOf(CHUNK_BYTES, bytes.size - written)
                    out.write(bytes, written, next)
                    out.flush()
                    written += next
                    Thread.sleep(CHUNK_DELAY_MILLIS)
                }
            }
        }
        runBlocking { engine.open(item(growing)).getOrThrow() }
        writer.start()
        engine.play().getOrThrow()
        val completed = awaitState(engine) { it.status is PlaybackStatus.Completed }
        writer.join()
        // Without following the file, playback would have ended at the partial end, long before the real end.
        assertTrue(completed.positionMillis >= EXPECTED_END_MILLIS, "ended at ${completed.positionMillis} ms")
    }

    private fun item(file: File): MediaItem {
        val uri = file.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, file.nameWithoutExtension, MediaKind.VIDEO, fileName = file.name)
    }

    private fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean): PlaybackState =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun withEngine(block: (MpvPlayerEngine) -> Unit) {
        if (!mp4.isFile || !mkv.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            engine.setVolume(0f)
            block(engine)
        }
    }

    private companion object {
        const val TIMEOUT = 30_000L
        const val ERROR_BUDGET_MILLIS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val TRUNCATED_FRACTION = 0.6
        const val INITIAL_BYTES = 40_000
        const val CHUNK_BYTES = 8_192
        const val CHUNK_DELAY_MILLIS = 400L
        const val EXPECTED_END_MILLIS = 4_500L
    }
}
