package dev.vimivo.desktop.capture

import dev.vimivo.desktop.platform.DesktopPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

class CaptureSourcesTest {
    @Test
    fun everyPlatformUsesMpvAvDeviceScheme() {
        for (platform in DesktopPlatform.entries) {
            assertTrue(CaptureSources.webcam(platform).startsWith("av://"), "webcam $platform")
            assertTrue(CaptureSources.screen(platform).startsWith("av://"), "screen $platform")
        }
    }

    @Test
    fun platformSpecificDevices() {
        assertTrue(CaptureSources.webcam(DesktopPlatform.MACOS).startsWith("av://avfoundation:"))
        assertTrue(CaptureSources.webcam(DesktopPlatform.WINDOWS).startsWith("av://dshow:video="))
        assertTrue(CaptureSources.webcam(DesktopPlatform.LINUX).startsWith("av://v4l2:/dev/"))
        assertTrue(CaptureSources.screen(DesktopPlatform.WINDOWS).startsWith("av://gdigrab:"))
        assertTrue(CaptureSources.screen(DesktopPlatform.LINUX).startsWith("av://x11grab:"))
    }
}
