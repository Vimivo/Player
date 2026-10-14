package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.SupportLevel
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun media(uri: String) = MediaItem(MediaId(uri), uri, uri, MediaKind.STREAM)
}
