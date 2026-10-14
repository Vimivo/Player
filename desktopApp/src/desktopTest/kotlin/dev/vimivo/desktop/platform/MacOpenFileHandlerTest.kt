package dev.vimivo.desktop.platform

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class MacOpenFileHandlerTest {
    @Test
    fun buffersValidFilesUntilCollectorStartsAndPreservesOrder() = runTest {
        val directory = Files.createTempDirectory("vimivo-open-files-test")
        val first = directory.resolve("one.mp3").also { it.writeText("one") }.toFile()
        val second = directory.resolve("two.flac").also { it.writeText("two") }.toFile()
        val missing = directory.resolve("missing.mp4").toFile()

        MacOpenFileHandler().use { handler ->
            handler.submit(listOf(first, missing, second))
            assertEquals(listOf(first, second), handler.files.first())
        }
    }
}
