@file:Suppress(
    "ArgumentListWrapping", "MagicNumber", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.desktop.streaming

import dev.vimivo.core.util.SafeLogger
import dev.vimivo.desktop.platform.DesktopDirectories
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopStreamHistoryRepository(
    private val storageDir: Path = DesktopDirectories.resolve().data,
) {
    private val historyFile: Path get() = storageDir.resolve(FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun loadRecentStreams(): List<String> = runCatching {
        if (!historyFile.exists()) return emptyList()
        val text = historyFile.readText()
        if (text.isBlank()) return emptyList()
        json.decodeFromString<List<String>>(text)
    }.getOrDefault(emptyList())

    fun recordStream(url: String): Result<Unit> = runCatching {
        val sanitized = SafeLogger.redactUrl(url.trim())
        if (sanitized.isBlank()) return@runCatching

        val existing = loadRecentStreams().filterNot { it.equals(sanitized, ignoreCase = true) }
        val updated = (listOf(sanitized) + existing).take(MAX_RECENT_STREAMS)

        if (!storageDir.exists()) {
            Files.createDirectories(storageDir)
        }
        historyFile.writeText(json.encodeToString(updated))
    }

    fun clearRecentStreams(): Result<Unit> = runCatching {
        if (historyFile.exists()) {
            Files.delete(historyFile)
        }
    }

    companion object {
        private const val FILE_NAME = "recent_streams.json"
        private const val MAX_RECENT_STREAMS = 10
    }
}
