package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import java.awt.Canvas

/**
 * Owns the macOS-only render target for an existing libmpv handle.
 *
 * The native bridge is deliberately limited to JAWT/Core Animation and the
 * libmpv render API; player commands and state remain in [MpvPlayerEngine].
 */
internal class MpvCocoaRenderer : DesktopVideoRenderer {
    private var attached = false

    override fun attach(canvas: Canvas, context: Pointer) {
        check(!attached) { "A render target is already attached" }
        // JAWT dereferences the canvas peer; calling it before the canvas is displayable crashes the JVM.
        check(canvas.isDisplayable) { "The video surface is not displayable yet" }
        MpvNative.loadCocoaBridge()
        val result = attachNative(canvas, Pointer.nativeValue(context))
        check(result == 0) { "Unable to attach libmpv to the Cocoa render target: ${describeAttachFailure(result)}" }
        attached = true
    }

    override fun detach() {
        if (attached) detachNative()
        attached = false
    }

    override fun presentedFrame(): PresentedFrame? {
        if (!attached) return null
        val (count, width, height) = presentedFrameNative() ?: return null
        return PresentedFrame(count, width.toInt(), height.toInt())
    }

    private external fun presentedFrameNative(): LongArray?
    private external fun attachNative(canvas: Canvas, mpvHandle: Long): Int
    private external fun detachNative()

    private companion object {
        // Mirrors the VIMIVO_ATTACH_* codes in MpvCocoaRenderer.mm; other negatives are libmpv error codes.
        fun describeAttachFailure(code: Int): String = when (code) {
            -1001 -> "a render target is already attached"
            -1002 -> "the JAWT interface is unavailable"
            -1003 -> "the video surface could not be locked"
            -1004 -> "the video surface has no Core Animation layer host"
            -1005 -> "an OpenGL context could not be created"
            else -> "libmpv render context error $code"
        }
    }
}
