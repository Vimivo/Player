@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.core.streaming

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreamValidatorTest {

    @Test
    fun testDirectVideoUrlValidation() {
        val result = StreamValidator.validate("https://cdn.example.org/videos/nature_documentary.mp4")
        assertIs<StreamValidationResult.Valid>(result)
        assertEquals(StreamProtocol.HTTPS_PROGRESSIVE, result.protocol)
        assertEquals(StreamType.VOD, result.type)
        assertFalse(result.isLive)
        assertEquals("nature documentary", result.displayTitle)
    }

    @Test
    fun testDirectAudioUrlValidation() {
        val result = StreamValidator.validate("https://audio.example.org/songs/symphony_no_5.flac")
        assertIs<StreamValidationResult.Valid>(result)
        assertEquals(StreamProtocol.HTTPS_PROGRESSIVE, result.protocol)
        assertEquals(StreamType.VOD, result.type)
        assertEquals("symphony no 5", result.displayTitle)
    }

    @Test
    fun testHlsManifestValidation() {
        val vodResult = StreamValidator.validate("https://stream.example.org/hls/master.m3u8")
        assertIs<StreamValidationResult.Valid>(vodResult)
        assertEquals(StreamProtocol.HLS, vodResult.protocol)
        assertEquals(StreamType.VOD, vodResult.type)

        val liveResult = StreamValidator.validate("https://stream.example.org/hls/live/stream.m3u8")
        assertIs<StreamValidationResult.Valid>(liveResult)
        assertEquals(StreamProtocol.HLS, liveResult.protocol)
        assertEquals(StreamType.LIVE, liveResult.type)
        assertTrue(liveResult.isLive)
    }

    @Test
    fun testDashManifestValidation() {
        val result = StreamValidator.validate("https://cdn.example.org/dash/presentation.mpd")
        assertIs<StreamValidationResult.Valid>(result)
        assertEquals(StreamProtocol.DASH, result.protocol)
        assertEquals(StreamType.VOD, result.type)
        assertFalse(result.isLive)
    }

    @Test
    fun testRtspStreamValidation() {
        val result = StreamValidator.validate("rtsp://192.168.1.50:554/live/ch0")
        assertIs<StreamValidationResult.Valid>(result)
        assertEquals(StreamProtocol.RTSP, result.protocol)
        assertEquals(StreamType.LIVE, result.type)
        assertTrue(result.isLive)
    }

    @Test
    fun testInternetRadioValidation() {
        val result = StreamValidator.validate("http://stream.radioparadise.com:8000/mp3-128")
        assertIs<StreamValidationResult.Valid>(result)
        assertEquals(StreamProtocol.INTERNET_RADIO, result.protocol)
        assertEquals(StreamType.LIVE, result.type)
        assertTrue(result.isLive)
    }

    @Test
    fun testWebpageUrlRejection() {
        val ytResult = StreamValidator.validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertIs<StreamValidationResult.Invalid>(ytResult)
        assertEquals(StreamFailureReason.WEBPAGE_NOT_DIRECT_STREAM, ytResult.reason)
        assertTrue(ytResult.message.contains("webpage, not a direct playable stream"))

        val vimeoResult = StreamValidator.validate("https://vimeo.com/123456789")
        assertIs<StreamValidationResult.Invalid>(vimeoResult)
        assertEquals(StreamFailureReason.WEBPAGE_NOT_DIRECT_STREAM, vimeoResult.reason)

        val htmlResult = StreamValidator.validate("https://example.org/media/player.html")
        assertIs<StreamValidationResult.Invalid>(htmlResult)
        assertEquals(StreamFailureReason.WEBPAGE_NOT_DIRECT_STREAM, htmlResult.reason)
    }

    @Test
    fun testInvalidAndLocalSchemesRejection() {
        val fileResult = StreamValidator.validate("file:///Users/user/Movies/video.mp4")
        assertIs<StreamValidationResult.Invalid>(fileResult)
        assertEquals(StreamFailureReason.UNSUPPORTED_PROTOCOL, fileResult.reason)

        val jsResult = StreamValidator.validate("javascript:alert(1)")
        assertIs<StreamValidationResult.Invalid>(jsResult)
        assertEquals(StreamFailureReason.UNSUPPORTED_PROTOCOL, jsResult.reason)

        val noSchemeResult = StreamValidator.validate("just-a-plain-string")
        assertIs<StreamValidationResult.Invalid>(noSchemeResult)
        assertEquals(StreamFailureReason.INVALID_URL, noSchemeResult.reason)

        val blankResult = StreamValidator.validate("   ")
        assertIs<StreamValidationResult.Invalid>(blankResult)
        assertEquals(StreamFailureReason.INVALID_URL, blankResult.reason)
    }

    @Test
    fun testReconnectionPolicyCalculations() {
        val policy = StreamReconnectionPolicy(
            maxRetries = 3,
            initialDelayMs = 1000L,
            backoffMultiplier = 2.0,
            maxDelayMs = 5000L,
        )

        assertEquals(1000L, policy.calculateDelayMs(1))
        assertEquals(2000L, policy.calculateDelayMs(2))
        assertEquals(4000L, policy.calculateDelayMs(3))
        assertEquals(5000L, policy.calculateDelayMs(4))

        // Recoverable network timeout
        assertTrue(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.TIMEOUT))
        assertTrue(policy.shouldRetry(attempt = 2, failureReason = StreamFailureReason.CONNECTION_FAILED))
        assertFalse(policy.shouldRetry(attempt = 3, failureReason = StreamFailureReason.TIMEOUT))

        // Non-recoverable errors
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.HTTP_FORBIDDEN))
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.NOT_FOUND))
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.EXPIRED_URL))

        // User cancellation overrides retry
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.TIMEOUT, isUserCancelled = true))
    }

    @Test
    fun testToStreamSourceMapping() {
        val hlsResult = StreamValidator.validate("https://example.com/live.m3u8")
        assertIs<StreamValidationResult.Valid>(hlsResult)
        val hlsSource = StreamValidator.toStreamSource(hlsResult)
        assertIs<StreamSource.HlsStream>(hlsSource)
        assertEquals("https://example.com/live.m3u8", hlsSource.url)
        assertEquals(StreamProtocol.HLS, hlsSource.protocol)
        assertTrue(hlsSource.isLive)

        val directResult = StreamValidator.validate("https://example.com/video.mp4")
        assertIs<StreamValidationResult.Valid>(directResult)
        val directSource = StreamValidator.toStreamSource(directResult)
        assertIs<StreamSource.DirectHttpMedia>(directSource)
        assertEquals("https://example.com/video.mp4", directSource.url)
        assertFalse(directSource.isLive)

        val rtspResult = StreamValidator.validate("rtsp://10.0.0.1:554/stream")
        assertIs<StreamValidationResult.Valid>(rtspResult)
        val rtspSource = StreamValidator.toStreamSource(rtspResult)
        assertIs<StreamSource.RtspStream>(rtspSource)
        assertTrue(rtspSource.isLive)

        val radioResult = StreamValidator.validate("http://stream.radio.org:8000/live")
        assertIs<StreamValidationResult.Valid>(radioResult)
        val radioSource = StreamValidator.toStreamSource(radioResult)
        assertIs<StreamSource.InternetRadio>(radioSource)
        assertTrue(radioSource.isLive)
    }

    @Test
    fun testHttpStatusClassification() {
        assertEquals(StreamFailureReason.HTTP_FORBIDDEN, StreamValidator.classifyHttpStatus(401))
        assertEquals(StreamFailureReason.HTTP_FORBIDDEN, StreamValidator.classifyHttpStatus(403))
        assertEquals(StreamFailureReason.NOT_FOUND, StreamValidator.classifyHttpStatus(404))
        assertEquals(StreamFailureReason.TIMEOUT, StreamValidator.classifyHttpStatus(408))
        assertEquals(StreamFailureReason.EXPIRED_URL, StreamValidator.classifyHttpStatus(410))
        assertEquals(StreamFailureReason.CONNECTION_FAILED, StreamValidator.classifyHttpStatus(429))
        assertEquals(StreamFailureReason.CONNECTION_FAILED, StreamValidator.classifyHttpStatus(500))
        assertEquals(StreamFailureReason.CONNECTION_FAILED, StreamValidator.classifyHttpStatus(503))
        assertEquals(StreamFailureReason.UNKNOWN, StreamValidator.classifyHttpStatus(200))
    }

    @Test
    fun testHeaderValidation() {
        val validHeaders = mapOf(
            "User-Agent" to "Vimivo/1.0",
            "Authorization" to "Bearer token123",
            "Referer" to "https://example.com",
        )
        val validated = StreamValidator.validateHeaders(validHeaders)
        assertTrue(validated.isSuccess)
        assertEquals(3, validated.getOrNull()?.size)

        // Blank header name rejected
        assertTrue(StreamValidator.validateHeaders(mapOf("" to "value")).isFailure)

        // Header name with illegal characters (colon, CRLF) rejected
        assertTrue(StreamValidator.validateHeaders(mapOf("Header:Name" to "value")).isFailure)
        assertTrue(StreamValidator.validateHeaders(mapOf("Header\r\nName" to "value")).isFailure)

        // Header value with CRLF injection rejected
        assertTrue(StreamValidator.validateHeaders(mapOf("Header" to "value\r\nInjected: true")).isFailure)
        assertTrue(StreamValidator.validateHeaders(mapOf("Header" to "value\nInjected: true")).isFailure)
    }

    @Test
    fun testToMediaItemConversion() {
        val result = StreamValidator.validate("https://example.com/streams/live.m3u8")
        assertIs<StreamValidationResult.Valid>(result)
        val mediaItem = StreamValidator.toMediaItem(result)

        assertEquals("https://example.com/streams/live.m3u8", mediaItem.uri)
        assertEquals(dev.vimivo.core.model.MediaKind.STREAM, mediaItem.kind)
        assertEquals(dev.vimivo.core.model.MediaSource.NETWORK, mediaItem.source)
    }

    @Test
    fun testPercentDecodedUrlDisplayTitle() {
        // Space decoding: %20 -> space
        val res1 = StreamValidator.validate("https://example.com/audio/Acoustic%20Session%2001.mp3")
        assertIs<StreamValidationResult.Valid>(res1)
        assertEquals("Acoustic Session 01", res1.displayTitle)

        // Bengali UTF-8 decoding: %E0%A6%97%E0%A6%BE%E0%A6%A8 -> গান
        val resBengali = StreamValidator.validate("https://example.com/songs/%E0%A6%97%E0%A6%BE%E0%A6%A8.mp3")
        assertIs<StreamValidationResult.Valid>(resBengali)
        assertEquals("গান", resBengali.displayTitle)

        // Direct percentDecode helper verification
        assertEquals("Hello World", StreamValidator.percentDecode("Hello%20World"))
        assertEquals("PlainTitle", StreamValidator.percentDecode("PlainTitle"))
        assertEquals("বাংলা", StreamValidator.percentDecode("%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE"))
    }
}
