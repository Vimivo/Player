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
        DesktopPlatform.WINDOWS -> UnsupportedDesktopVideoRenderer(
            "Windows embedded video requires the pending libmpv HWND/render-API adapter",
        )
        DesktopPlatform.LINUX -> UnsupportedDesktopVideoRenderer(
            "Linux embedded video is outside the current project scope",
        )
        DesktopPlatform.UNKNOWN -> UnsupportedDesktopVideoRenderer(
            "Embedded video is unavailable on this operating system",
        )
    }
}

private class UnsupportedDesktopVideoRenderer(private val reason: String) : DesktopVideoRenderer {
    override fun attach(canvas: Canvas, context: Pointer) = throw UnsupportedOperationException(reason)
    override fun detach() = Unit
}
