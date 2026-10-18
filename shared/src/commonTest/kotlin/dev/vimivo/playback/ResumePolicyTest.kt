package dev.vimivo.playback

import dev.vimivo.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ResumePolicyTest {
    @Test
    fun resumesAnInterruptedVideo() {
        assertEquals(120_000, start(MediaKind.VIDEO, 120_000, 600_000))
    }

    @Test
    fun restartsVideoThatWasBarelyStartedOrEffectivelyFinished() {
        assertEquals(0, start(MediaKind.VIDEO, 9_999, 600_000))
        assertEquals(0, start(MediaKind.VIDEO, 590_000, 600_000))
        assertEquals(0, start(MediaKind.VIDEO, 115_000, 120_000))
        assertEquals(0, start(MediaKind.VIDEO, 120_000, 600_000, completed = true))
    }

    @Test
    fun appliesNinetyFivePercentRuleToLongMedia() {
        // 95% of three hours is well before the final fifteen seconds.
        val duration = 3 * 3_600_000L
        assertEquals(0, start(MediaKind.VIDEO, duration * 95 / 100, duration))
        assertEquals(duration / 2, start(MediaKind.VIDEO, duration / 2, duration))
    }

    @Test
    fun songsRestartUnlessTheUserOptedIn() {
        assertEquals(0, start(MediaKind.AUDIO, 120_000, 240_000))
        assertEquals(120_000, start(MediaKind.AUDIO, 120_000, 240_000, includeShortAudio = true))
    }

    @Test
    fun longAudioResumesLikeVideo() {
        assertEquals(120_000, start(MediaKind.AUDIO, 120_000, 3_600_000))
    }

    @Test
    fun unknownDurationStillResumesVideoAndStreams() {
        assertEquals(60_000, start(MediaKind.VIDEO, 60_000, null))
        assertEquals(60_000, start(MediaKind.STREAM, 60_000, 0))
        assertEquals(0, start(MediaKind.AUDIO, 60_000, null))
    }

    private fun start(
        kind: MediaKind,
        position: Long,
        duration: Long?,
        completed: Boolean = false,
        includeShortAudio: Boolean = false,
    ) = ResumePolicy.startPositionMillis(kind, position, duration, completed, includeShortAudio)
}
