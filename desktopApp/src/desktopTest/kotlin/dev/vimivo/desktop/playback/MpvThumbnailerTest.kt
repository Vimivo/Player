package dev.vimivo.desktop.playback

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Live libmpv: timeline previews decode a frame at the position; skipped where libmpv is unavailable. */
class MpvThumbnailerTest {
    private val video = File("../testmedia/video_landscape.mp4").absoluteFile

    @Test
    fun rendersASmallFrameAtThePositionAndCachesIt() = withThumbnailer { thumbnailer ->
        val first = assertNotNull(thumbnailer.thumbnail(video.absolutePath, 2_000))
        assertEquals(320, first.width)
        assertSame(first, thumbnailer.thumbnail(video.absolutePath, 2_000))
    }

    @Test
    fun missingFileGivesNoPreview() = withThumbnailer { thumbnailer ->
        assertNull(thumbnailer.thumbnail(File(video.parentFile, "missing.mp4").absolutePath, 1_000))
    }

    private fun withThumbnailer(block: suspend (MpvThumbnailer) -> Unit) {
        if (!video.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvThumbnailer().use { runBlocking { block(it) } }
    }
}
