package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Canvas

internal interface DesktopVideoRenderer {
    fun attach(canvas: Canvas, context: Pointer)
    fun detach()

    /** Called after libmpv may have started or stopped its video output: on every poll and after the player closes. */
    fun afterVideoOutputChange() = Unit

    /**
     * How many frames this renderer has presented and at what size, or null where libmpv presents frames itself
     * (Windows embeds libmpv's own output). Lets tests wait for a real frame instead of sleeping.
     */
    fun presentedFrame(): PresentedFrame? = null

    /** True while the OS animates a fullscreen change and would ignore another request (macOS); false elsewhere. */
    fun windowTransitioning(): Boolean = false
}

/** A count of presented frames and the pixel size of the latest one. */
internal data class PresentedFrame(val count: Long, val width: Int, val height: Int) {
    fun sameSizeAs(other: PresentedFrame) = width == other.width && height == other.height
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
