package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Canvas

internal interface DesktopVideoRenderer {
    fun attach(canvas: Canvas, context: Pointer)
    fun detach()

    /** Called after libmpv may have started or stopped its video output: on every poll and after the player closes. */
    fun afterVideoOutputChange() = Unit
}

internal object DesktopVideoRendererFactory {
    fun create(platform: DesktopPlatform = DesktopPlatform.detect()): DesktopVideoRenderer = when (platform) {
        DesktopPlatform.MACOS -> MpvCocoaRenderer()
        DesktopPlatform.WINDOWS -> MpvWin32Renderer()
        DesktopPlatform.LINUX -> MpvEglRenderer()
        DesktopPlatform.UNKNOWN -> UnsupportedDesktopVideoRenderer(
            "Embedded video is unavailable on this operating system",
        )
    }
}

private class UnsupportedDesktopVideoRenderer(private val reason: String) : DesktopVideoRenderer {
    override fun attach(canvas: Canvas, context: Pointer) = throw UnsupportedOperationException(reason)
    override fun detach() = Unit
}
