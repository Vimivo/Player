package dev.vimivo.desktop.playback

import dev.vimivo.playback.PlaybackErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvFatalMessagesTest {
    @Test
    fun onlyFatalPlayerLinesAreTreatedAsPlaybackFailures() {
        val fatal = MpvLogMessage("cplayer", "fatal", "No video or audio streams selected.")
        assertEquals("No video or audio streams selected.", MpvLogEvents.fatalPlaybackText(fatal))
        assertNull(MpvLogEvents.fatalPlaybackText(fatal.copy(level = "error")))
        assertNull(MpvLogEvents.fatalPlaybackText(fatal.copy(prefix = "vo")))
        assertNull(MpvLogEvents.fatalPlaybackText(fatal.copy(text = "  ")))
    }

    @Test
    fun aFailedVideoOutputIsExplainedToTheUser() {
        val error = MpvPlaybackErrors.playbackFailure(
            "Error opening/initializing the selected video_out (--vo) device.",
        )
        assertEquals(PlaybackErrorCode.ENGINE_INITIALIZATION, error.code)
        assertTrue("video output" in error.message)
    }

    @Test
    fun aFileWithNothingDecodableIsExplainedToTheUser() {
        val error = MpvPlaybackErrors.playbackFailure("No video or audio streams selected.")
        assertEquals(PlaybackErrorCode.UNSUPPORTED_CODEC, error.code)
        assertTrue("could be decoded" in error.message)
    }

    @Test
    fun anyOtherFatalLineIsPassedThrough() {
        val error = MpvPlaybackErrors.playbackFailure("Something unexpected happened")
        assertEquals(PlaybackErrorCode.DECODING_FAILED, error.code)
        assertEquals("Something unexpected happened", error.message)
    }
}
