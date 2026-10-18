package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSpeedsTest {
    @Test
    fun presetsSpanTheFullSupportedRangeInOrder() {
        assertEquals(PlaybackSpeeds.MIN, PlaybackSpeeds.PRESETS.first())
        assertEquals(PlaybackSpeeds.MAX, PlaybackSpeeds.PRESETS.last())
        assertEquals(PlaybackSpeeds.PRESETS.sorted(), PlaybackSpeeds.PRESETS)
        assertTrue(1f in PlaybackSpeeds.PRESETS)
    }
}
