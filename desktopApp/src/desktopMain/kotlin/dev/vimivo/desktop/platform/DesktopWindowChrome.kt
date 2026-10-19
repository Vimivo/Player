@file:Suppress("FunctionNaming") // Native C names.

package dev.vimivo.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Window
import java.io.File
import javax.swing.JFrame

/**
 * Dark window chrome for the player window, never a white title bar above black video (spec §2.1):
 * - macOS: dark appearance ([MacWindows]; JDK 17 has no property for it) with a transparent title bar over the dark
 *   window background; the title shows the file's proxy icon and name.
 * - Windows: the immersive dark title bar (`DWMWA_USE_IMMERSIVE_DARK_MODE`, Windows 10 20H1 and later).
 * - Linux: the `_GTK_THEME_VARIANT=dark` hint that GNOME-family window managers honour.
 * Each call is best effort: a failure is recorded and the default chrome stays.
 */
internal object DesktopWindowChrome {
    private val TITLE_BAR = Color(0x14, 0x14, 0x16)

    /** Call on the event thread once [window] is showing; native calls run on an IO thread. */
    suspend fun applyDark(window: Window) {
        runCatching {
            when (DesktopPlatform.detect()) {
                DesktopPlatform.MACOS -> {
                    macTitleBar(window)
                    withContext(Dispatchers.IO) { MacWindows.setDarkAppearance(window) }
                }
                DesktopPlatform.WINDOWS -> withContext(Dispatchers.IO) { windowsDark(window) }
                DesktopPlatform.LINUX -> withContext(Dispatchers.IO) { linuxDark(window) }
                DesktopPlatform.UNKNOWN -> Unit
            }
        }.onFailure { DesktopDiagnosticExporter.defaultBuffer.recordError("WINDOW_CHROME", it) }
    }

    /** macOS proxy icon: the file the window shows (null clears it). */
    fun setRepresentedFile(window: Window, file: File?) {
        if (DesktopPlatform.detect() != DesktopPlatform.MACOS) return
        (window as? JFrame)?.rootPane?.putClientProperty("Window.documentFile", file)
    }

    /** A transparent title bar over the dark window background; [MacWindows] makes its text and controls dark. */
    private fun macTitleBar(window: Window) {
        val root = (window as? JFrame)?.rootPane ?: return
        root.putClientProperty("apple.awt.transparentTitleBar", true)
        window.background = TITLE_BAR
    }

    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private fun windowsDark(window: Window) {
        val hwnd = Native.getWindowPointer(window) ?: return
        val dwm = Native.load("dwmapi", DwmApi::class.java)
        val enabled = IntByReference(1)
        // 20 = DWMWA_USE_IMMERSIVE_DARK_MODE (Windows 10 20H1+); 19 is the same flag on earlier Windows 10 builds.
        if (dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled, Int.SIZE_BYTES) != 0) {
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, enabled, Int.SIZE_BYTES)
        }
    }

    @Suppress("LongParameterList") // Mirrors Xlib's C signatures.
    private interface Xlib : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XInternAtom(display: Pointer, name: String, onlyIfExists: Boolean): Long
        fun XChangeProperty(
            display: Pointer,
            window: Long,
            property: Long,
            type: Long,
            format: Int,
            mode: Int,
            data: Pointer,
            elements: Int,
        ): Int
        fun XFlush(display: Pointer): Int
        fun XCloseDisplay(display: Pointer): Int
    }

    private fun linuxDark(window: Window) {
        val xid = Native.getWindowID(window).takeIf { it != 0L } ?: return
        val xlib = Native.load("X11", Xlib::class.java)
        val display = xlib.XOpenDisplay(null) ?: return
        try {
            val value = "dark".toByteArray(Charsets.UTF_8)
            val data = Memory(value.size.toLong()).apply { write(0, value, 0, value.size) }
            xlib.XChangeProperty(
                display,
                xid,
                xlib.XInternAtom(display, "_GTK_THEME_VARIANT", false),
                xlib.XInternAtom(display, "UTF8_STRING", false),
                BITS_PER_ELEMENT,
                PROP_MODE_REPLACE,
                data,
                value.size,
            )
            xlib.XFlush(display)
        } finally {
            xlib.XCloseDisplay(display)
        }
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
    private const val BITS_PER_ELEMENT = 8
    private const val PROP_MODE_REPLACE = 0
}
