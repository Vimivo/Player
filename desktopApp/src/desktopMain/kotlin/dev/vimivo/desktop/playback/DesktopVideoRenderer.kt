package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Canvas

internal interface DesktopVideoRenderer {
    fun attach(canvas: Canvas, context: Pointer)
    fun detach()
}

internal object DesktopVideoRendererFactory {
    fun create(platform: DesktopPlatform = DesktopPlatform.detect()): DesktopVideoRenderer = when (platform) {
        DesktopPlatform.MACOS -> MpvCocoaRenderer()
        DesktopPlatform.WINDOWS -> MpvWin32Renderer()
        DesktopPlatform.LINUX -> MpvX11Renderer()
        DesktopPlatform.UNKNOWN -> UnsupportedDesktopVideoRenderer(
            "Embedded video is unavailable on this operating system",
        )
    }
}

private class UnsupportedDesktopVideoRenderer(private val reason: String) : DesktopVideoRenderer {
    override fun attach(canvas: Canvas, context: Pointer) = throw UnsupportedOperationException(reason)
    override fun detach() = Unit
}
