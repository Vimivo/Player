package dev.vimivo.desktop.platform

import java.nio.file.Path

internal enum class DesktopPlatform {
    MACOS,
    WINDOWS,
    LINUX,
    UNKNOWN,
    ;

    companion object {
        fun detect(osName: String = System.getProperty("os.name").orEmpty()): DesktopPlatform = when {
            osName.startsWith("Mac", ignoreCase = true) -> MACOS
            osName.startsWith("Windows", ignoreCase = true) -> WINDOWS
            osName.startsWith("Linux", ignoreCase = true) -> LINUX
            else -> UNKNOWN
        }
    }
}

internal data class DesktopApplicationDirectories(
    val data: Path,
    val cache: Path,
) {
    val database: Path get() = data.resolve("vimivo.sqlite")
    val artwork: Path get() = cache.resolve("artwork")
    val thumbnails: Path get() = cache.resolve("thumbnails")
}

internal object DesktopDirectories {
    fun resolve(
        platform: DesktopPlatform = DesktopPlatform.detect(),
        environment: Map<String, String> = System.getenv(),
        userHome: String? = System.getProperty("user.home"),
    ): DesktopApplicationDirectories {
        val home = requireNotNull(userHome?.takeIf(String::isNotBlank)) { "The user home directory is unavailable" }
        return when (platform) {
            DesktopPlatform.MACOS -> DesktopApplicationDirectories(
                data = Path.of(home, "Library", "Application Support", APP_NAME),
                cache = Path.of(home, "Library", "Caches", APP_NAME),
            )
            DesktopPlatform.WINDOWS -> {
                val localAppData = environment["LOCALAPPDATA"]?.takeIf(String::isNotBlank)
                    ?: Path.of(home, "AppData", "Local").toString()
                val root = Path.of(localAppData, APP_NAME)
                DesktopApplicationDirectories(data = root, cache = root.resolve("cache"))
            }
            DesktopPlatform.LINUX -> {
                val dataHome = environment["XDG_DATA_HOME"]?.takeIf(String::isNotBlank)
                    ?: Path.of(home, ".local", "share").toString()
                val cacheHome = environment["XDG_CACHE_HOME"]?.takeIf(String::isNotBlank)
                    ?: Path.of(home, ".cache").toString()
                DesktopApplicationDirectories(Path.of(dataHome, APP_DIRECTORY), Path.of(cacheHome, APP_DIRECTORY))
            }
            DesktopPlatform.UNKNOWN -> error("Unsupported desktop operating system")
        }
    }

    private const val APP_NAME = "Vimivo"
    private const val APP_DIRECTORY = "vimivo"
}
