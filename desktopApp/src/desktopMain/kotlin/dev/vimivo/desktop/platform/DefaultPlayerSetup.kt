package dev.vimivo.desktop.platform

import java.awt.Desktop
import java.net.URI

/** What "Make Vimivo the default player" does on each OS; operating systems never allow doing it silently. */
internal sealed interface DefaultPlayerResult {
    /** The OS settings page opened; the user chooses Vimivo there. */
    data object OpenedSystemSettings : DefaultPlayerResult

    /** Vimivo is now the default for these MIME types (Linux, after the user confirmed). */
    data class Applied(val mimeTypes: Int) : DefaultPlayerResult

    /** The OS has no API for it; show these steps instead. */
    data class Instructions(val steps: String) : DefaultPlayerResult
    data class Failed(val reason: String) : DefaultPlayerResult
}

internal object DefaultPlayerSetup {
    const val LINUX_DESKTOP_ENTRY = "dev.vimivo.desktop.desktop"
    const val MAC_STEPS = "In Finder, select a video or song, choose File › Get Info, pick Vimivo under " +
        "\"Open with\", then click \"Change All…\". Repeat once for each file type you want."

    /** The `xdg-mime` invocation for Linux, as an argument list (never a shell string). */
    fun linuxCommand(types: List<MediaType> = DesktopMediaTypes.all): List<String> =
        listOf("xdg-mime", "default", LINUX_DESKTOP_ENTRY) +
            types.filter { it.family != MediaFamily.SUBTITLE }.map(MediaType::mimeType).distinct()

    /** Linux changes defaults directly, so callers must ask the user first and pass [confirmed]. */
    fun makeDefault(platform: DesktopPlatform = DesktopPlatform.detect(), confirmed: Boolean): DefaultPlayerResult =
        runCatching {
            when (platform) {
                DesktopPlatform.WINDOWS -> {
                    Desktop.getDesktop().browse(URI(WindowsFileAssociations.DEFAULT_APPS_URI))
                    DefaultPlayerResult.OpenedSystemSettings
                }
                DesktopPlatform.LINUX -> if (confirmed) applyLinux() else DefaultPlayerResult.Failed("Not confirmed")
                DesktopPlatform.MACOS -> DefaultPlayerResult.Instructions(MAC_STEPS)
                DesktopPlatform.UNKNOWN -> DefaultPlayerResult.Failed("Unsupported operating system")
            }
        }.getOrElse { DefaultPlayerResult.Failed(it.message ?: "Could not change the default player") }

    private fun applyLinux(): DefaultPlayerResult {
        val command = linuxCommand()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return if (process.waitFor() == 0) {
            DefaultPlayerResult.Applied(command.size - LINUX_FIXED_ARGUMENTS)
        } else {
            DefaultPlayerResult.Failed(output.ifBlank { "xdg-mime failed" })
        }
    }

    private const val LINUX_FIXED_ARGUMENTS = 3
}
