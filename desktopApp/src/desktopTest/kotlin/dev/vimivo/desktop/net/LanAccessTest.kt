package dev.vimivo.desktop.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LanAccessTest {
    @Test
    fun tokensAreUniqueAndUrlSafe() {
        val tokens = List(50) { LanAccess.newToken() }.toSet()
        assertEquals(50, tokens.size)
        assertTrue(tokens.all { t -> t.length >= 24 && t.all { it.isLetterOrDigit() || it == '-' || it == '_' } })
    }

    @Test
    fun tokenComparison() {
        val token = LanAccess.newToken()
        assertTrue(LanAccess.tokenMatches(token, token))
        assertFalse(LanAccess.tokenMatches(token, null))
        assertFalse(LanAccess.tokenMatches(token, ""))
        assertFalse(LanAccess.tokenMatches(token, token + "x"))
        assertNotEquals(token, LanAccess.newToken())
    }

    @Test
    fun queryParsingDecodesAndKeepsFirstDuplicate() {
        val q = LanAccess.parseQuery("name=a%20b.mp4&t=abc&t=zzz&flag")
        assertEquals("a b.mp4", q["name"])
        assertEquals("abc", q["t"])
        assertEquals("", q["flag"])
        assertTrue(LanAccess.parseQuery(null).isEmpty())
    }

    @Test
    fun jsonEscapeHandlesQuotesBackslashesAndControlCharacters() {
        assertEquals("""a\"b\\c\nd\u0001""", LanAccess.jsonEscape("a\"b\\c\nd\u0001"))
    }
}
