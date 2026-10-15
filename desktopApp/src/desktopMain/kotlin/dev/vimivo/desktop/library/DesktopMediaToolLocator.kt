@file:Suppress(
    "ArgumentListWrapping", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth",
    "ReturnCount", "Wrapping",
)

package dev.vimivo.desktop.library

import java.io.File

/**
 * Discovers system multimedia inspection and frame extraction tools safely.
 * Never executes shell commands or passes unsanitized parameters.
 */
internal object DesktopMediaToolLocator {
    fun ffprobePath(): String? = findExecutable(
        systemProperty = "vimivo.ffprobe.path",
        environmentVariable = "FFPROBE_PATH",
        commandName = "ffprobe",
        candidatePaths = listOf(
            "/opt/homebrew/bin/ffprobe",
            "/usr/local/bin/ffprobe",
            "/usr/bin/ffprobe",
            "C:\\ffmpeg\\bin\\ffprobe.exe",
        ),
    )

    fun ffmpegPath(): String? = findExecutable(
        systemProperty = "vimivo.ffmpeg.path",
        environmentVariable = "FFMPEG_PATH",
        commandName = "ffmpeg",
        candidatePaths = listOf(
            "/opt/homebrew/bin/ffmpeg",
            "/usr/local/bin/ffmpeg",
            "/usr/bin/ffmpeg",
            "C:\\ffmpeg\\bin\\ffmpeg.exe",
        ),
    )

    private fun findExecutable(
        systemProperty: String,
        environmentVariable: String,
        commandName: String,
        candidatePaths: List<String>,
    ): String? {
        System.getProperty(systemProperty)?.takeIf(String::isNotBlank)?.let {
            if (File(it).canExecute()) return it
        }
        System.getenv(environmentVariable)?.takeIf(String::isNotBlank)?.let {
            if (File(it).canExecute()) return it
        }
        for (path in candidatePaths) {
            val file = File(path)
            if (file.isFile && file.canExecute()) return file.absolutePath
        }
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        for (dir in pathDirs) {
            val file = File(dir, commandName)
            if (file.isFile && file.canExecute()) return file.absolutePath
            val exeFile = File(dir, "$commandName.exe")
            if (exeFile.isFile && exeFile.canExecute()) return exeFile.absolutePath
        }
        return null
    }
}
