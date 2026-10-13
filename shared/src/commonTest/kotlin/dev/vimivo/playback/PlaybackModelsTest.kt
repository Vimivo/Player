package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackModelsTest {
    @Test
    fun defaultStateIsSafeAndIdle() {
        val state = PlaybackState()
        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(1f, state.speed)
        assertTrue(state.audioTracks.isEmpty())
    }
}
