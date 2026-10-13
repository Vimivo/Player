package dev.vimivo.platform.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun `parses and orders synchronized LRC lines`() {
        val lyrics = parseLrc("[00:12.50]Second\n[00:01.25][00:03.00]First")
        assertTrue(lyrics.synchronized)
        assertEquals(listOf(1_250L, 3_000L, 12_500L), lyrics.lines.map { it.timeMillis })
        assertEquals(listOf("First", "First", "Second"), lyrics.lines.map { it.text })
    }

    @Test
    fun `accepts plain lyrics without inventing timestamps`() {
        val lyrics = parseLrc("Line one\nLine two")
        assertEquals(false, lyrics.synchronized)
        assertEquals(listOf(null, null), lyrics.lines.map { it.timeMillis })
    }
}
