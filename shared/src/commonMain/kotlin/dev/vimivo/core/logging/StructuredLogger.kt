@file:Suppress("ArgumentListWrapping", "LongParameterList", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.logging

import dev.vimivo.core.concurrency.PlatformLock
import dev.vimivo.core.error.VimivoError
import dev.vimivo.core.error.VimivoErrorCategory
import dev.vimivo.core.util.SafeLogger
import kotlinx.serialization.Serializable

enum class LogLevel(val priority: Int) {
    VERBOSE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
}

@Serializable
data class StructuredLogEntry(
    val timestampEpochMs: Long,
    val level: LogLevel,
    val component: String,
    val category: VimivoErrorCategory? = null,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toFormattedLine(): String {
        val catPart = category?.let { " ($it)" } ?: ""
        val metaPart = if (metadata.isNotEmpty()) " | meta=$metadata" else ""
        return "[$timestampEpochMs] [$level] [$component]$catPart: $message$metaPart"
    }
}

fun interface LogAppender {
    fun append(entry: StructuredLogEntry)
}

/**
 * Thread-safe, bounded, privacy-safe structured logger for Vimivo.
 * Redacts user paths, query parameters, credentials, and tokens from all log entries.
 * Caps in-memory logs in a bounded circular buffer to prevent unbounded memory growth.
 */
class StructuredLogger(
    val appVersion: String = "1.0.0-rc.1",
    val platform: String = "desktop",
    private val maxBufferSize: Int = 250,
) {
    var minLogLevel: LogLevel = LogLevel.INFO
    private val buffer = mutableListOf<StructuredLogEntry>()
    private val appenders = mutableListOf<LogAppender>()
    private val lock = PlatformLock()

    fun addAppender(appender: LogAppender) {
        lock.withLock {
            appenders.add(appender)
        }
    }

    fun removeAppender(appender: LogAppender) {
        lock.withLock {
            appenders.remove(appender)
        }
    }

    fun log(
        level: LogLevel,
        component: String,
        message: String,
        category: VimivoErrorCategory? = null,
        metadata: Map<String, String> = emptyMap(),
        timestampEpochMs: Long = 0L,
    ) {
        if (level.priority < minLogLevel.priority) return

        val sanitizedMessage = SafeLogger.sanitize(message)
        val sanitizedMetadata = sanitizeMetadata(metadata)

        val entry = StructuredLogEntry(
            timestampEpochMs = timestampEpochMs,
            level = level,
            component = component,
            category = category,
            message = sanitizedMessage,
            metadata = sanitizedMetadata,
        )

        val currentAppenders = lock.withLock {
            if (buffer.size >= maxBufferSize) {
                buffer.removeAt(0)
            }
            buffer.add(entry)
            appenders.toList()
        }

        currentAppenders.forEach { appender ->
            runCatching { appender.append(entry) }
        }
    }

    fun v(component: String, message: String, metadata: Map<String, String> = emptyMap()) =
        log(LogLevel.VERBOSE, component, message, null, metadata)

    fun d(component: String, message: String, metadata: Map<String, String> = emptyMap()) =
        log(LogLevel.DEBUG, component, message, null, metadata)

    fun i(component: String, message: String, metadata: Map<String, String> = emptyMap()) =
        log(LogLevel.INFO, component, message, null, metadata)

    fun w(component: String, message: String, category: VimivoErrorCategory? = null, metadata: Map<String, String> = emptyMap()) =
        log(LogLevel.WARN, component, message, category, metadata)

    fun e(component: String, message: String, category: VimivoErrorCategory? = null, metadata: Map<String, String> = emptyMap()) =
        log(LogLevel.ERROR, component, message, category, metadata)

    fun logError(error: VimivoError) {
        val level = if (error.isRecoverable) LogLevel.WARN else LogLevel.ERROR
        val meta = mutableMapOf<String, String>()
        meta["errorCode"] = error.code
        error.sanitizedTechnicalDetail?.let { meta["detail"] = it }

        log(
            level = level,
            component = error.component,
            message = error.userMessage,
            category = error.category,
            metadata = meta,
        )
    }

    fun snapshotEntries(): List<StructuredLogEntry> = lock.withLock {
        buffer.toList()
    }

    fun clear() = lock.withLock {
        buffer.clear()
    }

    private fun sanitizeMetadata(meta: Map<String, String>): Map<String, String> {
        if (meta.isEmpty()) return emptyMap()
        val sensitiveKeys = setOf(
            "password", "pass", "token", "secret", "key", "auth",
            "authorization", "api_key", "credential", "private_key",
        )

        return meta.mapNotNull { (k, v) ->
            if (sensitiveKeys.any { k.contains(it, ignoreCase = true) }) {
                k to "[REDACTED]"
            } else {
                k to SafeLogger.sanitize(v)
            }
        }.toMap()
    }

    companion object {
        val default = StructuredLogger()
    }
}
