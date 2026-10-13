package dev.vimivo.platform.android.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoResumePolicyTest {
    @Test
    fun resumesAnInterruptedVideo() {
        val video = video(duration = 120_000, position = 45_000)

        assertEquals(45_000L, video.safeResumePosition())
    }

    @Test
    fun restartsVideoSavedWithinFinalTenSeconds() {
        val video = video(duration = 120_000, position = 115_000)

        assertEquals(0L, video.safeResumePosition())
    }

    private fun video(duration: Long, position: Long) = MediaItem(
        id = MediaId("video"),
        uri = "content://video",
        title = "Video",
        kind = MediaKind.VIDEO,
        durationMillis = duration,
        lastPlaybackPositionMillis = position,
    )
}
