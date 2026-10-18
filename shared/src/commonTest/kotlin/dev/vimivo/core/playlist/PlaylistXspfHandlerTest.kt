@file:Suppress("ArgumentListWrapping", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.core.playlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistXspfHandlerTest {
    @Test
    fun parsesVlcStyleXspf() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <playlist xmlns="http://xspf.org/ns/0/" xmlns:vlc="http://www.videolan.org/vlc/playlist/ns/0/" version="1">
              <title>Road Trip &amp; Chill</title>
              <trackList>
                <track>
                  <location>file:///Users/me/Music/Song%20One.mp3</location>
                  <title>Song &lt;One&gt; &#233;</title>
                  <duration>215000</duration>
                  <extension application="http://www.videolan.org/vlc/playlist/0"><vlc:id>0</vlc:id></extension>
                </track>
                <track><location><![CDATA[https://example.com/live.m3u8?a=1&b=2]]></location></track>
                <track><title>No location is skipped</title></track>
              </trackList>
            </playlist>
        """.trimIndent()
        val parsed = PlaylistXspfHandler.parse(xml)
        assertEquals("Road Trip & Chill", parsed.name)
        assertEquals(2, parsed.entries.size)
        assertEquals(PlaylistItemEntry("file:///Users/me/Music/Song%20One.mp3", "Song <One> é", 215), parsed.entries[0])
        assertEquals("https://example.com/live.m3u8?a=1&b=2", parsed.entries[1].uri)
        assertNull(parsed.entries[1].title)
    }

    @Test
    fun trackTitlesDoNotBecomePlaylistName() {
        val xml = """<playlist version="1"><trackList><track><location>a.mp3</location><title>Track</title></track></trackList></playlist>"""
        assertEquals("Fallback", PlaylistXspfHandler.parse(xml, "Fallback").name)
    }

    @Test
    fun exportRoundTripsAndEscapes() {
        val entries = listOf(
            PlaylistItemEntry("file:///a%26b.flac", "Rock & Roll \"Live\"", 61),
            PlaylistItemEntry("https://example.com/s?x=1&y=2", null, null),
        )
        val xml = PlaylistXspfHandler.export("Mine <1>", entries)
        assertTrue("&amp;" in xml && "<title>Mine &lt;1&gt;</title>" in xml)
        assertTrue("<duration>61000</duration>" in xml)
        val parsed = PlaylistXspfHandler.parse(xml)
        assertEquals("Mine <1>", parsed.name)
        assertEquals(entries, parsed.entries)
    }

    @Test
    fun doubleEscapedTextDecodesOnce() {
        val xml = """<playlist version="1"><title>&amp;lt;tag&amp;gt;</title><trackList/></playlist>"""
        assertEquals("&lt;tag&gt;", PlaylistXspfHandler.parse(xml).name)
    }

    @Test
    fun rejectsDtdAndNonPlaylists() {
        assertFailsWith<IllegalArgumentException> {
            PlaylistXspfHandler.parse("""<!DOCTYPE x [<!ENTITY a "aaaa">]><playlist><title>&a;</title></playlist>""")
        }
        assertFailsWith<IllegalArgumentException> { PlaylistXspfHandler.parse("<html></html>") }
        assertTrue(PlaylistXspfHandler.isXspf("List.XSPF"))
    }
}
