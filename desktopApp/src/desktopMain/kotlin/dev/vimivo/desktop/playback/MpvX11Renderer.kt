@file:Suppress("TooGenericExceptionCaught")

package dev.vimivo.desktop.playback

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.AWTEvent
import java.awt.Canvas
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ContainerEvent
import java.awt.event.HierarchyEvent
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Owns the Linux native X11 / XWayland video render target for an existing libmpv handle.
 *
 * Attaches the AWT Canvas's X11 Window ID (XID) directly to libmpv's video output via the "wid" property.
 *
 * The canvas is a sibling X window of Compose's own rendering canvas. When Compose (Skiko) recreates that canvas,
 * for example after falling back from OpenGL to software rendering, the new window is stacked above the video and
 * paints over it. The renderer therefore raises the video window on attach, whenever a component is added to or
 * moved in the video's window, and, because Skiko can restack without any AWT event, whenever a twice-a-second
 * stacking check finds it covered.
 */
internal class MpvX11Renderer : DesktopVideoRenderer {
    private var attached = false
    private var activeContext: Pointer? = null
    private var canvas: Canvas? = null
    private var xid = 0L
    private var stacking: AWTEventListener? = null
    private var stackingCheck: Timer? = null

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
        this.canvas = canvas
        this.xid = xid
        attached = true
        keepOnTop(canvas)
    }

    override fun detach() {
        if (attached) {
            stacking?.let(Toolkit.getDefaultToolkit()::removeAWTEventListener)
            stacking = null
            stackingCheck?.stop()
            stackingCheck = null
            // Stop video output while the embedded window still exists. Changing "wid" at runtime makes
            // libmpv re-create its output against the old window, which X11 reports as a fatal BadWindow.
            activeContext?.let { ctx ->
                runCatching { MpvNative.load().mpv_command(ctx, StringArray(arrayOf("stop"))) }
            }
            activeContext = null
            canvas = null
            attached = false
        }
    }

    private fun keepOnTop(canvas: Canvas) {
        val listener = AWTEventListener { event ->
            val source = (event.source as? java.awt.Component) ?: return@AWTEventListener
            val relevant = when (event) {
                is ContainerEvent -> event.id == ContainerEvent.COMPONENT_ADDED
                is HierarchyEvent -> event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L
                else -> false
            }
            val window = SwingUtilities.getWindowAncestor(canvas)
            if (relevant && window != null && SwingUtilities.getWindowAncestor(source) === window) raise()
        }
        stacking = listener
        Toolkit.getDefaultToolkit().addAWTEventListener(
            listener,
            AWTEvent.CONTAINER_EVENT_MASK or AWTEvent.HIERARCHY_EVENT_MASK,
        )
        raise()
        stackingCheck = Timer(STACKING_CHECK_MILLIS) {
            if (attached && canvas?.isShowing == true) runCatching { X11.raiseIfCovered(xid) }
        }.apply { start() }
    }

    /** Restacks after AWT has applied its own changes, so the video window ends up last (topmost). */
    private fun raise() {
        SwingUtilities.invokeLater {
            if (!attached || canvas?.isShowing != true) return@invokeLater
            runCatching { X11.raise(xid) }
        }
    }

    /** The two Xlib calls needed, on a private display connection opened once. */
    private object X11 {
        @Suppress("FunctionNaming", "LongParameterList") // Xlib's C names and signatures.
        private interface Xlib : Library {
            fun XOpenDisplay(name: String?): Pointer?
            fun XRaiseWindow(display: Pointer, window: Long): Int
            fun XFlush(display: Pointer): Int
            fun XQueryTree(
                display: Pointer,
                window: Long,
                root: LongByReference,
                parent: LongByReference,
                children: PointerByReference,
                count: IntByReference,
            ): Int
            fun XFree(data: Pointer): Int
        }

        private val xlib: Xlib by lazy { Native.load("X11", Xlib::class.java) }
        private val display: Pointer? by lazy { xlib.XOpenDisplay(null) }

        /** Raises [window] only when a sibling is stacked above it (XQueryTree lists children bottom to top). */
        fun raiseIfCovered(window: Long) {
            val parent = query(window)?.first ?: return
            val siblings = query(parent)?.second ?: return
            if (siblings.isNotEmpty() && siblings.last() != window) raise(window)
        }

        /** The parent of [window] and its children, bottom to top; null when the query fails. */
        private fun query(window: Long): Pair<Long, LongArray>? {
            val connection = display ?: return null
            val parent = LongByReference()
            val children = PointerByReference()
            val count = IntByReference()
            if (xlib.XQueryTree(connection, window, LongByReference(), parent, children, count) == 0) return null
            val list = children.value
            val ids = LongArray(count.value) { index -> list.getLong(index.toLong() * Native.LONG_SIZE) }
            list?.let(xlib::XFree)
            return parent.value to ids
        }

        fun raise(window: Long) {
            val connection = display ?: return
            xlib.XRaiseWindow(connection, window)
            xlib.XFlush(connection)
        }
    }

    private companion object {
        const val STACKING_CHECK_MILLIS = 500
    }
}
