package dev.vimivo.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacySafeDiagnosticsTest {

    @Test
    fun bufferHonorsCapacityLimit() {
        val buffer = DiagnosticBuffer(maxCapacity = 10)
        for (i in 1..25) {
            buffer.recordEvent("TEST", "Event message $i", timestampEpochMs = i.toLong())
        }

        val events = buffer.snapshotEvents()
        assertEquals(10, events.size, "Buffer must not exceed maximum capacity")
        assertEquals("Event message 16", events.first().message)
        assertEquals("Event message 25", events.last().message)
    }

    @Test
    fun bufferAutomaticallyRedactsPathsAndTokens() {
        val buffer = DiagnosticBuffer(maxCapacity = 50)
        buffer.recordEvent("IO", "Opened file: /Users/johndoe/Music/Secret Track.flac")
        buffer.recordEvent("NET", "Streaming from: https://example.com/stream.m3u8?auth=secret_token_12345")

        val events = buffer.snapshotEvents()
        assertEquals(2, events.size)

        val pathMsg = events[0].message
        assertFalse(pathMsg.contains("/Users/johndoe"), "Username must be redacted")
        assertTrue(pathMsg.contains("~/user/Music/Secret Track.flac"))

        val urlMsg = events[1].message
        assertFalse(urlMsg.contains("secret_token_12345"), "Token parameter must be redacted")
        assertTrue(urlMsg.contains("[REDACTED]"))
    }

    @Test
    fun errorRecordingSanitizesExceptionMessageAndStackTrace() {
        val buffer = DiagnosticBuffer(maxCapacity = 50)
        val ex = IllegalStateException("Failed to open /home/alice/Videos/personal.mp4 with token=supersecret")
        buffer.recordError("PLAYBACK", ex, timestampEpochMs = 12345L)

        val errors = buffer.snapshotErrors()
        assertEquals(1, errors.size)
        val error = errors.single()

        assertEquals("IllegalStateException", error.exceptionType)
        assertFalse(error.message.contains("/home/alice"), "Path must be redacted from error message")
        assertFalse(error.message.contains("supersecret"), "Token must be redacted from error message")
        assertTrue(error.message.contains("~/user/Videos/personal.mp4"))

        assertTrue(error.sanitizedStackTrace.isNotEmpty())
        error.sanitizedStackTrace.forEach { line ->
            assertFalse(line.contains("/home/alice"))
        }
    }

    @Test
    fun reportFormatsMarkdownAndSerializesJsonCleanly() {
        val buffer = DiagnosticBuffer()
        buffer.recordEvent("STARTUP", "Application initialized successfully", 100L)
        buffer.recordError("MEDIA", RuntimeException("Decoder failure at /Users/tester/sample.mp4"), 200L)

        val report = DiagnosticReport(
            appVersion = "1.0.0-rc.1",
            buildCommit = "3d77bde",
            platform = "Android",
            osVersion = "Android 16 (API 36)",
            architecture = "arm64-v8a",
            generatedTimestampEpochMs = 300L,
            playbackBackend = "Media3 ExoPlayer 1.11.1",
            recentEvents = buffer.snapshotEvents(),
            errorSummaries = buffer.snapshotErrors(),
        )

        val md = report.toFormattedMarkdown()
        assertTrue(md.contains("# Vimivo Privacy-Safe Diagnostic Report"))
        assertTrue(md.contains("Android 16 (API 36)"))
        assertTrue(md.contains("Media3 ExoPlayer 1.11.1"))
        assertFalse(md.contains("/Users/tester"), "Markdown output must not leak username")

        val json = report.toJson()
        assertTrue(json.contains("\"appVersion\": \"1.0.0-rc.1\""))
        assertTrue(json.contains("\"buildCommit\": \"3d77bde\""))
        assertFalse(json.contains("/Users/tester"), "JSON output must not leak username")
    }
}
