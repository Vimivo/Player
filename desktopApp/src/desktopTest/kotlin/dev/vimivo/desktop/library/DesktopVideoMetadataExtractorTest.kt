@file:Suppress(
    "ArgumentListWrapping", "Indentation", "LongMethod", "MaximumLineLength", "MaxLineLength",
    "ParameterListWrapping", "PropertyWrapping", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopVideoMetadataExtractorTest {
    @Test
    fun extractsMp4VideoMetadataAndGeneratesThumbnail() = runTest {
        val file = fixture("sample.mp4")
        val tempDir = Files.createTempDirectory("vimivo-video-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir, thumbnailDirectory = tempDir)
        val extractor = DesktopMediaMetadataExtractor(cache)

        val metadata = extractor.extract(
            MediaItem(MediaId("mp4"), file.toURI().toString(), "Sample MP4", MediaKind.VIDEO, sizeBytes = file.length(), dateModifiedEpochSeconds = 1000L),
        ).getOrThrow()

        assertEquals("বাংলা ভিডিও - Sample MP4", metadata.title)
        assertEquals(320, metadata.width)
        assertEquals(240, metadata.height)
        assertEquals("h264", metadata.videoCodec)
        assertEquals("aac", metadata.audioCodec)
        assertEquals("MP4", metadata.container)
        assertEquals(1, metadata.audioTrackCount)
        assertNotNull(metadata.durationMillis)
        assertTrue(metadata.durationMillis in 400L..600L)
        assertNotNull(metadata.thumbnailUri)
        val thumbFile = File(URI(metadata.thumbnailUri))
        assertTrue(thumbFile.isFile, "Generated thumbnail file must exist on disk")
        assertTrue(thumbFile.length() > 0, "Generated thumbnail file must not be empty")
    }

    @Test
    fun extractsMkvVideoMetadata() = runTest {
        val file = fixture("sample.mkv")
        val tempDir = Files.createTempDirectory("vimivo-mkv-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir, thumbnailDirectory = tempDir)
        val extractor = DesktopMediaMetadataExtractor(cache)

        val metadata = extractor.extract(
            MediaItem(MediaId("mkv"), file.toURI().toString(), "Sample MKV", MediaKind.VIDEO, sizeBytes = file.length(), dateModifiedEpochSeconds = 1000L),
        ).getOrThrow()

        assertEquals(320, metadata.width)
        assertEquals(240, metadata.height)
        assertEquals("h264", metadata.videoCodec)
        assertEquals("aac", metadata.audioCodec)
        assertEquals("MKV", metadata.container)
        assertNotNull(metadata.thumbnailUri)
    }

    @Test
    fun extractsWebmVideoMetadata() = runTest {
        val file = fixture("sample.webm")
        val tempDir = Files.createTempDirectory("vimivo-webm-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir, thumbnailDirectory = tempDir)
        val extractor = DesktopMediaMetadataExtractor(cache)

        val metadata = extractor.extract(
            MediaItem(MediaId("webm"), file.toURI().toString(), "Sample WebM", MediaKind.VIDEO, sizeBytes = file.length(), dateModifiedEpochSeconds = 1000L),
        ).getOrThrow()

        assertEquals(320, metadata.width)
        assertEquals(240, metadata.height)
        assertEquals("vp8", metadata.videoCodec)
        assertEquals("opus", metadata.audioCodec)
        assertEquals("WEBM", metadata.container)
        assertNotNull(metadata.thumbnailUri)
    }

    @Test
    fun extractsShortVideoMetadata() = runTest {
        val file = fixture("short.mp4")
        val tempDir = Files.createTempDirectory("vimivo-short-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir, thumbnailDirectory = tempDir)
        val extractor = DesktopMediaMetadataExtractor(cache)

        val metadata = extractor.extract(
            MediaItem(MediaId("short"), file.toURI().toString(), "Short Video", MediaKind.VIDEO, sizeBytes = file.length(), dateModifiedEpochSeconds = 1000L),
        ).getOrThrow()

        assertNotNull(metadata.durationMillis)
        assertTrue(metadata.durationMillis in 100L..250L)
        assertNotNull(metadata.thumbnailUri)
    }

    @Test
    fun extractsVideoWithoutAudio() = runTest {
        val file = fixture("no_audio.mp4")
        val tempDir = Files.createTempDirectory("vimivo-noaudio-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir, thumbnailDirectory = tempDir)
        val extractor = DesktopMediaMetadataExtractor(cache)

        val metadata = extractor.extract(
            MediaItem(MediaId("noaudio"), file.toURI().toString(), "No Audio", MediaKind.VIDEO, sizeBytes = file.length(), dateModifiedEpochSeconds = 1000L),
        ).getOrThrow()

        assertEquals(320, metadata.width)
        assertEquals(240, metadata.height)
        assertEquals("h264", metadata.videoCodec)
        assertNull(metadata.audioCodec)
        assertEquals(0, metadata.audioTrackCount)
    }

    @Test
    fun handlesCorruptVideoGracefullyWithoutCrash() = runTest {
        val file = fixture("corrupt.mp4")
        val result = DesktopMediaMetadataExtractor().extract(
            MediaItem(MediaId("corrupt"), file.toURI().toString(), "Corrupt", MediaKind.VIDEO, sizeBytes = file.length()),
        )

        assertTrue(result.isFailure, "Corrupted video file extraction must return failure result rather than crashing JVM")
    }

    @Test
    fun thumbnailCacheReusesExistingCachedThumbnail() = runTest {
        val file = fixture("sample.mp4")
        val tempDir = Files.createTempDirectory("vimivo-cache-hit-test")
        val cache = DesktopMediaCache(artworkDirectory = tempDir, thumbnailDirectory = tempDir)
        val extractor = DesktopMediaMetadataExtractor(cache)
        val media = MediaItem(MediaId("hit"), file.toURI().toString(), "Hit", MediaKind.VIDEO, sizeBytes = file.length(), dateModifiedEpochSeconds = 1000L)

        val first = extractor.extract(media).getOrThrow()
        val second = extractor.extract(media).getOrThrow()

        assertNotNull(first.thumbnailUri)
        assertEquals(first.thumbnailUri, second.thumbnailUri)
    }

    private fun fixture(name: String): File {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/video/$name")
            ?: error("Fixture not found in resources: fixtures/video/$name")
        val extension = name.substringAfterLast('.', "")
        val temp = Files.createTempFile("vimivo-video-fixture-", ".$extension").toFile()
        temp.deleteOnExit()
        stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        return temp
    }
}
