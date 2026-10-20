package dev.vimivo.desktop.platform

import dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter
import dev.vimivo.desktop.playback.MpvNative
import java.awt.Window

/**
 * macOS window helpers in the Cocoa bridge: per-window dark appearance (JDK 17 has no property for it) and real
 * child windows for overlays, which AWT's owned windows are not (they would stay behind in a fullscreen space).
 * Calls block briefly on the AppKit thread, so they run off the event thread. Failures are recorded, never thrown.
 */
internal object MacWindows {
    private val available: Boolean by lazy {
        DesktopPlatform.detect() == DesktopPlatform.MACOS &&
            runCatching { MpvNative.loadCocoaBridge() }
                .onFailure { DesktopDiagnosticExporter.defaultBuffer.recordError("MAC_WINDOWS", it) }
                .isSuccess
    }

    fun setDarkAppearance(window: Window): Boolean =
        available && window.isDisplayable && runCatching { setDarkAppearanceNative(window) }.getOrDefault(false)

    fun attachChild(owner: Window, child: Window): Boolean =
        available && owner.isDisplayable && child.isDisplayable &&
            runCatching { attachChildNative(owner, child) }.getOrDefault(false)

    /** Makes Vimivo the active app (UI capture: macOS delivers hover only to the active app). */
    fun activate() {
        if (available) runCatching { activateNative() }
    }

    private external fun setDarkAppearanceNative(window: Window): Boolean

    private external fun activateNative()

    private external fun attachChildNative(owner: Window, child: Window): Boolean
}
