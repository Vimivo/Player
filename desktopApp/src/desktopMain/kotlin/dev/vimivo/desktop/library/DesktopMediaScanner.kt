@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod", "MagicNumber", "MaximumLineLength", "MaxLineLength",
    "ParameterListWrapping", "PropertyWrapping", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.sql.Connection

internal data class ScanResult(
    val discovered: Int,
    val indexed: Int,
    val errors: Int,
    val added: Int = indexed,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val unavailable: Int = 0,
)

private data class ExistingScanItem(
    val sizeBytes: Long?,
    val modifiedMs: Long?,
    val available: Boolean,
)

/** Bounded, cancellation-aware discovery; it never follows symlinks and updates incrementally. */
internal class DesktopMediaScanner(private val database: DesktopDatabase) {
    suspend fun scan(rootId: String, root: Path, onProgress: (Int) -> Unit = {}): Result<ScanResult> = runCatching {
        require(Files.isDirectory(root)) { "Library folder is unavailable: $root" }
        val candidates = mutableListOf<MediaItem>()
        var errors = 0
        val scanContext = currentCoroutineContext()

        Files.walkFileTree(
            root,
            emptySet<FileVisitOption>(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    scanContext.ensureActive()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    scanContext.ensureActive()
                    if (attrs.isRegularFile && file.extension().lowercase() in SUPPORTED_EXTENSIONS) {
                        runCatching { file.toMedia(rootId, attrs) }
                            .onSuccess { candidates += it }
                            .onFailure { errors++ }
                        if (candidates.size % 32 == 0) onProgress(candidates.size)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    errors++
                    return FileVisitResult.CONTINUE
                }
            },
        )

        database.transaction { connection ->
            val existing = loadExisting(connection, rootId)
            var added = 0
            var updated = 0
            var unchanged = 0
            val foundUris = HashSet<String>(candidates.size)
            val itemsToInsert = mutableListOf<MediaItem>()
            val itemsToUpdate = mutableListOf<MediaItem>()

            for (item in candidates) {
                foundUris.add(item.uri)
                val current = existing[item.uri]
                val itemModifiedMs = (item.dateModifiedEpochSeconds ?: 0) * 1000
                if (current == null) {
                    added++
                    itemsToInsert.add(item)
                } else if (current.sizeBytes != item.sizeBytes || current.modifiedMs != itemModifiedMs || !current.available) {
                    updated++
                    itemsToUpdate.add(item)
                } else {
                    unchanged++
                }
            }

            if (itemsToInsert.isNotEmpty()) {
                insertBatch(connection, rootId, itemsToInsert)
            }
            if (itemsToUpdate.isNotEmpty()) {
                updateBatch(connection, rootId, itemsToUpdate)
            }

            var unavailable = 0
            val missingUris = existing.keys.filter { it !in foundUris && existing[it]?.available == true }
            if (missingUris.isNotEmpty()) {
                unavailable = missingUris.size
                markUnavailable(connection, missingUris)
            }

            connection.prepareStatement("UPDATE library_roots SET last_scan_ms=?, available=1 WHERE id=?").use { statement ->
                statement.setLong(1, System.currentTimeMillis())
                statement.setString(2, rootId)
                statement.executeUpdate()
            }

            ScanResult(
                discovered = candidates.size,
                indexed = candidates.size,
                errors = errors,
                added = added,
                updated = updated,
                unchanged = unchanged,
                unavailable = unavailable,
            )
        }
    }

    private fun loadExisting(connection: Connection, rootId: String): Map<String, ExistingScanItem> {
        val existing = mutableMapOf<String, ExistingScanItem>()
        connection.prepareStatement("SELECT uri, size_bytes, modified_ms, available FROM media_items WHERE root_id=?").use { stmt ->
            stmt.setString(1, rootId)
            stmt.executeQuery().use { rows ->
                while (rows.next()) {
                    existing[rows.getString(1)] = ExistingScanItem(
                        sizeBytes = rows.getLong(2).takeIf { !rows.wasNull() },
                        modifiedMs = rows.getLong(3).takeIf { !rows.wasNull() },
                        available = rows.getInt(4) != 0,
                    )
                }
            }
        }
        return existing
    }

    private fun insertBatch(connection: Connection, rootId: String, items: List<MediaItem>) {
        connection.prepareStatement(
            "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,1)",
        ).use { statement ->
            items.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                chunk.forEach { item ->
                    statement.setString(1, item.id.value)
                    statement.setString(2, rootId)
                    statement.setString(3, item.uri)
                    statement.setString(4, item.fileName)
                    statement.setString(5, item.title)
                    statement.setString(6, item.kind.name)
                    statement.setString(7, item.mimeType)
                    statement.setLong(8, item.sizeBytes ?: 0)
                    statement.setLong(9, (item.dateModifiedEpochSeconds ?: 0) * 1000)
                    statement.setLong(10, System.currentTimeMillis())
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun updateBatch(connection: Connection, rootId: String, items: List<MediaItem>) {
        connection.prepareStatement(
            "UPDATE media_items SET root_id=?, file_name=?, title=?, kind=?, mime_type=?, " +
                "size_bytes=?, modified_ms=?, indexed_ms=?, available=1 WHERE uri=?",
        ).use { statement ->
            items.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                chunk.forEach { item ->
                    statement.setString(1, rootId)
                    statement.setString(2, item.fileName)
                    statement.setString(3, item.title)
                    statement.setString(4, item.kind.name)
                    statement.setString(5, item.mimeType)
                    statement.setLong(6, item.sizeBytes ?: 0)
                    statement.setLong(7, (item.dateModifiedEpochSeconds ?: 0) * 1000)
                    statement.setLong(8, System.currentTimeMillis())
                    statement.setString(9, item.uri)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun markUnavailable(connection: Connection, missingUris: List<String>) {
        connection.prepareStatement("UPDATE media_items SET available=0 WHERE uri=?").use { statement ->
            missingUris.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                chunk.forEach { uri ->
                    statement.setString(1, uri)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun Path.toMedia(rootId: String, attrs: BasicFileAttributes? = null): MediaItem {
        val attributes = attrs ?: Files.readAttributes(this, BasicFileAttributes::class.java)
        val extension = extension().lowercase()
        val kind = if (extension in AUDIO_EXTENSIONS) MediaKind.AUDIO else MediaKind.VIDEO
        val uri = toRealPath().toUri().toString()
        return MediaItem(
            id = MediaId(identity(rootId, uri)), uri = uri, title = fileName.toString().substringBeforeLast('.'), kind = kind,
            fileName = fileName.toString(), sizeBytes = attributes.size(), dateModifiedEpochSeconds = attributes.lastModifiedTime().toMillis() / 1000,
            folderName = parent?.fileName?.toString(), folderPath = parent?.toString(), source = MediaSource.SAF_TREE,
        )
    }

    private fun Path.extension() = fileName.toString().substringAfterLast('.', "")
    private fun identity(rootId: String, uri: String) = MessageDigest.getInstance("SHA-256").digest("$rootId\u0000$uri".toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val BATCH_CHUNK_SIZE = 500
        val AUDIO_EXTENSIONS = setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "alac", "aiff", "aif", "wma", "ape")
        val SUPPORTED_EXTENSIONS = AUDIO_EXTENSIONS + setOf("mp4", "mkv", "mov", "avi", "webm", "m4v", "mpeg", "mpg", "ts", "m2ts", "flv", "wmv", "3gp")
    }
}
