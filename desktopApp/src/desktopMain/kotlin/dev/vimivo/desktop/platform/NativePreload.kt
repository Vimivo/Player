package dev.vimivo.desktop.platform

import dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter
import dev.vimivo.desktop.diagnostics.StartupTrace
import dev.vimivo.desktop.playback.MpvNative
import java.awt.Font
import java.awt.font.FontRenderContext
import kotlin.concurrent.thread

/**
 * Loads what the first window would otherwise load on the UI thread: JNA's dispatch library, libmpv (with its FFmpeg
 * dependencies), SQLite's native library, and the AWT font registry (built the first time Swing measures menu text).
 * Profiling showed these serialized on the event dispatch thread before the first window; here they overlap with AWT
 * and Compose start. The macOS bridge is not preloaded: it links AWT's native library, which must load through the
 * toolkit. Each step is idempotent, and a failure only costs the overlap: the real call site loads and reports it.
 */
internal object NativePreload {
    fun start(): Thread = thread(name = "vimivo-native-preload", isDaemon = true) {
        step("jna") { Class.forName("com.sun.jna.Native") }
        step("libmpv") { MpvNative.load() }
        step("sqlite") { Class.forName("org.sqlite.JDBC") }
        step("fonts") {
            Font(Font.DIALOG, Font.PLAIN, MENU_FONT_SIZE).getStringBounds("Vimivo", FontRenderContext(null, true, true))
        }
        StartupTrace.mark("preloaded")
    }

    private inline fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { DesktopDiagnosticExporter.defaultBuffer.recordError("NATIVE_PRELOAD_$name", it) }
    }

    private const val MENU_FONT_SIZE = 13
}
