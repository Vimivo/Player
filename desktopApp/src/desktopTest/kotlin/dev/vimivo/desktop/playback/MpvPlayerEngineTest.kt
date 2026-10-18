package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.SupportLevel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MpvPlayerEngineTest {
    @Test
    fun reportsSupportedDesktopSourcesWithoutLoadingNativeLibrary() {
        val engine = MpvPlayerEngine()
        try {
            assertEquals(SupportLevel.PREFERRED, engine.supportFor(media("file:///tmp/song.flac")))
            assertEquals(SupportLevel.PREFERRED, engine.supportFor(media("https://example.test/video.mkv")))
            assertEquals(SupportLevel.PREFERRED, engine.supportFor(media("rtsp://camera.test/live")))
            assertEquals(SupportLevel.UNSUPPORTED, engine.supportFor(media("content://android-only/item")))
        } finally {
            engine.close()
        }
    }

    @Test
    fun handsLocalFilesToLibmpvAsPathsWhateverTheUriForm() {
        val file = Files.createTempFile("vimivo track", ".mp3").toFile().apply { deleteOnExit() }

        // File.toURI() yields file:/path, which libmpv cannot open; Path.toUri() yields file:///path.
        assertEquals(file.path, MpvPlayerEngine.mpvTarget(file.toURI().toString()))
        assertEquals(file.path, MpvPlayerEngine.mpvTarget(file.toPath().toUri().toString()))
    }

    @Test
    fun leavesNetworkUrlsUntouchedAndRejectsMalformedFileUris() {
        assertEquals("https://example.test/a b.m3u8", MpvPlayerEngine.mpvTarget("https://example.test/a b.m3u8"))
        assertFailsWith<IllegalArgumentException> { MpvPlayerEngine.mpvTarget("file:song.mp3") }
    }

    private fun media(uri: String) = MediaItem(MediaId(uri), uri, uri, MediaKind.STREAM)
}
