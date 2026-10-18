package dev.vimivo.desktop.platform

import dev.vimivo.desktop.player.LaunchArguments
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSingleInstanceTest {
    // Short path: Unix-domain socket paths are limited to roughly 100 bytes.
    private val directory = Files.createTempDirectory(File("/tmp").toPath(), "vi").also { it.toFile().deleteOnExit() }

    @Test
    fun secondLaunchForwardsFilesToTheRunningInstance() {
        val received = LinkedBlockingQueue<LaunchArguments>()
        val primary = assertNotNull(DesktopSingleInstance(directory).becomePrimary(received::put))
        primary.use {
            val launch =
                LaunchArguments(
                    listOf(File("/media/Movie Night.mkv")),
                    startPositionMillis = 600_000,
                    fullscreen = true
                )

            assertTrue(DesktopSingleInstance(directory).forward(launch))

            val delivered = assertNotNull(received.poll(2, TimeUnit.SECONDS))
            assertEquals(listOf(File("/media/Movie Night.mkv").absoluteFile), delivered.files)
            assertEquals(600_000L, delivered.startPositionMillis)
            assertTrue(delivered.fullscreen)
        }
    }

    @Test
    fun onlyOneProcessCanBePrimaryAndAStaleSocketDoesNotBlockTheNextOne() {
        val first = assertNotNull(DesktopSingleInstance(directory).becomePrimary {})
        assertNull(DesktopSingleInstance(directory).becomePrimary {}, "a second primary must be refused")
        first.close()

        // Leave a stale socket file behind, as a crashed instance would.
        Files.writeString(directory.resolve("instance.sock"), "")
        assertFalse(DesktopSingleInstance(directory).forward(LaunchArguments(listOf(File("a.mp3")))))
        DesktopSingleInstance(directory).becomePrimary {}.use { assertNotNull(it) }
    }

    @Test
    fun launchRequestsRoundTripThroughJson() {
        val launch =
            LaunchArguments(
                listOf(File("/a/b.flac"), File("/c d/é.mkv")),
                startPositionMillis = 1_500,
                fullscreen = false
            )
        val decoded = assertNotNull(DesktopSingleInstance.decode(DesktopSingleInstance.encode(launch)))
        assertEquals(launch.files.map(File::getAbsoluteFile), decoded.files)
        assertEquals(1_500L, decoded.startPositionMillis)
        assertNull(DesktopSingleInstance.decode("not json"))
    }
}
