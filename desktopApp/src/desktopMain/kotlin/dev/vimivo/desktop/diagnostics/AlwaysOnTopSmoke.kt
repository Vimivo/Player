package dev.vimivo.desktop.diagnostics

import kotlinx.coroutines.delay
import java.io.File

/** Smoke check that the View ▸ Always on Top choice reaches the real window (V1 #17). */
internal object AlwaysOnTopSmoke {
    private const val SETTLE_MILLIS = 500L

    /** [toggle] flips the choice; [isOnTop] reads the window's own flag. Checks on, then off again. */
    suspend fun check(file: File, toggle: () -> Unit, isOnTop: () -> Boolean?): DesktopSmokeResult {
        val result = File("${file.name}#always-on-top")
        val before = isOnTop() ?: return DesktopSmokeResult.Failed(result, "no player window")
        toggle()
        delay(SETTLE_MILLIS)
        val on = isOnTop()
        toggle()
        delay(SETTLE_MILLIS)
        val off = isOnTop()
        return if (!before && on == true && off == false) {
            DesktopSmokeResult.Passed(result, positionMillis = 0, videoTracks = 1)
        } else {
            val reason = "window flag went $before -> $on -> $off, expected false -> true -> false"
            DesktopSmokeResult.Failed(result, reason)
        }
    }
}
