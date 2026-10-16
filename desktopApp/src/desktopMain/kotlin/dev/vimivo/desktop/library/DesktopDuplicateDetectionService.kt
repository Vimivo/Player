@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.DuplicateConfidence
import dev.vimivo.core.model.DuplicateGroup
import dev.vimivo.core.model.MediaItem
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal class DesktopDuplicateDetectionService(private val database: DesktopDatabase) {

    fun computeContentHash(file: File): String {
        require(file.isFile && file.canRead()) { "Cannot hash non-existent or unreadable file: ${file.name}" }
        val length = file.length()
        val md = MessageDigest.getInstance("SHA-256")
        val sampleSize = 64 * 1024 // 64 KB

        if (length <= sampleSize * 3) {
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
        } else {
            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(sampleSize)

                // Head sample
                raf.seek(0)
                raf.readFully(buffer)
                md.update(buffer)

                // Mid sample
                raf.seek((length / 2) - (sampleSize / 2))
                raf.readFully(buffer)
                md.update(buffer)

                // Tail sample
                raf.seek(length - sampleSize)
                raf.readFully(buffer)
                md.update(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun findDuplicates(items: List<MediaItem>): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()

        // 1. Exact content duplicates by file size + hash
        val bySize = items.filter { (it.sizeBytes ?: 0L) > 0L }.groupBy { it.sizeBytes!! }
        bySize.filter { it.value.size > 1 }.forEach { (_, candidateList) ->
            val byHash = candidateList.groupBy { item ->
                val file = runCatching { File(URI(item.uri)) }.getOrNull()
                if (file != null && file.exists()) {
                    runCatching { getOrComputeHash(item, file) }.getOrNull()
                } else null
            }
            byHash.forEach { (hash, matchedItems) ->
                if (hash != null && matchedItems.size > 1) {
                    groups.add(
                        DuplicateGroup(
                            id = UUID.randomUUID().toString(),
                            confidence = DuplicateConfidence.EXACT_CONTENT_MATCH,
                            items = matchedItems,
                            reason = "Exact content match (${matchedItems.first().sizeBytes} bytes, hash: ${hash.take(8)}...)",
                        ),
                    )
                }
            }
        }

        // 2. Possible duplicates by normalized title + artist + duration within 2s
        val exactMatchedIds = groups.flatMap { it.items.map { item -> item.id } }.toSet()
        val remaining = items.filter { it.id !in exactMatchedIds }
        val byMetadata = remaining.groupBy { item ->
            val normTitle = item.title.lowercase().trim()
            val normArtist = item.artist?.lowercase()?.trim().orEmpty()
            "$normTitle::$normArtist"
        }
        byMetadata.filter { it.value.size > 1 }.forEach { (_, matchedItems) ->
            // Check if duration matches within 2000 ms
            val firstDuration = matchedItems.first().durationMillis
            val allSimilarDuration = firstDuration != null && matchedItems.all {
                val d = it.durationMillis ?: return@all false
                kotlin.math.abs(d - firstDuration) <= 2000
            }
            if (allSimilarDuration) {
                groups.add(
                    DuplicateGroup(
                        id = UUID.randomUUID().toString(),
                        confidence = DuplicateConfidence.POSSIBLE_DUPLICATE,
                        items = matchedItems,
                        reason = "Matching title and artist with equivalent duration",
                    ),
                )
            }
        }

        return groups
    }

    private fun getOrComputeHash(item: MediaItem, file: File): String {
        val existing = database.transaction { connection ->
            connection.prepareStatement(
                "SELECT content_hash, modified_ms FROM media_hashes WHERE media_id=?",
            ).use { statement ->
                statement.setString(1, item.id.value)
                statement.executeQuery().use { rows ->
                    if (rows.next() && rows.getLong(2) == file.lastModified()) rows.getString(1) else null
                }
            }
        }
        if (existing != null) return existing

        val newHash = computeContentHash(file)
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO media_hashes(media_id, content_hash, file_size_bytes, modified_ms, updated_at_ms) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT(media_id) DO UPDATE SET content_hash=excluded.content_hash, file_size_bytes=excluded.file_size_bytes, " +
                    "modified_ms=excluded.modified_ms, updated_at_ms=excluded.updated_at_ms",
            ).use { statement ->
                statement.setString(1, item.id.value)
                statement.setString(2, newHash)
                statement.setLong(3, file.length())
                statement.setLong(4, file.lastModified())
                statement.setLong(5, System.currentTimeMillis())
                statement.executeUpdate()
            }
        }
        return newHash
    }
}
