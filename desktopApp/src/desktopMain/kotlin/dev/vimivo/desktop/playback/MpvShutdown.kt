package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import dev.vimivo.desktop.platform.DesktopPlatform

/** How the libmpv core is stopped before it is destroyed. */
internal object MpvShutdown {
    private const val IDLE_TIMEOUT_NANOS = 2_000_000_000L
    private const val POLL_MILLIS = 10L
    private const val COREAUDIO_QUIESCE_MILLIS = 150L

    /**
     * Shuts playback down while the libmpv core is still alive, before `mpv_terminate_destroy`:
     * 1. without gapless audio, `stop` closes the audio output (mpv otherwise keeps it open into idle and only tears it
     *    down inside terminate), which removes its CoreAudio device listener;
     * 2. wait until the player is idle;
     * 3. on macOS, give CoreAudio a moment to deliver the device notifications it sends when the AudioUnit closes,
     *    while every listener's owner (the device-list watcher lives until terminate) still exists.
     * A notification arriving after terminate crashed in mpv's `hotplug_cb` (docs/evidence/D-coreaudio-shutdown).
     */
    fun stopBeforeDestroy(mpv: MpvNative, handle: Pointer, platform: DesktopPlatform) {
        runCatching {
            mpv.mpv_set_property_string(handle, "gapless-audio", "no")
            mpv.mpv_command(handle, StringArray(arrayOf("stop")))
            val deadline = System.nanoTime() + IDLE_TIMEOUT_NANOS
            while (System.nanoTime() < deadline && !isIdle(mpv, handle)) Thread.sleep(POLL_MILLIS)
            if (platform == DesktopPlatform.MACOS) Thread.sleep(COREAUDIO_QUIESCE_MILLIS)
        }
    }

    private fun isIdle(mpv: MpvNative, handle: Pointer): Boolean {
        val value = mpv.mpv_get_property_string(handle, "idle-active") ?: return false
        return try { value.getString(0) == "yes" } finally { mpv.mpv_free(value) }
    }
}
