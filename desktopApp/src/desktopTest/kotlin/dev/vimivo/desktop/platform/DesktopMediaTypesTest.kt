package dev.vimivo.desktop.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMediaTypesTest {
    @Test
    fun coversEverySpecifiedExtension() {
        val video = "mp4 m4v mkv webm avi mov wmv flv ts m2ts mts mpg mpeg vob ogv 3gp 3g2 divx rmvb asf f4v hevc"
        val audio = "mp3 m4a aac flac alac wav aiff aif ogg oga opus wma ape wv dsf dff mka ac3 dts amr"
        assertEquals(video.split(' ').toSet(), DesktopMediaTypes.extensions(MediaFamily.VIDEO))
        assertEquals(audio.split(' ').toSet(), DesktopMediaTypes.extensions(MediaFamily.AUDIO))
        assertEquals(setOf("m3u", "m3u8", "pls", "xspf", "cue"), DesktopMediaTypes.extensions(MediaFamily.PLAYLIST))
        assertEquals(setOf("srt", "ass", "ssa", "vtt", "sub"), DesktopMediaTypes.extensions(MediaFamily.SUBTITLE))
    }

    @Test
    fun classifiesFilesCaseInsensitively() {
        assertEquals(MediaFamily.VIDEO, DesktopMediaTypes.familyOf(File("Movie.MKV")))
        assertEquals(MediaFamily.PLAYLIST, DesktopMediaTypes.familyOf(File("mix.m3u8")))
        assertNull(DesktopMediaTypes.familyOf(File("notes.txt")))
    }

    @Test
    fun linuxDesktopEntryAdvertisesEveryMimeTypeAndAcceptsMultipleFiles() {
        val entry = File("src/desktopMain/resources/linux/dev.vimivo.desktop.desktop").readLines()
        val mimeLine = entry.first { it.startsWith("MimeType=") }.removePrefix("MimeType=")
        val advertised = mimeLine.split(';').filter(String::isNotBlank)
        assertEquals(DesktopMediaTypes.all.map(MediaType::mimeType).toSet(), advertised.toSet())
        val acceptsFiles = entry.any { it.startsWith("Exec=") && it.endsWith("%F") }
        assertTrue(acceptsFiles, "Exec must take %F so selections open together")
    }
}
