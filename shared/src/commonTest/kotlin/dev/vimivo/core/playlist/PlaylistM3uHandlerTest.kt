@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.playlist

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistM3uHandlerTest {

    @Test
    fun testParseM3uWithMetadata() {
        val m3uContent = """
            #EXTM3U
            #PLAYLIST:Rock Classics
            #EXTINF:215,Queen - Bohemian Rhapsody
            file:///Music/queen.mp3
            #EXT-X-TARGETDURATION:10
            #EXTINF:180,The Beatles - Yesterday
            file:///Music/yesterday.flac
        """.trimIndent()

        val parsed = PlaylistM3uHandler.parseM3u(m3uContent)
        assertEquals("Rock Classics", parsed.name)
        assertEquals(2, parsed.entries.size)

        assertEquals("file:///Music/queen.mp3", parsed.entries[0].uri)
        assertEquals("Queen - Bohemian Rhapsody", parsed.entries[0].title)
        assertEquals(215, parsed.entries[0].durationSeconds)

        assertEquals("file:///Music/yesterday.flac", parsed.entries[1].uri)
        assertEquals("The Beatles - Yesterday", parsed.entries[1].title)
        assertEquals(180, parsed.entries[1].durationSeconds)
    }

    @Test
    fun testExportM3u8RoundTrip() {
        val originalEntries = listOf(
            PlaylistItemEntry(
                uri = "file:///Music/track1.mp3",
                title = "Track One",
                durationSeconds = 120,
            ),
            PlaylistItemEntry(
                uri = "file:///Music/track2.opus",
                title = "Track Two",
                durationSeconds = 300,
            ),
        )

        val exported = PlaylistM3uHandler.exportM3u8("My Exported Playlist", originalEntries)
        val reparsed = PlaylistM3uHandler.parseM3u(exported)

        assertEquals("My Exported Playlist", reparsed.name)
        assertEquals(2, reparsed.entries.size)
        assertEquals("Track One", reparsed.entries[0].title)
        assertEquals(120, reparsed.entries[0].durationSeconds)
        assertEquals("file:///Music/track1.mp3", reparsed.entries[0].uri)
    }
}
