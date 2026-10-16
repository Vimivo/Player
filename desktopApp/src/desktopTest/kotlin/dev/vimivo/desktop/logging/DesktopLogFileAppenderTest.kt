@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop.logging

import dev.vimivo.core.logging.LogLevel
import dev.vimivo.core.logging.StructuredLogEntry
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLogFileAppenderTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("vimivo_log_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testAppendWritesToMainLogFile() {
        val appender = DesktopLogFileAppender(
            logDirectory = tempDir,
            baseFileName = "test_vimivo.log",
            maxFileSizeBytes = 10_000L,
            maxBackupIndex = 3,
        )

        val entry = StructuredLogEntry(
            timestampEpochMs = 1700000000000L,
            level = LogLevel.INFO,
            component = "PlaybackEngine",
            message = "Media initialized successfully",
        )

        appender.append(entry)

        val logFile = appender.getMainLogFile()
        assertTrue(logFile.exists())
        val text = logFile.readText()
        assertTrue(text.contains("[INFO] [PlaybackEngine]: Media initialized successfully"))
    }

    @Test
    fun testLogRotationWhenSizeExceeded() {
        // Set low size threshold (e.g. 100 bytes) to force rotation
        val appender = DesktopLogFileAppender(
            logDirectory = tempDir,
            baseFileName = "rotating.log",
            maxFileSizeBytes = 120L,
            maxBackupIndex = 2,
        )

        for (i in 1..10) {
            val entry = StructuredLogEntry(
                timestampEpochMs = 1700000000000L + i,
                level = LogLevel.INFO,
                component = "RotateTest",
                message = "Log message payload number $i - padding string for length",
            )
            appender.append(entry)
        }

        val mainFile = File(tempDir, "rotating.log")
        val backup1 = File(tempDir, "rotating.log.1")
        val backup2 = File(tempDir, "rotating.log.2")
        val backup3 = File(tempDir, "rotating.log.3")

        assertTrue(mainFile.exists())
        assertTrue(backup1.exists())
        assertTrue(backup2.exists())
        // backup3 should NOT exist because maxBackupIndex is 2
        assertEquals(false, backup3.exists())
    }
}
