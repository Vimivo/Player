package dev.vimivo.platform.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMatchingTest {
    @Test
    fun acceptsExactAndLanguageSuffixedMatches() {
        assertTrue(isSubtitleMatch("movie.srt", "movie"))
        assertTrue(isSubtitleMatch("movie.en.srt", "movie"))
        assertTrue(isSubtitleMatch("movie.bn.ass", "movie"))
        assertTrue(isSubtitleMatch("MOVIE.webVTT", "movie").not())
        assertFalse(isSubtitleMatch("another.en.srt", "movie"))
    }

    @Test
    fun mapsSupportedFormatsAndRejectsUnknownOnes() {
        assertEquals(AndroidSubtitleRepository.MIME_SRT, subtitleMimeType("srt"))
        assertEquals(AndroidSubtitleRepository.MIME_ASS, subtitleMimeType("ssa"))
        assertEquals(AndroidSubtitleRepository.MIME_WEBVTT, subtitleMimeType("vtt"))
        assertNull(subtitleMimeType("sub"))
    }

    @Test
    fun extractsOnlyExplicitLanguageSuffixes() {
        assertEquals("en", subtitleLanguageFromName("movie.en.srt"))
        assertEquals("pt-br", subtitleLanguageFromName("movie.pt-BR.vtt"))
        assertNull(subtitleLanguageFromName("movie.srt"))
    }
}
