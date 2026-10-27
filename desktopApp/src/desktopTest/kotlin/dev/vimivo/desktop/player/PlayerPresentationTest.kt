package dev.vimivo.desktop.player

import dev.vimivo.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerPresentationTest {
    @Test
    fun videoAndStreamsAlwaysUseThePlayerWindowVideoLayout() {
        PlayerPresentation.entries.forEach { current ->
            assertEquals(PlayerPresentation.VIDEO, nextPresentation(current, MediaKind.VIDEO))
            assertEquals(PlayerPresentation.VIDEO, nextPresentation(current, MediaKind.STREAM))
        }
    }

    @Test
    fun audioAfterVideoSwitchesThePlayerWindowToTheAudioLayout() {
        assertEquals(PlayerPresentation.AUDIO, nextPresentation(PlayerPresentation.VIDEO, MediaKind.AUDIO))
        assertEquals(PlayerPresentation.AUDIO, nextPresentation(PlayerPresentation.AUDIO, MediaKind.AUDIO))
    }

    @Test
    fun audioStartedFromTheLibraryStaysInTheLibrary() {
        assertEquals(PlayerPresentation.LIBRARY, nextPresentation(PlayerPresentation.LIBRARY, MediaKind.AUDIO))
        assertEquals(PlayerPresentation.LIBRARY, nextPresentation(PlayerPresentation.LIBRARY, null))
    }
}
