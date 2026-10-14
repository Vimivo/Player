package dev.vimivo.desktop.platform

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopPlatformTest {
    @Test
    fun detectsSupportedDesktopOperatingSystems() {
        assertEquals(DesktopPlatform.MACOS, DesktopPlatform.detect("Mac OS X"))
        assertEquals(DesktopPlatform.WINDOWS, DesktopPlatform.detect("Windows 11"))
        assertEquals(DesktopPlatform.LINUX, DesktopPlatform.detect("Linux"))
        assertEquals(DesktopPlatform.UNKNOWN, DesktopPlatform.detect("Plan 9"))
    }

    @Test
    fun windowsUsesLocalAppDataWithoutHardcodedUserPath() {
        val directories = DesktopDirectories.resolve(
            platform = DesktopPlatform.WINDOWS,
            environment = mapOf("LOCALAPPDATA" to "C:\\Users\\测试 User\\AppData\\Local"),
            userHome = "C:\\Users\\ignored",
        )
        assertEquals(Path.of("C:\\Users\\测试 User\\AppData\\Local", "Vimivo", "vimivo.sqlite"), directories.database)
        assertEquals(Path.of("C:\\Users\\测试 User\\AppData\\Local", "Vimivo", "cache"), directories.cache)
    }

    @Test
    fun windowsFallsBackToConventionalPerUserDirectory() {
        val directories = DesktopDirectories.resolve(DesktopPlatform.WINDOWS, emptyMap(), "C:\\Users\\Vimivo")
        assertEquals(Path.of("C:\\Users\\Vimivo", "AppData", "Local", "Vimivo"), directories.data)
    }

    @Test
    fun linuxUsesXdgDirectoriesAndKeepsCacheSeparate() {
        val directories = DesktopDirectories.resolve(
            platform = DesktopPlatform.LINUX,
            environment = mapOf("XDG_DATA_HOME" to "/var/data", "XDG_CACHE_HOME" to "/var/cache"),
            userHome = "/home/vimivo",
        )
        assertEquals(Path.of("/var/data/vimivo/vimivo.sqlite"), directories.database)
        assertEquals(Path.of("/var/cache/vimivo"), directories.cache)
    }

    @Test
    fun missingHomeFailsInsteadOfWritingToWorkingDirectory() {
        assertFailsWith<IllegalArgumentException> {
            DesktopDirectories.resolve(DesktopPlatform.WINDOWS, emptyMap(), null)
        }
    }
}
