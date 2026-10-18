@file:Suppress("ArgumentListWrapping", "PropertyWrapping", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaInfoAndRecentMediaTest {
    @Test
    fun describesVideoAndAudioTracks() {
        val video = TrackInfo("1", TrackType.VIDEO, "Main", codec = "h264", width = 1920, height = 1080, frameRate = 23.976f)
        assertEquals("H264 · 1920×1080 · 23.976 fps", MediaInfoFormatter.describe(video))
        val audio = TrackInfo(
            "2", TrackType.AUDIO, "English", language = "eng", codec = "aac",
            channels = 6, sampleRate = 48_000, bitrate = 384_000, origin = TrackOrigin.EXTERNAL,
        )
        assertEquals("AAC · 5.1 · 48 kHz · 384 kbps · eng · external", MediaInfoFormatter.describe(audio))
        assertEquals("", MediaInfoFormatter.describe(TrackInfo("3", TrackType.SUBTITLE, "Sub")))
    }

    @Test
    fun formatsSizesAndDurations() {
        assertEquals("512 B", MediaInfoFormatter.formatFileSize(512))
        assertEquals("1.5 MB", MediaInfoFormatter.formatFileSize(1_572_864))
        assertEquals("2.0 GB", MediaInfoFormatter.formatFileSize(2_147_483_648))
        assertEquals("3:05", MediaInfoFormatter.formatDuration(185_000))
        assertEquals("1:02:03", MediaInfoFormatter.formatDuration(3_723_000))
    }

    @Test
    fun recentMediaDeduplicatesAndCaps() {
        val a = RecentMediaEntry("file:///a.mkv", "A", true)
        val b = RecentMediaEntry("file:///b.mp3", "B", false)
        val c = RecentMediaEntry("file:///c.mp4", "C", true)
        var list = RecentMediaList.record(emptyList(), a, capacity = 2)
        list = RecentMediaList.record(list, b, capacity = 2)
        list = RecentMediaList.record(list, a.copy(title = "A renamed"), capacity = 2)
        assertEquals(listOf("A renamed", "B"), list.map(RecentMediaEntry::title))
        list = RecentMediaList.record(list, c, capacity = 2)
        assertEquals(listOf(c.uri, "file:///a.mkv"), list.map(RecentMediaEntry::uri))
        assertEquals(list, RecentMediaList.record(list, RecentMediaEntry(" ", "blank", false), capacity = 2))
        assertFailsWith<IllegalArgumentException> { RecentMediaList.record(list, a, capacity = 0) }
    }
}
