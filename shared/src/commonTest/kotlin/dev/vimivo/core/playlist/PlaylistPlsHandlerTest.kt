package dev.vimivo.core.playlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistPlsHandlerTest {
    @Test
    fun parsesEntriesInIndexOrderWithTitlesAndLengths() {
        val pls = """
            [playlist]
            File2=b.mp3
            Title2=Second
            File1=/music/a.flac
            Title1=First
            Length1=212
            Length2=-1
            NumberOfEntries=2
            Version=2
        """.trimIndent()

        val parsed = PlaylistPlsHandler.parse(pls, "Mix")

        assertEquals(
            listOf(PlaylistItemEntry("/music/a.flac", "First", 212), PlaylistItemEntry("b.mp3", "Second", null)),
            parsed.entries,
        )
    }

    @Test
    fun dispatchesByExtension() {
        assertEquals(1, PlaylistFormats.parse("list.M3U8", "#EXTM3U\nsong.mp3")?.entries?.size)
        assertEquals("list", PlaylistFormats.parse("list.pls", "File1=a.mp3")?.name)
        assertNull(PlaylistFormats.parse("album.cue", "FILE \"a.flac\" WAVE"))
    }
}
