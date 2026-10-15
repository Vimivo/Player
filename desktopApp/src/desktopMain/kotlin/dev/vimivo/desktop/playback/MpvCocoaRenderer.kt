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
        val frameworks = MpvNative.findBundledFrameworksDir()
        val bundledBridge = frameworks?.resolve("libvimivo_mpv_cocoa.dylib")
        if (bundledBridge != null && bundledBridge.exists()) {
            System.load(bundledBridge.absolutePath)
        } else {
            System.loadLibrary("vimivo_mpv_cocoa")
        }
        require(attachNative(canvas, Pointer.nativeValue(context))) {
            "Unable to attach libmpv to the Cocoa render target"
        }
        attached = true
    }

    override fun detach() {
        if (attached) detachNative()
        attached = false
    }

    private external fun attachNative(canvas: Canvas, mpvHandle: Long): Boolean
    private external fun detachNative()
}
