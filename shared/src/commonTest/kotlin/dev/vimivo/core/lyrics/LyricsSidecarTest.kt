package dev.vimivo.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsSidecarTest {
    @Test
    fun anExactNameBeatsALanguageVariant() {
        val names = listOf("Song.en.lrc", "Song.lrc", "Other.lrc")
        assertEquals("Song.lrc", LyricsSidecar.best("Song.mp3", names))
    }

    @Test
    fun aLanguageVariantIsUsedWhenThereIsNoExactName() {
        assertEquals("Song.bn.lrc", LyricsSidecar.best("Song.flac", listOf("Song.en.lrc", "Song.bn.lrc")))
    }

    @Test
    fun matchingIgnoresCaseAndOtherFilesAreIgnored() {
        assertEquals("SONG.LRC", LyricsSidecar.best("song.mp3", listOf("song.txt", "SONG.LRC", "song.mp3")))
        assertNull(LyricsSidecar.best("Song.mp3", listOf("Song.txt", "Songbook.lrc")))
    }

    @Test
    fun aNameThatOnlyStartsWithTheStemIsNotAVariant() {
        assertNull(LyricsSidecar.best("Song.mp3", listOf("Song 2.lrc", "Songs.lrc")))
    }

    @Test
    fun aMediaNameWithoutAnExtensionStillMatches() {
        assertEquals("Track.lrc", LyricsSidecar.best("Track", listOf("Track.lrc")))
    }
}
