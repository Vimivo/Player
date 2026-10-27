package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotOptionsTest {
    @Test
    fun defaultsAreAPlainPngInTheDefaultFolder() {
        val options = SnapshotOptions()
        assertEquals(SnapshotFormat.PNG, options.format)
        assertFalse(options.includeSubtitles)
        assertEquals(null, options.folder)
    }

    @Test
    fun nameCarriesTitlePositionAndTimestamp() {
        assertEquals(
            "My Film-1h02m03s-1700000000000.png",
            SnapshotNaming.fileName("My Film", 3_723_999, 1_700_000_000_000, SnapshotFormat.PNG),
        )
    }

    @Test
    fun jpegUsesTheJpgExtension() {
        assertTrue(SnapshotNaming.fileName("x", 0, 1, SnapshotFormat.JPEG).endsWith(".jpg"))
    }

    @Test
    fun charactersFileSystemsRejectAreRemoved() {
        val name = SnapshotNaming.fileName("a/b\\c:d*e?f\"g<h>i|j", 5_000, 7, SnapshotFormat.PNG)
        assertEquals("a b c d e f g h i j-0h00m05s-7.png", name)
    }

    @Test
    fun anEmptyOrDotOnlyTitleFallsBackToVimivo() {
        assertTrue(SnapshotNaming.fileName(null, 0, 1, SnapshotFormat.PNG).startsWith("vimivo-"))
        assertTrue(SnapshotNaming.fileName("  ..  ", 0, 1, SnapshotFormat.PNG).startsWith("vimivo-"))
    }

    @Test
    fun longTitlesAreShortenedAndNegativePositionsClamped() {
        val name = SnapshotNaming.fileName("x".repeat(200), -5, 1, SnapshotFormat.PNG)
        assertTrue(name.startsWith("x".repeat(60) + "-0h00m00s-"))
        assertFalse("x".repeat(61) in name)
    }

    @Test
    fun bengaliTitlesSurvive() {
        assertTrue(SnapshotNaming.fileName("বাংলা ভিডিও", 0, 1, SnapshotFormat.PNG).startsWith("বাংলা ভিডিও-"))
    }
}
