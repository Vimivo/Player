@file:Suppress(
    "ArgumentListWrapping", "Indentation", "LongMethod", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("WAV", metadata.container)
    }

    @Test
    fun extractsTaggedMp3MetadataWithUnicodeAndBengaliTags() = runTest {
        val file = fixture("tagged.mp3")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("mp3"), file.toURI().toString(), "Mp3", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertEquals("আমার গান - Bengali Song", metadata.title)
        assertEquals("শিল্পী - Bengali Artist", metadata.artist)
        assertEquals("অ্যালবাম - Bengali Album", metadata.album)
        assertEquals("মূল শিল্পী - Bengali Album Artist", metadata.albumArtist)
        assertEquals("Folk", metadata.genre)
        assertEquals(3, metadata.trackNumber)
        assertEquals(1, metadata.discNumber)
        assertEquals(2026, metadata.releaseYear)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(2, metadata.channels)
        assertNotNull(metadata.durationMillis)
        val duration = metadata.durationMillis ?: 0L
        assertTrue(duration in 400L..600L)
    }

    @Test
    fun extractsEmbeddedArtworkFromMp3() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-art-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir)
        val file = fixture("artwork.mp3")
        val extractor = DesktopMediaMetadataExtractor(cache)
        val metadata = extractor.extract(
            MediaItem(MediaId("art-mp3"), file.toURI().toString(), "Art", MediaKind.AUDIO, sizeBytes = file.length(), dateModifiedEpochSeconds = 123456L),
        ).getOrThrow()

        assertEquals("চিত্রসহ গান - Artwork Song", metadata.title)
        assertEquals("শিল্পী - Artwork Artist", metadata.artist)
        assertEquals("অ্যালবাম - Artwork Album", metadata.album)
        assertNotNull(metadata.artworkUri)
        val artFile = File(URI(metadata.artworkUri))
        assertTrue(artFile.isFile, "Cached artwork file must exist")
        assertTrue(artFile.length() > 0, "Cached artwork file must not be empty")
    }

    @Test
    fun extractsTaggedFlacMetadata() = runTest {
        val file = fixture("tagged.flac")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("flac"), file.toURI().toString(), "Flac", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertEquals("আমার গান - Bengali Song", metadata.title)
        assertEquals("শিল্পী - Bengali Artist", metadata.artist)
        assertEquals("অ্যালবাম - Bengali Album", metadata.album)
        assertEquals("Folk", metadata.genre)
        assertEquals(3, metadata.trackNumber)
        assertEquals(2026, metadata.releaseYear)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(2, metadata.channels)
        assertEquals("FLAC", metadata.container)
    }

    @Test
    fun extractsTaggedM4aMetadata() = runTest {
        val file = fixture("tagged.m4a")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("m4a"), file.toURI().toString(), "M4a", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertEquals("আমার গান - Bengali Song", metadata.title)
        assertEquals("শিল্পী - Bengali Artist", metadata.artist)
        assertEquals("অ্যালবাম - Bengali Album", metadata.album)
        assertEquals("Folk", metadata.genre)
        assertEquals(3, metadata.trackNumber)
        assertEquals(1, metadata.discNumber)
        assertEquals(2026, metadata.releaseYear)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(2, metadata.channels)
    }

    @Test
    fun extractsTaggedOggVorbisMetadata() = runTest {
        val file = fixture("tagged.ogg")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("ogg"), file.toURI().toString(), "Ogg", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertEquals("আমার গান - Bengali Song", metadata.title)
        assertEquals("শিল্পী - Bengali Artist", metadata.artist)
        assertEquals("অ্যালবাম - Bengali Album", metadata.album)
        assertEquals("Folk", metadata.genre)
        assertEquals(2026, metadata.releaseYear)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(2, metadata.channels)
    }

    @Test
    fun extractsTaggedOpusMetadata() = runTest {
        val file = fixture("tagged.opus")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("opus"), file.toURI().toString(), "Opus", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertEquals("আমার গান - Bengali Song", metadata.title)
        assertEquals("শিল্পী - Bengali Artist", metadata.artist)
        assertEquals("অ্যালবাম - Bengali Album", metadata.album)
        assertEquals("Folk", metadata.genre)
        assertEquals(2026, metadata.releaseYear)
    }

    @Test
    fun extractsAiffAudioMetadata() = runTest {
        val file = fixture("test.aiff")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("aiff"), file.toURI().toString(), "Aiff", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertEquals(44100, metadata.sampleRate)
        assertEquals(1, metadata.channels)
    }

    @Test
    fun handlesAudioWithMissingTagsGracefully() = runTest {
        val file = fixture("missing_tags.mp3")
        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("missing-tags"), file.toURI().toString(), "No Tags", MediaKind.AUDIO, sizeBytes = file.length()),
        ).getOrThrow()

        assertNull(metadata.artist)
        assertNull(metadata.album)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(2, metadata.channels)
    }

    @Test
    fun handlesCorruptedFileGracefullyWithoutCrash() = runTest {
        val file = fixture("corrupted.mp3")
        val result = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("corrupted"), file.toURI().toString(), "Corrupt", MediaKind.AUDIO, sizeBytes = file.length()),
        )

        assertTrue(result.isFailure, "Corrupted file extraction should return failure result rather than crashing JVM")
    }

    @Test
    fun handlesZeroByteAudioFileGracefully() = runTest {
        val emptyFile = Files.createTempFile("vimivo-zero-", ".mp3").toFile()
        emptyFile.deleteOnExit()
        val result = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("zero"), emptyFile.toURI().toString(), "Zero Byte", MediaKind.AUDIO, sizeBytes = 0L),
        )

        assertTrue(result.isFailure, "Zero-byte file extraction should return failure result gracefully")
    }

    @Test
    fun extractsMetadataFromUnicodeAndBengaliFilenames() = runTest {
        val original = fixture("tagged.mp3")
        val unicodeDir = Files.createTempDirectory("vimivo-বাংলা-test")
        val unicodeFile = unicodeDir.resolve("আমার বাংলা গান — রবীন্দ্রনাথ.mp3").toFile()
        original.copyTo(unicodeFile, overwrite = true)
        unicodeFile.deleteOnExit()

        val metadata = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("bengali-file"), unicodeFile.toURI().toString(), unicodeFile.nameWithoutExtension, MediaKind.AUDIO, sizeBytes = unicodeFile.length()),
        ).getOrThrow()

        assertEquals("আমার গান - Bengali Song", metadata.title)
        assertEquals("শিল্পী - Bengali Artist", metadata.artist)
    }

    private fun fixture(name: String): File {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/audio/$name")
            ?: error("Fixture not found in resources: fixtures/audio/$name")
        val extension = name.substringAfterLast('.', "")
        val temp = Files.createTempFile("vimivo-fixture-", ".$extension").toFile()
        temp.deleteOnExit()
        stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        return temp
    }
}
