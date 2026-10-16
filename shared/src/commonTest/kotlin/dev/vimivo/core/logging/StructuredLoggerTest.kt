@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.logging

import dev.vimivo.core.error.VimivoError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredLoggerTest {

    @Test
    fun testLogLevelFiltering() {
        val logger = StructuredLogger(maxBufferSize = 50)
        logger.minLogLevel = LogLevel.WARN

        logger.d("TestComponent", "Debug message should be ignored")
        logger.i("TestComponent", "Info message should be ignored")
        assertEquals(0, logger.snapshotEntries().size)

        logger.w("TestComponent", "Warning message should be captured")
        logger.e("TestComponent", "Error message should be captured")
        assertEquals(2, logger.snapshotEntries().size)
    }

    @Test
    fun testBoundedMemoryRingBuffer() {
        val logger = StructuredLogger(maxBufferSize = 5)
        logger.minLogLevel = LogLevel.INFO

        for (i in 1..20) {
            logger.i("TestComponent", "Message number $i")
        }

        val entries = logger.snapshotEntries()
        assertEquals(5, entries.size)
        assertEquals("Message number 16", entries.first().message)
        assertEquals("Message number 20", entries.last().message)
    }

    @Test
    fun testSensitiveMetadataAndPathRedaction() {
        val logger = StructuredLogger(maxBufferSize = 10)
        logger.minLogLevel = LogLevel.INFO

        val meta = mapOf(
            "auth_token" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
            "api_key" to "AIzaSyD-sample-key",
            "safe_property" to "normal_value",
        )

        logger.i(
            component = "AuthService",
            message = "Loading user media from /Users/alice/Music/my_album/track.flac",
            metadata = meta,
        )

        val entry = logger.snapshotEntries().single()
        assertFalse(entry.message.contains("alice"))
        assertTrue(entry.message.contains("~/user/Music/my_album/track.flac"))

        assertEquals("[REDACTED]", entry.metadata["auth_token"])
        assertEquals("[REDACTED]", entry.metadata["api_key"])
        assertEquals("normal_value", entry.metadata["safe_property"])
    }

    @Test
    fun testLogErrorIntegration() {
        val logger = StructuredLogger(maxBufferSize = 10)
        val error = VimivoError.mediaNotFound("/Users/charlie/Songs/song.mp3")

        logger.logError(error)
        val entry = logger.snapshotEntries().single()
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("ERR_MEDIA_NOT_FOUND", entry.metadata["errorCode"])
        assertFalse(entry.metadata["detail"]?.contains("charlie") == true)
    }

    @Test
    fun testAppenderCallbackInvocation() {
        val logger = StructuredLogger(maxBufferSize = 10)
        val received = mutableListOf<StructuredLogEntry>()
        val appender = LogAppender { received.add(it) }

        logger.addAppender(appender)
        logger.i("AppenderTest", "Test message")

        assertEquals(1, received.size)
        assertEquals("Test message", received.single().message)

        logger.removeAppender(appender)
        logger.i("AppenderTest", "Second message")
        assertEquals(1, received.size)
    }
}
