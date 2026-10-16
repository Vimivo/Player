@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop.reliability

import dev.vimivo.core.error.VimivoError
import dev.vimivo.core.error.VimivoErrorCategory
import dev.vimivo.core.logging.LogLevel
import dev.vimivo.core.logging.StructuredLogEntry
import dev.vimivo.core.logging.StructuredLogger
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.logging.DesktopLogFileAppender
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.SupportLevel
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReliabilityTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("desktop_reliability_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRepeatedNativePlayerEngineLifecycle() {
        // Repeatedly instantiate, evaluate capabilities, and safely close engines
        for (iteration in 1..15) {
            val engine = MpvPlayerEngine()
            try {
                val media = MediaItem(
                    id = MediaId("test_track_$iteration"),
                    uri = "file:///tmp/track_$iteration.flac",
                    title = "Test Track $iteration",
                    kind = MediaKind.AUDIO,
                )
                val support = engine.supportFor(media)
                assertEquals(SupportLevel.PREFERRED, support)
                assertTrue(engine.capabilities.localAudio)
            } finally {
                engine.close()
            }
        }
    }

    @Test
    fun testConcurrentDiagnosticLoggingSafety() {
        val appender = DesktopLogFileAppender(
            logDirectory = tempDir,
            baseFileName = "concurrent_test.log",
            maxFileSizeBytes = 50_000L,
            maxBackupIndex = 3,
        )

        val threadPool = Executors.newFixedThreadPool(8)
        val entriesPerThread = 50

        for (t in 0 until 8) {
            threadPool.submit {
                for (i in 0 until entriesPerThread) {
                    val entry = StructuredLogEntry(
                        timestampEpochMs = 1700000000000L + i,
                        level = LogLevel.INFO,
                        component = "Thread-$t",
                        message = "Concurrent message payload $i from worker thread $t",
                    )
                    appender.append(entry)
                }
            }
        }

        threadPool.shutdown()
        val finished = threadPool.awaitTermination(5, TimeUnit.SECONDS)
        assertTrue(finished, "Logging threads did not finish within timeout")

        val mainLog = appender.getMainLogFile()
        assertTrue(mainLog.exists())
        assertTrue(mainLog.length() > 0)
    }

    @Test
    fun testDesktopErrorDiagnosticsRedaction() {
        val logger = StructuredLogger(platform = "desktop")
        val error = VimivoError.mediaNotFound("/Users/developer/SecretProjects/song.mp3")

        logger.logError(error)
        val log = logger.snapshotEntries().single()

        assertEquals(VimivoErrorCategory.MEDIA_NOT_FOUND, log.category)
        assertFalse(log.toFormattedLine().contains("developer"))
        assertTrue(log.toFormattedLine().contains("~/user/SecretProjects/song.mp3"))
    }
}
