@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LrcLyricsParserTest {

    @Test
    fun testParseStandardSynchronizedLyrics() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [al:Test Album]
            [offset:200]
            [00:01.00]First line
            [00:05.50]Second line
            [00:10.00]Third line
        """.trimIndent()

        val doc = LrcLyricsParser.parse(lrc)
        assertTrue(doc.synchronized)
        assertEquals("Test Song", doc.metadata["ti"])
        assertEquals("Test Artist", doc.metadata["ar"])
        assertEquals(200L, doc.offsetMillis)

        // Raw 1.00s + 200ms offset = 1200ms
        assertEquals(3, doc.lines.size)
        assertEquals(1_200L, doc.lines[0].timeMillis)
        assertEquals("First line", doc.lines[0].text)
        assertEquals(5_700L, doc.lines[1].timeMillis)
        assertEquals("Second line", doc.lines[1].text)
        assertEquals(10_200L, doc.lines[2].timeMillis)
        assertEquals("Third line", doc.lines[2].text)
    }

    @Test
    fun testMultipleTimestampsPerLine() {
        val lrc = """
            [00:02.00][00:08.00]Repeated chorus phrase
            [00:05.00]Middle bridge
        """.trimIndent()

        val doc = LrcLyricsParser.parse(lrc)
        assertTrue(doc.synchronized)
        assertEquals(3, doc.lines.size)
        assertEquals(2_000L, doc.lines[0].timeMillis)
        assertEquals("Repeated chorus phrase", doc.lines[0].text)
        assertEquals(5_000L, doc.lines[1].timeMillis)
        assertEquals("Middle bridge", doc.lines[1].text)
        assertEquals(8_000L, doc.lines[2].timeMillis)
        assertEquals("Repeated chorus phrase", doc.lines[2].text)
    }

    @Test
    fun testUnicodeAndBengaliLyrics() {
        val lrc = """
            [00:03.50]আমার সোনার বাংলা
            [00:07.00]আমি তোমায় ভালোবাসি
        """.trimIndent()

        val doc = LrcLyricsParser.parse(lrc)
        assertTrue(doc.synchronized)
        assertEquals(2, doc.lines.size)
        assertEquals("আমার সোনার বাংলা", doc.lines[0].text)
        assertEquals(3_500L, doc.lines[0].timeMillis)
        assertEquals("আমি তোমায় ভালোবাসি", doc.lines[1].text)
        assertEquals(7_000L, doc.lines[1].timeMillis)
    }

    @Test
    fun testActiveLineIndexBinarySearch() {
        val lrc = """
            [00:02.00]Line 1
            [00:06.00]Line 2
            [00:10.00]Line 3
        """.trimIndent()

        val doc = LrcLyricsParser.parse(lrc)

        // Before first line
        assertEquals(-1, doc.findActiveLineIndex(1_000L))

        // At exactly first line
        assertEquals(0, doc.findActiveLineIndex(2_000L))

        // Between first and second line
        assertEquals(0, doc.findActiveLineIndex(4_000L))

        // At second line
        assertEquals(1, doc.findActiveLineIndex(6_000L))

        // Past third line
        assertEquals(2, doc.findActiveLineIndex(15_000L))
    }

    @Test
    fun testPlainUnsynchronizedLyrics() {
        val plainText = """
            First plain verse without tags
            Second plain verse without tags
        """.trimIndent()

        val doc = LrcLyricsParser.parse(plainText)
        assertFalse(doc.synchronized)
        assertEquals(2, doc.lines.size)
        assertEquals(null, doc.lines[0].timeMillis)
        assertEquals(-1, doc.findActiveLineIndex(5_000L))
    }

    @Test
    fun testTimestampedInstrumentalBreak() {
        val lrc = """
            [00:02.00]Vocal intro
            [00:06.00]
            [00:12.00]Second verse after guitar solo
        """.trimIndent()

        val doc = LrcLyricsParser.parse(lrc)
        assertTrue(doc.synchronized)
        assertEquals(3, doc.lines.size)

        assertEquals("Vocal intro", doc.lines[0].text)
        assertEquals(2_000L, doc.lines[0].timeMillis)

        // Instrumental break line has empty text
        assertEquals("", doc.lines[1].text)
        assertEquals(6_000L, doc.lines[1].timeMillis)

        assertEquals("Second verse after guitar solo", doc.lines[2].text)
        assertEquals(12_000L, doc.lines[2].timeMillis)

        // During guitar solo (at 8s), active line should be index 1 (blank line), clearing vocals
        assertEquals(1, doc.findActiveLineIndex(8_000L))
        assertEquals("", doc.lines[doc.findActiveLineIndex(8_000L)].text)
    }
}
