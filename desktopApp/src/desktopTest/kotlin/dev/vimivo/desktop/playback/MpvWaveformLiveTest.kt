package dev.vimivo.desktop.playback

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The timeline draws real peaks, decoded by a second libmpv handle: a clip that is loud for two seconds and ten times
 * quieter for two more gives tall then short bars; the result is cached by path, size and modified time. Skipped where
 * libmpv or the media is missing.
 */
class MpvWaveformLiveTest {
    private val clip = File("../testmedia/peaks_loud_then_quiet.flac").absoluteFile
    private val other = File("../testmedia/track2_lunar.mp3").absoluteFile

    private fun available() = clip.isFile && other.isFile && runCatching { MpvNative.load() }.isSuccess

    @Test
    fun aLoudHalfThenAQuietHalfGivesTallBarsThenShortOnes() = runBlocking {
        if (!available()) return@runBlocking println("Skipping: libmpv or test media unavailable")
        val cache = Files.createTempDirectory("vimivo-waveform-test")
        try {
            val peaks = assertNotNull(MpvWaveform(cache).peaks(clip.path))
            assertEquals(160, peaks.size)
            assertTrue(peaks.take(70).average() > 0.9, "first half: ${peaks.take(70).average()}")
            assertTrue(peaks.drop(90).average() in 0.03..0.2, "second half: ${peaks.drop(90).average()}")
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun theSecondCallComesFromTheCacheAndAChangedFileIsDecodedAgain() = runBlocking {
        if (!available()) return@runBlocking println("Skipping: libmpv or test media unavailable")
        val dir = Files.createTempDirectory("vimivo-waveform-test").toFile()
        try {
            val song = File(dir, "song.flac").also { clip.copyTo(it) }
            val cacheDir = File(dir, "cache")
            val waveform = MpvWaveform(cacheDir.toPath())
            val first = assertNotNull(waveform.peaks(song.path))
            val cached = cacheDir.listFiles().orEmpty()
            assertEquals(1, cached.size)
            val stamp = cached.single().lastModified()
            assertEquals(first.size, assertNotNull(waveform.peaks(song.path)).size)
            assertEquals(stamp, cached.single().lastModified(), "the cache file was not rewritten")

            other.copyTo(song, overwrite = true)
            song.setLastModified(song.lastModified() + 5_000)
            val changed = assertNotNull(waveform.peaks(song.path))
            assertEquals(2, cacheDir.listFiles().orEmpty().size)
            assertNotEquals(first.map { (it * 255).toInt() }, changed.map { (it * 255).toInt() })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aFileThatIsNotAudioGivesNoPeaks() = runBlocking {
        if (!available()) return@runBlocking println("Skipping: libmpv or test media unavailable")
        val dir = Files.createTempDirectory("vimivo-waveform-test").toFile()
        try {
            val junk = File(dir, "junk.mp3").apply { writeBytes(ByteArray(4_096) { (it * 31).toByte() }) }
            assertNull(MpvWaveform(File(dir, "cache").toPath()).peaks(junk.path))
        } finally {
            dir.deleteRecursively()
        }
    }
}
