package dev.vimivo.desktop.library

import dev.vimivo.desktop.platform.DesktopDirectories
import dev.vimivo.playback.RecentMediaEntry
import dev.vimivo.playback.RecentMediaList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * On-device "Open Recent" list for files and streams the user opened explicitly.
 * Stored as JSON in the per-user data directory; never uploaded.
 */
class DesktopRecentMediaRepository(
    private val storageDir: Path = DesktopDirectories.resolve().data,
    private val capacity: Int = RecentMediaList.DEFAULT_CAPACITY,
) {
    private val file: Path get() = storageDir.resolve(FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val mutableEntries = MutableStateFlow(load())

    val entries: StateFlow<List<RecentMediaEntry>> = mutableEntries.asStateFlow()

    fun record(entry: RecentMediaEntry): Result<Unit> = runCatching {
        synchronized(lock) {
            val updated = RecentMediaList.record(mutableEntries.value, entry, capacity)
            if (updated == mutableEntries.value) return@runCatching
            persist(updated)
            mutableEntries.value = updated
        }
    }

    fun clear(): Result<Unit> = runCatching {
        synchronized(lock) {
            Files.deleteIfExists(file)
            mutableEntries.value = emptyList()
        }
    }

    private fun load(): List<RecentMediaEntry> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<Map<String, String>>>(file.readText()).mapNotNull { row ->
            val uri = row[KEY_URI]?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            RecentMediaEntry(uri, row[KEY_TITLE].orEmpty().ifBlank { uri }, row[KEY_VIDEO] == "true")
        }.take(capacity)
    }.getOrDefault(emptyList())

    private fun persist(entries: List<RecentMediaEntry>) {
        Files.createDirectories(storageDir)
        val rows = entries.map { mapOf(KEY_URI to it.uri, KEY_TITLE to it.title, KEY_VIDEO to it.isVideo.toString()) }
        // Write-then-move so a crash never leaves a truncated list behind.
        val temporary = storageDir.resolve("$FILE_NAME.tmp")
        temporary.writeText(json.encodeToString(rows))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val FILE_NAME = "recent_media.json"
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
        const val KEY_VIDEO = "video"
    }
}
