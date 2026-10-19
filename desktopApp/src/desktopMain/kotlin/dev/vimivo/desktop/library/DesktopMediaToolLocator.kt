@file:Suppress(
    "ArgumentListWrapping", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth",
    "ReturnCount", "Wrapping",
)

package dev.vimivo.desktop.library

import java.io.File

/**
 * Finds an optional system ffprobe for the media-info probe (which falls back to header sniffing without it).
 * Library metadata and thumbnails use the bundled libmpv instead (`MpvMediaInspector`). Never runs a shell.
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
