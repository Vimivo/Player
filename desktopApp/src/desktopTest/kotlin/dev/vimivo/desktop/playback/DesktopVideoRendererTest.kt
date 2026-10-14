package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Canvas
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopVideoRendererTest {
    @Test
    fun capabilityReportingDoesNotClaimUnimplementedWindowsVideo() {
        assertFalse(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).localVideo)
        assertFalse(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).hdr)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).localAudio)
    }

    @Test
    fun windowsRendererFailsWithActionableReasonInsteadOfLoadingCocoaLibrary() {
        val renderer = DesktopVideoRendererFactory.create(DesktopPlatform.WINDOWS)
        val error = assertFailsWith<UnsupportedOperationException> {
            renderer.attach(Canvas(), Pointer.createConstant(1))
        }
        assertTrue(error.message.orEmpty().contains("Windows embedded video"))
    }
}
