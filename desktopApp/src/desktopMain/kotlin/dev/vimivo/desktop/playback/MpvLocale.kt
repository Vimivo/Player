package dev.vimivo.desktop.playback

import com.sun.jna.Library
import com.sun.jna.Native
import dev.vimivo.desktop.platform.DesktopPlatform

/**
 * libmpv refuses to create a context unless `LC_NUMERIC` is "C" ("Non-C locale detected"), because it
 * parses numbers with the C library. The JVM adopts the user's locale (e.g. bn_BD, de_DE) at startup, so
 * on Linux and macOS the numeric locale must be reset before every `mpv_create`. Windows is unaffected.
 */
internal object MpvLocale {
    private interface CLibrary : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    /** `LC_NUMERIC` differs by C library: 1 on glibc/musl Linux, 4 on macOS. */
    fun lcNumeric(platform: DesktopPlatform): Int? = when (platform) {
        DesktopPlatform.LINUX -> LINUX_LC_NUMERIC
        DesktopPlatform.MACOS -> MACOS_LC_NUMERIC
        DesktopPlatform.WINDOWS, DesktopPlatform.UNKNOWN -> null
    }

    /** Returns the numeric locale now in effect, or null when the platform does not need the fix. */
    fun ensureCNumeric(platform: DesktopPlatform = DesktopPlatform.detect()): String? {
        val category = lcNumeric(platform) ?: return null
        val libc = Native.load("c", CLibrary::class.java)
        libc.setlocale(category, "C")
        return libc.setlocale(category, null)
    }

    private const val LINUX_LC_NUMERIC = 1
    private const val MACOS_LC_NUMERIC = 4
}
