package dev.vimivo.platform.android.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDocumentsTest {
    @Test
    fun parsesSupportedReferencesAndIgnoresMetadataAndUnsafePaths() {
        val result = parseM3u(
            listOf(
                "#EXTM3U",
                "#EXTINF:10,Song",
                "content://media/audio/1",
                "https://example.test/stream",
                "/storage/emulated/0/private.mp3",
                "",
            ),
        )

        assertEquals(listOf("content://media/audio/1", "https://example.test/stream"), result)
    }
}
