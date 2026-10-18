package dev.vimivo.app

import dev.vimivo.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMediaRequestTest {
    @Test
    fun opensLocalVideoWithItsDisplayName() {
        val request = ExternalMediaRequest.parse(VIEW, "content://downloads/42", "video/mp4", "Holiday.mp4")

        assertEquals(MediaKind.VIDEO, request?.media?.kind)
        assertEquals("Holiday", request?.media?.title)
        assertEquals("content://downloads/42", request?.media?.uri)
    }

    @Test
    fun fallsBackToTheUriNameAndDetectsAudio() {
        val request = ExternalMediaRequest.parse(VIEW, "file:///sdcard/Music/song.flac", "audio/flac", null)

        assertEquals(MediaKind.AUDIO, request?.media?.kind)
        assertEquals("song", request?.media?.title)
    }

    @Test
    fun ignoresNetworkLinksNonMediaTypesAndOtherActions() {
        assertNull(ExternalMediaRequest.parse(VIEW, "https://example.test/a.mp4", "video/mp4", null))
        assertNull(ExternalMediaRequest.parse(VIEW, "content://docs/1", "application/pdf", null))
        assertNull(ExternalMediaRequest.parse(VIEW, "content://docs/1", null, null))
        assertNull(ExternalMediaRequest.parse("android.intent.action.SEND", "content://docs/1", "video/mp4", null))
    }

    private companion object {
        const val VIEW = ExternalMediaRequest.ACTION_VIEW
    }
}
