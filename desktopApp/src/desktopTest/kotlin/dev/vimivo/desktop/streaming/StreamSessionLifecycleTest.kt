@file:Suppress(
    "ArgumentListWrapping", "MagicNumber", "MaxLineLength", "MaximumLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.streaming

import dev.vimivo.core.streaming.LiveLatencyMode
import dev.vimivo.core.streaming.StreamFailureReason
import dev.vimivo.core.streaming.StreamReconnectionPolicy
import dev.vimivo.core.streaming.StreamValidator
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackStatus
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamSessionLifecycleTest {

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
    fun testTestStreamServerServesDirectAudioWithByteRanges() {
        // Full GET request
        val fullUrl = URI("${server.baseUrl}/media/audio.mp3").toURL()
        val fullConn = fullUrl.openConnection() as HttpURLConnection
        fullConn.requestMethod = "GET"
        assertEquals(200, fullConn.responseCode)
        assertEquals("audio/mpeg", fullConn.contentType)
        assertEquals("bytes", fullConn.getHeaderField("Accept-Ranges"))
        val fullBytes = fullConn.inputStream.use { it.readBytes() }
        assertEquals(4096, fullBytes.size)

        // Partial range request (bytes=0-99)
        val rangeConn = fullUrl.openConnection() as HttpURLConnection
        rangeConn.requestMethod = "GET"
        rangeConn.setRequestProperty("Range", "bytes=0-99")
        assertEquals(206, rangeConn.responseCode)
        assertEquals("bytes 0-99/4096", rangeConn.getHeaderField("Content-Range"))
        val rangeBytes = rangeConn.inputStream.use { it.readBytes() }
        assertEquals(100, rangeBytes.size)
    }

    @Test
    fun testTestStreamServerServesHlsVodAndLiveManifests() {
        // VOD manifest (has ENDLIST)
        val vodUrl = URI("${server.baseUrl}/hls/vod.m3u8").toURL()
        val vodConn = vodUrl.openConnection() as HttpURLConnection
        assertEquals(200, vodConn.responseCode)
        val vodText = vodConn.inputStream.bufferedReader().use { it.readText() }
        assertTrue(vodText.contains("#EXTM3U"))
        assertTrue(vodText.contains("#EXT-X-ENDLIST"))

        // Live manifest (sliding window, no ENDLIST)
        val liveUrl = URI("${server.baseUrl}/hls/live.m3u8").toURL()
        val liveConn1 = liveUrl.openConnection() as HttpURLConnection
        assertEquals(200, liveConn1.responseCode)
        val liveText1 = liveConn1.inputStream.bufferedReader().use { it.readText() }
        assertTrue(liveText1.contains("#EXTM3U"))
        assertTrue(liveText1.contains("#EXT-X-MEDIA-SEQUENCE:1"))
        assertFalse(liveText1.contains("#EXT-X-ENDLIST"))

        // Subsequent poll advances media sequence (live sliding window)
        val liveConn2 = liveUrl.openConnection() as HttpURLConnection
        val liveText2 = liveConn2.inputStream.bufferedReader().use { it.readText() }
        assertTrue(liveText2.contains("#EXT-X-MEDIA-SEQUENCE:2"))

        // Segment retrieval
        val segmentUrl = URI("${server.baseUrl}/hls/segment1.ts").toURL()
        val segmentConn = segmentUrl.openConnection() as HttpURLConnection
        assertEquals(200, segmentConn.responseCode)
        assertEquals("video/MP2T", segmentConn.contentType)
        val segmentBytes = segmentConn.inputStream.use { it.readBytes() }
        assertEquals(1880, segmentBytes.size)
    }

    @Test
    fun testTestStreamServerServesDashManifest() {
        val dashUrl = URI("${server.baseUrl}/dash/manifest.mpd").toURL()
        val dashConn = dashUrl.openConnection() as HttpURLConnection
        assertEquals(200, dashConn.responseCode)
        assertEquals("application/dash+xml", dashConn.contentType)
        val mpdText = dashConn.inputStream.bufferedReader().use { it.readText() }
        assertTrue(mpdText.contains("<MPD"))
        assertTrue(mpdText.contains("<AdaptationSet"))
    }

    @Test
    fun testFaultSimulationAndClassification() {
        val codes = listOf(401, 403, 404, 408, 429, 500)
        codes.forEach { code ->
            val conn = URI("${server.baseUrl}/fault/$code").toURL().openConnection() as HttpURLConnection
            assertEquals(code, conn.responseCode)
            val classified = StreamValidator.classifyHttpStatus(code)
            when (code) {
                401, 403 -> assertEquals(StreamFailureReason.HTTP_FORBIDDEN, classified)
                404 -> assertEquals(StreamFailureReason.NOT_FOUND, classified)
                408 -> assertEquals(StreamFailureReason.TIMEOUT, classified)
                429, 500 -> assertEquals(StreamFailureReason.CONNECTION_FAILED, classified)
            }
        }
    }

    @Test
    fun testReconnectionPolicyWithServerFaults() {
        val policy = StreamReconnectionPolicy(maxRetries = 2)

        // 408 Timeout is transient/retryable
        val timeoutReason = StreamValidator.classifyHttpStatus(408)
        assertTrue(policy.shouldRetry(attempt = 1, failureReason = timeoutReason))

        // 403 Forbidden is terminal/non-retryable
        val forbiddenReason = StreamValidator.classifyHttpStatus(403)
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = forbiddenReason))

        // 404 Not Found is terminal/non-retryable
        val notFoundReason = StreamValidator.classifyHttpStatus(404)
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = notFoundReason))

        // User explicit Stop cancellation overrides any retryable failure
        assertTrue(policy.shouldRetry(attempt = 1, failureReason = timeoutReason, isUserCancelled = false))
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = timeoutReason, isUserCancelled = true))
    }

    @Test
    fun testMpvPlayerEngineLiveLatencyModes() {
        val engine = MpvPlayerEngine()
        try {
            // Default is BALANCED
            assertEquals(LiveLatencyMode.BALANCED, engine.liveLatencyMode)

            // Switch to LOW_LATENCY
            val lowLatencyResult = engine.setLiveLatencyMode(LiveLatencyMode.LOW_LATENCY)
            assertTrue(lowLatencyResult.isSuccess)
            assertEquals(LiveLatencyMode.LOW_LATENCY, engine.liveLatencyMode)

            // Switch to STABLE_PLAYBACK
            val stableResult = engine.setLiveLatencyMode(LiveLatencyMode.STABLE_PLAYBACK)
            assertTrue(stableResult.isSuccess)
            assertEquals(LiveLatencyMode.STABLE_PLAYBACK, engine.liveLatencyMode)
        } finally {
            engine.close()
        }
    }

    @Test
    fun testStopAndPauseIntentPreservation() {
        val engine = MpvPlayerEngine()
        try {
            // Pausing sets userPaused flag
            val pauseResult = engine.pause()
            assertTrue(pauseResult.isSuccess)

            // Stopping sets userStopped flag, clears userPaused, and resets to Idle
            val stopResult = engine.stop()
            assertTrue(stopResult.isSuccess)
            assertEquals(PlaybackStatus.Idle, engine.state.value.status)
        } finally {
            engine.close()
        }
    }

    @Test
    fun testRedirectDetection() {
        val conn = URI("${server.baseUrl}/fault/redirect").toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        assertEquals(302, conn.responseCode)
        assertEquals("/media/audio.mp3", conn.getHeaderField("Location"))
    }
}
