@file:Suppress("TooGenericExceptionCaught")

package dev.vimivo.desktop.playback

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.awt.Canvas
import java.awt.GraphicsEnvironment

/**
 * Owns the Linux native X11 / XWayland video render target for an existing libmpv handle.
 *
 * Attaches the AWT Canvas's X11 Window ID (XID) directly to libmpv's video output via the "wid" property.
 */
internal class MpvX11Renderer : DesktopVideoRenderer {
    private var attached = false
    private var activeContext: Pointer? = null

    override fun attach(canvas: Canvas, context: Pointer) {
        check(!attached) { "A render target is already attached" }
        if (!GraphicsEnvironment.isHeadless() && !canvas.isDisplayable) {
            runCatching { canvas.addNotify() }
        }
        val xid = runCatching { Native.getComponentID(canvas) }.getOrDefault(0L)
        check(xid != 0L) { "Unable to obtain native X11 Window ID from Canvas" }

        val mpv = MpvNative.load()
        val result = mpv.mpv_set_property_string(context, "wid", xid.toString())
        require(result >= 0) { "Unable to attach libmpv to Linux X11 Window: error code $result" }

        activeContext = context
        attached = true
    }

    override fun detach() {
        if (attached) {
            // Stop video output while the embedded window still exists. Changing "wid" at runtime makes
            // libmpv re-create its output against the old window, which X11 reports as a fatal BadWindow.
            activeContext?.let { ctx ->
                runCatching { MpvNative.load().mpv_command(ctx, StringArray(arrayOf("stop"))) }
            }
            activeContext = null
            attached = false
        }
    }
}
