@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.desktop.streaming

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStreamHistoryRepositoryTest {

    @Test
    fun testRecordAndLoadRecentStreams() {
        val tempDir = Files.createTempDirectory("vimivo-stream-history-test")
        val repo = DesktopStreamHistoryRepository(tempDir)

        assertTrue(repo.loadRecentStreams().isEmpty())

        repo.recordStream("https://cdn.example.org/stream1.m3u8")
        repo.recordStream("https://cdn.example.org/stream2.mp4")

        val list = repo.loadRecentStreams()
        assertEquals(2, list.size)
        assertEquals("https://cdn.example.org/stream2.mp4", list[0], "Most recent stream should be first")
        assertEquals("https://cdn.example.org/stream1.m3u8", list[1])
    }

    @Test
    fun testRedactsSensitiveParametersInRecordedStreams() {
        val tempDir = Files.createTempDirectory("vimivo-stream-redact-test")
        val repo = DesktopStreamHistoryRepository(tempDir)

        repo.recordStream("https://cdn.example.org/live.m3u8?token=supersecret123&user=john")
        val list = repo.loadRecentStreams()
        assertEquals(1, list.size)
        assertTrue(list[0].contains("token=[REDACTED]"))
        assertTrue(!list[0].contains("supersecret123"))
    }

    @Test
    fun testClearRecentStreams() {
        val tempDir = Files.createTempDirectory("vimivo-stream-clear-test")
        val repo = DesktopStreamHistoryRepository(tempDir)

        repo.recordStream("https://cdn.example.org/stream1.mp3")
        assertEquals(1, repo.loadRecentStreams().size)

        repo.clearRecentStreams()
        assertTrue(repo.loadRecentStreams().isEmpty())
    }
}
