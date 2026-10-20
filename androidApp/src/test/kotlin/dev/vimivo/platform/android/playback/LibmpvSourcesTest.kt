package dev.vimivo.platform.android.playback

import dev.vimivo.settings.DecoderPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibmpvSourcesTest {
    private val opened = mutableListOf<String>()
    private val sources = LibmpvSources { uri ->
        opened += uri
        42
    }

    @Test
    fun fileUrisBecomeDecodedPaths() {
        assertEquals(
            "/storage/emulated/0/Movies/My film.mkv",
            sources.resolve("file:///storage/emulated/0/Movies/My%20film.mkv"),
        )
    }

    @Test
    fun contentUrisAreOpenedAndHandedOverAsAClosingDescriptor() {
        val uri = "content://com.android.providers.media.documents/document/video%3A12"
        assertEquals("fdclose://42", sources.resolve(uri))
        assertEquals(listOf(uri), opened)
    }

    @Test
    fun plainNetworkUrlsPassThrough() {
        assertEquals("http://example.test/a.ts", sources.resolve("http://example.test/a.ts"))
        assertEquals("rtsp://cam.test/live", sources.resolve("rtsp://cam.test/live"))
    }

    @Test
    fun schemesWithoutTlsOrSupportStayWithMedia3() {
        assertFalse(LibmpvSources.isSupported("https://example.test/a.mp4"))
        assertFalse(LibmpvSources.isSupported("smb://nas/share/a.mkv"))
        assertFalse(LibmpvSources.isSupported("/sdcard/no-scheme.mkv"))
        assertTrue(LibmpvSources.isSupported("CONTENT://media/external/video/1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolvingAnUnsupportedSchemeFails() {
        sources.resolve("https://example.test/a.mp4")
    }

    @Test
    fun decoderPreferenceMapsToLibmpvHwdec() {
        assertEquals("no", LibmpvPlayerEngine.hwdecFor(DecoderPreference.SOFTWARE))
        assertEquals("mediacodec-copy", LibmpvPlayerEngine.hwdecFor(DecoderPreference.HARDWARE))
        assertEquals("no", LibmpvPlayerEngine.hwdecFor(DecoderPreference.AUTOMATIC))
    }
}
