package dev.vimivo.core.diagnostics

import dev.vimivo.core.concurrency.PlatformLock
import dev.vimivo.core.util.SafeLogger
import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticEvent(
    val timestampEpochMs: Long,
    val category: String,
    val message: String,
)

@Serializable
data class DiagnosticError(
    val timestampEpochMs: Long,
    val category: String,
    val exceptionType: String,
    val message: String,
    val sanitizedStackTrace: List<String>,
)

@Serializable
data class DiagnosticReport(
    val appVersion: String,
    val buildCommit: String,
    val platform: String,
    val osVersion: String,
    val architecture: String,
    val generatedTimestampEpochMs: Long,
    val playbackBackend: String,
    val recentEvents: List<DiagnosticEvent>,
    val errorSummaries: List<DiagnosticError>,
) {
    fun toFormattedMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# Vimivo Privacy-Safe Diagnostic Report")
        sb.appendLine()
        sb.appendLine("> [!NOTE]")
        sb.appendLine("> This report is generated locally and contains zero user credentials, passwords,")
        sb.appendLine("> private keys, personal media files, or raw media library records.")
        sb.appendLine("> All filesystem usernames and URL authentication parameters have been redacted.")
        sb.appendLine()
        sb.appendLine("## System Information")
        sb.appendLine("- **Application Version**: `$appVersion`")
        sb.appendLine("- **Build Commit**: `$buildCommit`")
        sb.appendLine("- **Platform Target**: `$platform`")
        sb.appendLine("- **OS Version**: `$osVersion`")
        sb.appendLine("- **Architecture**: `$architecture`")
        sb.appendLine("- **Playback Backend**: `$playbackBackend`")
        sb.appendLine("- **Generated Epoch MS**: `$generatedTimestampEpochMs`")
        sb.appendLine()
        sb.appendLine("## Recent Events (${recentEvents.size})")
        if (recentEvents.isEmpty()) {
            sb.appendLine("*(No events recorded)*")
        } else {
            recentEvents.forEach { event ->
                sb.appendLine("- `[${event.timestampEpochMs}]` **[${event.category}]**: ${event.message}")
            }
        }
        sb.appendLine()
        sb.appendLine("## Error Summaries (${errorSummaries.size})")
        if (errorSummaries.isEmpty()) {
            sb.appendLine("*(No errors recorded)*")
        } else {
            errorSummaries.forEach { error ->
                sb.appendLine("### ${error.exceptionType}: ${error.message}")
                sb.appendLine("- Category: `${error.category}`")
                sb.appendLine("- Timestamp: `${error.timestampEpochMs}`")
                sb.appendLine("```text")
                error.sanitizedStackTrace.take(15).forEach { sb.appendLine(it) }
                if (error.sanitizedStackTrace.size > 15) {
                    sb.appendLine("... (${error.sanitizedStackTrace.size - 15} more frames truncated)")
                }
                sb.appendLine("```")
            }
        }
        return sb.toString()
    }

    fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"appVersion\": \"${escapeJson(appVersion)}\",")
        sb.appendLine("  \"buildCommit\": \"${escapeJson(buildCommit)}\",")
        sb.appendLine("  \"platform\": \"${escapeJson(platform)}\",")
        sb.appendLine("  \"osVersion\": \"${escapeJson(osVersion)}\",")
        sb.appendLine("  \"architecture\": \"${escapeJson(architecture)}\",")
        sb.appendLine("  \"generatedTimestampEpochMs\": $generatedTimestampEpochMs,")
        sb.appendLine("  \"playbackBackend\": \"${escapeJson(playbackBackend)}\",")
        sb.appendLine("  \"recentEventsCount\": ${recentEvents.size},")
        sb.appendLine("  \"errorSummariesCount\": ${errorSummaries.size}")
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Thread-safe, bounded in-memory ring buffer for diagnostic recording.
 * Holds at most maxCapacity entries to prevent memory growth during prolonged playback.
 */
class DiagnosticBuffer(private val maxCapacity: Int = 100) {
    private val events = mutableListOf<DiagnosticEvent>()
    private val errors = mutableListOf<DiagnosticError>()
    private val lock = PlatformLock()

    fun recordEvent(category: String, message: String, timestampEpochMs: Long = 0L) {
        val sanitized = SafeLogger.sanitize(message)
        val event = DiagnosticEvent(
            timestampEpochMs = timestampEpochMs,
            category = category,
            message = sanitized,
        )
        lock.withLock {
            if (events.size >= maxCapacity) {
                events.removeAt(0)
            }
            events.add(event)
        }
    }

    fun recordError(category: String, throwable: Throwable, timestampEpochMs: Long = 0L) {
        val sanitizedMessage = SafeLogger.sanitize(throwable.message ?: "No message")
        val sanitizedStack = throwable.stackTraceToString()
            .lines()
            .map { SafeLogger.sanitize(it) }
            .filter { it.isNotBlank() }

        val errorEntry = DiagnosticError(
            timestampEpochMs = timestampEpochMs,
            category = category,
            exceptionType = throwable::class.simpleName ?: "Throwable",
            message = sanitizedMessage,
            sanitizedStackTrace = sanitizedStack,
        )
        lock.withLock {
            if (errors.size >= maxCapacity) {
                errors.removeAt(0)
            }
            errors.add(errorEntry)
        }
    }

    fun snapshotEvents(): List<DiagnosticEvent> = lock.withLock { events.toList() }
    fun snapshotErrors(): List<DiagnosticError> = lock.withLock { errors.toList() }
    fun clear() = lock.withLock {
        events.clear()
        errors.clear()
    }
}
