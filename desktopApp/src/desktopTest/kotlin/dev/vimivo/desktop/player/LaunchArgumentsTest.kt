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

    @Test
    fun parsesAllExtendedCliFlagsCorrectly() {
        val args = arrayOf(
            "--end=01:30",
            "--volume=150",
            "--speed=1.75",
            "--sub=subs.srt",
            "--audio-track=2",
            "--loop",
            "--enqueue",
            "--audio-only",
            "--help",
            "video.mp4",
        )
        val parsed = LaunchArguments.parse(args)
        assertEquals(90_000L, parsed.endPositionMillis)
        assertEquals(1.5f, parsed.volume)
        assertEquals(1.75f, parsed.speed)
        assertEquals(File("subs.srt"), parsed.subtitleFile)
        assertEquals("2", parsed.audioTrack)
        assertEquals(true, parsed.loop)
        assertEquals(true, parsed.enqueue)
        assertEquals(true, parsed.audioOnly)
        assertEquals(true, parsed.showHelp)
        assertEquals(listOf(File("video.mp4")), parsed.files)
    }
}
