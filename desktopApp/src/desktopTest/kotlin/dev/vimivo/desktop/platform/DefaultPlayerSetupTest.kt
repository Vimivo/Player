package dev.vimivo.desktop.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultPlayerSetupTest {
    @Test
    fun linuxSetsVimivoForMediaAndPlaylistsButNotSubtitles() {
        val command = DefaultPlayerSetup.linuxCommand()
        assertEquals(listOf("xdg-mime", "default", "dev.vimivo.desktop.desktop"), command.take(3))
        assertTrue("video/x-matroska" in command && "audio/flac" in command && "audio/x-scpls" in command)
        assertTrue("application/x-subrip" !in command)
        assertEquals(command.size, command.toSet().size, "no duplicate MIME types")
    }

    @Test
    fun linuxNeverChangesDefaultsWithoutConfirmationAndMacGetsInstructions() {
        assertIs<DefaultPlayerResult.Failed>(DefaultPlayerSetup.makeDefault(DesktopPlatform.LINUX, confirmed = false))
        val mac = DefaultPlayerSetup.makeDefault(DesktopPlatform.MACOS, confirmed = true)
        assertIs<DefaultPlayerResult.Instructions>(mac)
    }
}
