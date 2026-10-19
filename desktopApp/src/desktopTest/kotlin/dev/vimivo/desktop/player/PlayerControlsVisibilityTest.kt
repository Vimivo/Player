package dev.vimivo.desktop.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerControlsVisibilityTest {
    @Test
    fun autoHideIsOffByDefaultAndOnlyForWindowedVideo() {
        assertFalse(PlayerState().autoHidesControls(showsVideo = true))
        val enabled = PlayerState(autoHideControls = true)
        assertTrue(enabled.autoHidesControls(showsVideo = true))
        assertFalse(enabled.autoHidesControls(showsVideo = false))
        assertFalse(enabled.copy(fullscreen = true).autoHidesControls(showsVideo = true))
    }

    @Test
    fun dockedBarShowsOutsideFullscreenAndForAudio() {
        assertTrue(PlayerState().showsControlBar())
        assertFalse(PlayerState(fullscreen = true, presentation = PlayerPresentation.VIDEO).showsControlBar())
        assertTrue(PlayerState(fullscreen = true, presentation = PlayerPresentation.AUDIO).showsControlBar())
    }
}
