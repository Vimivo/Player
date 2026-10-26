package dev.vimivo.platform.android.playback

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * V1: the seek bar draws real peaks. A clip that is loud for two seconds and ten times quieter for two more must give
 * tall bars then short ones; the result is cached by path, size and modified time and recomputed when the file changes.
 */
@RunWith(AndroidJUnit4::class)
class AndroidWaveformRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workDir: File
    private lateinit var repository: AndroidWaveformRepository

    @Before
    fun setUp() {
        workDir = File(context.cacheDir, "waveform-test-${System.nanoTime()}").apply { mkdirs() }
        repository = AndroidWaveformRepository(context, File(workDir, "cache"))
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private fun asset(name: String, to: String = name): File {
        val file = File(workDir, to)
        InstrumentationRegistry.getInstrumentation().context.assets.open("media/$name").use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file
    }

    @Test
    fun aLoudHalfThenAQuietHalfGivesTallBarsThenShortOnes() = runBlocking {
        val peaks = checkNotNull(repository.peaks(Uri.fromFile(asset("peaks_loud_then_quiet.flac"))))
        assertEquals(160, peaks.size)
        val loud = peaks.take(70).average()
        val quiet = peaks.drop(90).average()
        assertTrue("first half should be near the top, was $loud", loud > 0.9)
        assertTrue("second half should be about a tenth, was $quiet", quiet in 0.03..0.2)
        assertTrue(peaks.all { it in 0f..1f })
    }

    @Test
    fun anMp3IsDecodedToo() = runBlocking {
        val peaks = checkNotNull(repository.peaks(Uri.fromFile(asset("track2_lunar.mp3"))))
        assertEquals(160, peaks.size)
        assertTrue("normalised so the loudest bar is 1", peaks.max() > 0.99f)
    }

    @Test
    fun theSecondCallComesFromTheCacheAndAChangedFileIsDecodedAgain() = runBlocking {
        val file = asset("peaks_loud_then_quiet.flac", "song.flac")
        val uri = Uri.fromFile(file)
        val first = checkNotNull(repository.peaks(uri))
        val cached = File(workDir, "cache").listFiles().orEmpty()
        assertEquals("one cache file after the first call", 1, cached.size)
        val stamp = cached.single().lastModified()
        assertArrayEquals(quantised(first), quantised(checkNotNull(repository.peaks(uri))), 0f)
        assertEquals("the cache file was not rewritten", stamp, cached.single().lastModified())

        // Replace the file by another clip: different size and time, so it needs its own peaks.
        asset("track2_lunar.mp3").copyTo(file, overwrite = true)
        file.setLastModified(file.lastModified() + 5_000)
        val changed = checkNotNull(repository.peaks(uri))
        assertEquals("a second cache file for the new contents", 2, File(workDir, "cache").listFiles().orEmpty().size)
        assertFalse("different audio, different bars", quantised(first).contentEquals(quantised(changed)))
    }

    @Test
    fun aFileThatIsNotAudioGivesNoPeaksAndNoCrash() = runBlocking {
        val junk = File(workDir, "junk.mp3").apply { writeBytes(ByteArray(4_096) { (it * 31).toByte() }) }
        assertNull(repository.peaks(Uri.fromFile(junk)))
    }

    @Test
    fun anUnknownKindOfUriHasNoCacheKey() {
        assertNull(repository.cacheKey(Uri.parse("https://example.com/a.mp3")))
    }

    private fun quantised(peaks: FloatArray) = FloatArray(peaks.size) { Math.round(peaks[it] * 255) / 255f }
}
