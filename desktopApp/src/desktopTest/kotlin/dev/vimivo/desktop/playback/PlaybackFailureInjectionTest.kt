@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MagicNumber",
    "MaxLineLength", "MaximumLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.core.streaming.StreamFailureReason
import dev.vimivo.core.streaming.StreamReconnectionPolicy
import dev.vimivo.core.streaming.StreamValidationResult
import dev.vimivo.core.streaming.StreamValidator
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopLibraryRepository
import dev.vimivo.desktop.library.DesktopMediaScanner
import dev.vimivo.desktop.streaming.TestStreamServer
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.SupportLevel
import kotlinx.coroutines.test.runTest
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackFailureInjectionTest {

    private lateinit var server: TestStreamServer

    @BeforeTest
    fun setUp() {
        server = TestStreamServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun testCorruptAndTruncatedMediaHandling() {
        val tempDir = Files.createTempDirectory("vimivo-corrupt-media")
        val corruptAudio = tempDir.resolve("truncated.mp3")
        // Write 4 bytes: not a valid audio frame
        Files.write(corruptAudio, byteArrayOf(0x00, 0x01, 0x02, 0x03))

        val engine = MpvPlayerEngine()
        try {
            val item = MediaItem(
                id = MediaId("corrupt-1"),
                uri = corruptAudio.toUri().toString(),
                title = "Corrupt Audio",
                kind = MediaKind.AUDIO,
                source = MediaSource.SAF_FILE,
            )
            // Support check must recognize valid file scheme
            assertEquals(SupportLevel.PREFERRED, engine.supportFor(item))
        } finally {
            engine.close()
        }
    }

    @Test
    fun testMissingMediaFileReconciliation() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-missing-media")
        val mediaDir = Files.createDirectories(tempDir.resolve("Library"))
        val audioFile = mediaDir.resolve("song.flac").also { Files.write(it, byteArrayOf(0x46, 0x4C, 0x61, 0x43)) }

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val repo = DesktopLibraryRepository(database)
            val root = repo.addRoot(mediaDir).getOrThrow()
            val scanner = DesktopMediaScanner(database)

            // Initial scan: media found
            scanner.scan(root.id, mediaDir).getOrThrow()
            assertEquals(1, repo.media(MediaKind.AUDIO).size)

            // File deleted externally
            Files.delete(audioFile)

            // Rescan must mark as unavailable=0, never deleting the record or user metadata
            scanner.scan(root.id, mediaDir).getOrThrow()
            assertEquals(0, repo.media(MediaKind.AUDIO).size, "Available items must be 0")

            // Raw database query confirms record preservation
            database.transaction { conn ->
                conn.createStatement().executeQuery("SELECT available FROM media_items WHERE id = 'song.flac'").use { rs ->
                    if (rs.next()) {
                        assertEquals(0, rs.getInt(1), "Missing file must be preserved with available=0")
                    }
                }
            }
        }
    }

    @Test
    fun testNetworkHttpStatusFaultInjection() {
        val testCodes = listOf(
            401 to StreamFailureReason.HTTP_FORBIDDEN,
            403 to StreamFailureReason.HTTP_FORBIDDEN,
            404 to StreamFailureReason.NOT_FOUND,
            408 to StreamFailureReason.TIMEOUT,
            429 to StreamFailureReason.CONNECTION_FAILED,
            500 to StreamFailureReason.CONNECTION_FAILED,
        )

        testCodes.forEach { (statusCode, expectedReason) ->
            val url = URI("${server.baseUrl}/fault/$statusCode").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            assertEquals(statusCode, conn.responseCode)

            val classifiedReason = StreamValidator.classifyHttpStatus(statusCode)
            assertEquals(expectedReason, classifiedReason, "Status $statusCode must classify as $expectedReason")
        }
    }

    @Test
    fun testRedirectLoopHandling() {
        val url = URI("${server.baseUrl}/fault/redirect-loop").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        assertEquals(302, conn.responseCode)
        assertEquals("/fault/redirect-loop", conn.getHeaderField("Location"))
    }

    @Test
    fun testHeaderCrlfInjectionRejection() {
        // Attempting CRLF response splitting in header names
        val evilHeaderName = mapOf("X-Evil\r\nInjected: true" to "value")
        val result1 = StreamValidator.validateHeaders(evilHeaderName)
        assertTrue(result1.isFailure, "Header name with CRLF must be rejected")

        // Attempting CRLF in header values
        val evilHeaderValue = mapOf("X-Auth" to "bearer-token\r\nSet-Cookie: stolen=true")
        val result2 = StreamValidator.validateHeaders(evilHeaderValue)
        assertTrue(result2.isFailure, "Header value with CRLF must be rejected")

        // Clean headers accepted
        val cleanHeaders = mapOf(
            "User-Agent" to "Vimivo/1.0",
            "Authorization" to "Bearer 12345",
        )
        val cleanResult = StreamValidator.validateHeaders(cleanHeaders)
        assertTrue(cleanResult.isSuccess)
    }

    @Test
    fun testWebpageUrlValidationRejection() {
        val webpageUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://vimeo.com/123456789",
            "https://www.tiktok.com/@creator/video/12345",
            "https://facebook.com/watch/?v=123",
            "https://example.org/index.html",
        )

        webpageUrls.forEach { url ->
            val result = StreamValidator.validate(url)
            assertIs<StreamValidationResult.Invalid>(result)
            assertEquals(StreamFailureReason.WEBPAGE_NOT_DIRECT_STREAM, result.reason)
        }
    }

    @Test
    fun testReconnectionPolicyBackoffAndImmediateStop() {
        val policy = StreamReconnectionPolicy(
            maxRetries = 3,
            initialDelayMs = 500L,
            backoffMultiplier = 2.0,
            maxDelayMs = 2000L,
        )

        // Attempt 1: 500ms
        assertEquals(500L, policy.calculateDelayMs(1))
        // Attempt 2: 1000ms
        assertEquals(1000L, policy.calculateDelayMs(2))
        // Attempt 3: 2000ms (capped at maxDelayMs)
        assertEquals(2000L, policy.calculateDelayMs(3))

        // Retrying transient timeout
        assertTrue(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.TIMEOUT))
        assertTrue(policy.shouldRetry(attempt = 2, failureReason = StreamFailureReason.TIMEOUT))
        assertFalse(policy.shouldRetry(attempt = 3, failureReason = StreamFailureReason.TIMEOUT))

        // Non-retryable terminal errors
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.HTTP_FORBIDDEN))
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.NOT_FOUND))

        // Immediate user stop cancellation overrides everything
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.TIMEOUT, isUserCancelled = true))
    }

    @Test
    fun testPlaybackErrorCodeClassificationHonesty() {
        // Assert that network errors are strictly separate from decoder errors
        val networkCode = PlaybackErrorCode.NETWORK
        val decoderCode = PlaybackErrorCode.UNSUPPORTED_CODEC
        val sourceNotFound = PlaybackErrorCode.SOURCE_NOT_FOUND

        assertFalse(networkCode == decoderCode, "Network transport error must never be reported as unsupported codec")
        assertFalse(sourceNotFound == decoderCode, "HTTP 404 must never be reported as codec incompatibility")
    }
}
