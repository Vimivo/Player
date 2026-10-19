@file:Suppress("TooGenericExceptionCaught")

package dev.vimivo.desktop.playback

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.awt.Canvas
import java.awt.GraphicsEnvironment

/**
 * Owns the Linux native X11 / XWayland video render target for an existing libmpv handle.
 *
 * Attaches the AWT Canvas's X11 Window ID (XID) directly to libmpv's video output via the "wid" property.
 *
 * Xlib's error handler is process-wide. AWT installs one that ignores harmless errors (a query on a window that was
 * just destroyed); libmpv installs its own while its video output runs and resets it to Xlib's default on shutdown,
 * and the default handler exits the process on any error. The renderer therefore puts AWT's handler back after libmpv
 * may have changed it.
 */
internal class MpvX11Renderer : DesktopVideoRenderer {
    private var attached = false
    private var activeContext: Pointer? = null
    private var toolkitErrorHandler: Pointer? = null

    override fun attach(canvas: Canvas, context: Pointer) {
        check(!attached) { "A render target is already attached" }
        if (!GraphicsEnvironment.isHeadless() && !canvas.isDisplayable) {
            runCatching { canvas.addNotify() }
        }
        val xid = runCatching { Native.getComponentID(canvas) }.getOrDefault(0L)
        check(xid != 0L) { "Unable to obtain native X11 Window ID from Canvas" }

        // libmpv has not created its X11 output yet, so the installed handler is still AWT's.
        toolkitErrorHandler = XErrorHandlers.current()
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

    override fun afterVideoOutputChange() {
        toolkitErrorHandler?.let { handler -> runCatching { XErrorHandlers.install(handler) } }
    }

    /** `XSetErrorHandler`, the only Xlib call needed here. */
    private object XErrorHandlers {
        @Suppress("FunctionNaming") // Xlib's C name.
        private interface Xlib : Library {
            fun XSetErrorHandler(handler: Pointer?): Pointer?
        }

        private val xlib: Xlib by lazy { Native.load("X11", Xlib::class.java) }

        /** The installed handler (Xlib reports its default as `_XDefaultError`, never null). */
        fun current(): Pointer? = xlib.XSetErrorHandler(null).also { xlib.XSetErrorHandler(it) }

        fun install(handler: Pointer) {
            xlib.XSetErrorHandler(handler)
        }
    }
}
