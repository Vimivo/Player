package dev.vimivo.desktop.capture

import dev.vimivo.desktop.platform.DesktopPlatform

/**
 * Default capture sources as mpv `av://<ffmpeg input device>:<device>` URLs (libavdevice).
 * Windows DirectShow needs the device name, so the default is only a template the user edits.
 */
internal object CaptureSources {
    fun webcam(platform: DesktopPlatform): String = when (platform) {
        DesktopPlatform.MACOS, DesktopPlatform.UNKNOWN -> "av://avfoundation:0"
        DesktopPlatform.WINDOWS -> "av://dshow:video=Integrated Camera"
        DesktopPlatform.LINUX -> "av://v4l2:/dev/video0"
    }

    fun screen(platform: DesktopPlatform): String = when (platform) {
        DesktopPlatform.MACOS, DesktopPlatform.UNKNOWN -> "av://avfoundation:Capture screen 0"
        DesktopPlatform.WINDOWS -> "av://gdigrab:desktop"
        DesktopPlatform.LINUX -> "av://x11grab::0.0"
    }
}
