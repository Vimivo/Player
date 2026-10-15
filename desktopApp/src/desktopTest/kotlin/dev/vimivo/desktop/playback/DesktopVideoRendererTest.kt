package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Canvas
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopVideoRendererTest {

    @Test
    fun capabilityReportingReflectsWindowsNativeVideoSupport() {
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).localVideo)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).hdr)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).localAudio)
    }

    @Test
    fun windowsRendererInstantiatesWin32RendererAndFailsCleanlyWithoutHwnd() {
        val renderer = DesktopVideoRendererFactory.create(DesktopPlatform.WINDOWS)
        assertTrue(renderer is MpvWin32Renderer)
        val error = assertFailsWith<IllegalStateException> {
            renderer.attach(Canvas(), Pointer.createConstant(1))
        }
        assertTrue(error.message.orEmpty().contains("HWND"))
    }

    @Test
    fun linuxRendererRemainsExplicitlyUnsupported() {
        val renderer = DesktopVideoRendererFactory.create(DesktopPlatform.LINUX)
        val error = assertFailsWith<UnsupportedOperationException> {
            renderer.attach(Canvas(), Pointer.createConstant(1))
        }
        assertTrue(error.message.orEmpty().contains("Linux embedded video"))
    }
}
