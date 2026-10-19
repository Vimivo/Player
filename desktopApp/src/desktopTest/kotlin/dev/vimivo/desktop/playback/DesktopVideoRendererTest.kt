package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Canvas
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopVideoRendererTest {

    @Test
    fun capabilityReportingReflectsWindowsAndLinuxNativeVideoSupport() {
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).localVideo)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).hdr)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.WINDOWS).localAudio)

        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.LINUX).localVideo)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.LINUX).hdr)
        assertTrue(MpvPlayerEngine.capabilitiesFor(DesktopPlatform.LINUX).localAudio)
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
    fun linuxRendererUsesTheRenderApiAndFailsCleanlyWithoutAWindow() {
        // The LGPL libmpv has no X11 output; Linux renders into Vimivo's own EGL context (ADR-004).
        val renderer = DesktopVideoRendererFactory.create(DesktopPlatform.LINUX)
        assertTrue(renderer is MpvEglRenderer)
        val error = assertFailsWith<IllegalStateException> {
            renderer.attach(Canvas(), Pointer.createConstant(1))
        }
        assertTrue(error.message.orEmpty().contains("not displayable"))
        renderer.detach()
    }

    @Test
    fun unknownPlatformRendererRemainsUnsupported() {
        val renderer = DesktopVideoRendererFactory.create(DesktopPlatform.UNKNOWN)
        val error = assertFailsWith<UnsupportedOperationException> {
            renderer.attach(Canvas(), Pointer.createConstant(1))
        }
        assertTrue(error.message.orEmpty().contains("Embedded video is unavailable"))
    }
}
