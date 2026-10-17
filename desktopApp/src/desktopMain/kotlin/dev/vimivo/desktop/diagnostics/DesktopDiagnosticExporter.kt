@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.diagnostics

import dev.vimivo.core.diagnostics.DiagnosticBuffer
import dev.vimivo.core.diagnostics.DiagnosticReport
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Desktop diagnostic manager and safe exporter.
 * Collects local system environment details, redacts sensitive paths/tokens,
 * and allows user-initiated export to a local file.
 */
class DesktopDiagnosticExporter(
    private val buffer: DiagnosticBuffer = defaultBuffer,
    val appVersion: String = APP_VERSION,
    val buildCommit: String = BUILD_COMMIT,
) {
    fun createReport(
        playbackBackend: String = "libmpv 2.0.0 (JNA)",
        now: Long = System.currentTimeMillis(),
    ): DiagnosticReport {
        return DiagnosticReport(
            appVersion = appVersion,
            buildCommit = buildCommit,
            platform = "Compose Desktop (JVM)",
            osVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            architecture = System.getProperty("os.arch") ?: "unknown",
            generatedTimestampEpochMs = now,
            playbackBackend = playbackBackend,
            recentEvents = buffer.snapshotEvents(),
            errorSummaries = buffer.snapshotErrors(),
        )
    }

    fun exportToPath(destination: Path, playbackBackend: String = "libmpv 2.0.0 (JNA)"): Result<Path> {
        return runCatching {
            val destinationFile = destination.toFile()
            val canonicalPath = runCatching { destinationFile.canonicalPath }.getOrDefault(destinationFile.absolutePath)
            val absolutePath = destinationFile.absolutePath

            // Security check: Refuse to write to dangerous root system locations
            val dangerousPrefixes = listOf(
                "/System",
                "/etc",
                "/private/etc",
                "/bin",
                "/sbin",
                "/usr",
                "/var/root",
                "/private/var/root",
                "/var/db",
                "/private/var/db",
                "C:\\Windows",
                "C:\\Program Files",
            )
            val isDangerous = dangerousPrefixes.any { prefix ->
                canonicalPath.startsWith(prefix, ignoreCase = true) ||
                    absolutePath.startsWith(prefix, ignoreCase = true)
            }
            if (isDangerous) {
                error("Export destination is within a protected system directory: $canonicalPath")
            }

            destination.parent?.toFile()?.mkdirs()
            val report = createReport(playbackBackend)
            val markdown = report.toFormattedMarkdown()
            destination.writeText(markdown)
            destination
        }
    }

    companion object {
        const val APP_VERSION = "1.0.0-rc.1"
        const val BUILD_COMMIT = "3d77bde"

        val defaultBuffer = DiagnosticBuffer(maxCapacity = 150)
    }
}
