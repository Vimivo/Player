package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopMediaMetadataExtractorTest {
    @Test
    fun extractsVerifiedWaveFormatAndDuration() = runTest {
        val file = Files.createTempFile("vimivo-metadata", ".wav").toFile()
        val format = AudioFormat(8_000f, 16, 1, true, false)
        val pcm = ByteArray(16_000)
        AudioInputStream(ByteArrayInputStream(pcm), format, pcm.size.toLong() / format.frameSize).use { input ->
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, file)
        }

        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("wave"), file.toURI().toString(), "Wave", MediaKind.AUDIO),
        ).getOrThrow()

        assertEquals(8_000, metadata.sampleRate)
        assertEquals(1, metadata.channels)
        assertNotNull(metadata.durationMillis)
        assertEquals(1_000, metadata.durationMillis)
        assertEquals("WAVE", metadata.container)
    }
}
