package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.SnapshotOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 #16 with the real libmpv: PNG and JPEG by extension. Burning subtitles in needs a real video output;
 * the smoke test proves that in the actual window (`#snapshot-subtitles`).
 */
class MpvSnapshotLiveTest {
    private val folder = Files.createTempDirectory("vimivo-snapshots").toFile().apply { deleteOnExit() }

    @Test
    fun theExtensionPicksPngOrJpeg() = withClip("video_landscape.mp4", position = 700) { engine ->
        val png = File(folder, "frame.png")
        val jpeg = File(folder, "frame.jpg")
        engine.takeSnapshot(png.absolutePath, SnapshotOptions()).getOrThrow()
        engine.takeSnapshot(jpeg.absolutePath, SnapshotOptions()).getOrThrow()
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), png.readBytes().take(4).map { it.toInt() and 0xFF })
        assertEquals(listOf(0xFF, 0xD8, 0xFF), jpeg.readBytes().take(3).map { it.toInt() and 0xFF })
        assertTrue(ImageIO.read(png).width > 0 && ImageIO.read(jpeg).width > 0)
    }

    private fun withClip(name: String, position: Long, block: (MpvPlayerEngine) -> Unit) {
        val file = File("../testmedia/$name").absoluteFile
        if (!file.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            val uri = file.toPath().toUri().toString()
            runBlocking {
                engine.setVolume(0f)
                engine.open(MediaItem(MediaId(uri), uri, name, MediaKind.VIDEO, fileName = name)).getOrThrow()
                engine.play().getOrThrow()
                withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > position } }
                delay(SETTLE)
            }
            block(engine)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val SETTLE = 100L
    }
}
