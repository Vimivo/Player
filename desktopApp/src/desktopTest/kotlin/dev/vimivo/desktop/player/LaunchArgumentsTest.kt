package dev.vimivo.desktop.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchArgumentsTest {
    @Test
    fun parsesFilesStartAndFullscreenInAnyOrder() {
        val parsed = LaunchArguments.parse(arrayOf("--start=00:10:00", "a.mkv", "-psn_0_1234", "--fullscreen", "b.mp3"))

        assertEquals(listOf(File("a.mkv"), File("b.mp3")), parsed.files)
        assertEquals(600_000L, parsed.startPositionMillis)
        assertEquals(true, parsed.fullscreen)
        assertEquals(false, parsed.smokeTest)
    }

    @Test
    fun parsesClockAndSecondsForms() {
        assertEquals(90_000L, LaunchArguments.parseTime("90"))
        assertEquals(90_000L, LaunchArguments.parseTime("1:30"))
        assertEquals(3_723_000L, LaunchArguments.parseTime("01:02:03"))
        assertEquals(90_500L, LaunchArguments.parseTime("1:30.5"))
    }

    @Test
    fun rejectsMalformedTimes() {
        listOf("", "1::2", "a:30", "-5", "1:2:3:4").forEach { assertNull(LaunchArguments.parseTime(it), it) }
        assertNull(LaunchArguments.parse(arrayOf("--start=soon", "a.mkv")).startPositionMillis)
    }
}
