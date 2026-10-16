@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.logging

import dev.vimivo.core.logging.LogAppender
import dev.vimivo.core.logging.StructuredLogEntry
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Thread-safe local file appender with bounded size and circular log rotation.
 * Ensures local disk usage is strictly bounded and logs never grow uncontrollably.
 */
class DesktopLogFileAppender(
    val logDirectory: File,
    val baseFileName: String = "vimivo.log",
    val maxFileSizeBytes: Long = 1024 * 1024L, // 1 MB default
    val maxBackupIndex: Int = 3,
) : LogAppender {

    private val lock = Any()
    private val mainLogFile = File(logDirectory, baseFileName)

    init {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
    }

    override fun append(entry: StructuredLogEntry) {
        synchronized(lock) {
            try {
                rotateIfNecessary()
                val line = entry.toFormattedLine() + "\n"
                FileOutputStream(mainLogFile, true).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(line)
                        writer.flush()
                    }
                }
            } catch (_: Exception) {
                // Diagnostics must never crash the desktop application
            }
        }
    }

    fun rotateIfNecessary() {
        if (!mainLogFile.exists() || mainLogFile.length() < maxFileSizeBytes) {
            return
        }

        // Delete oldest backup if it reached max backup index
        val oldestBackup = File(logDirectory, "$baseFileName.$maxBackupIndex")
        if (oldestBackup.exists()) {
            oldestBackup.delete()
        }

        // Shift existing backups down: e.g. .2 -> .3, .1 -> .2
        for (i in (maxBackupIndex - 1) downTo 1) {
            val current = File(logDirectory, "$baseFileName.$i")
            if (current.exists()) {
                val next = File(logDirectory, "$baseFileName.${i + 1}")
                current.renameTo(next)
            }
        }

        // Rotate main log to .1
        val firstBackup = File(logDirectory, "$baseFileName.1")
        mainLogFile.renameTo(firstBackup)
    }

    fun getMainLogFile(): File = mainLogFile
}
