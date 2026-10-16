@file:Suppress(
    "ArgumentListWrapping", "Indentation", "LongMethod", "MagicNumber", "MaximumLineLength", "MaxLineLength",
    "ParameterListWrapping", "PropertyWrapping", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

internal data class ScanResult(val discovered: Int, val indexed: Int, val errors: Int)

/** Bounded, cancellation-aware discovery; it never follows symlinks. */
internal class DesktopMediaScanner(private val database: DesktopDatabase) {
    suspend fun scan(rootId: String, root: Path, onProgress: (Int) -> Unit = {}): Result<ScanResult> = runCatching {
        require(Files.isDirectory(root)) { "Library folder is unavailable: $root" }
        val candidates = mutableListOf<MediaItem>()
        var errors = 0
        val scanContext = currentCoroutineContext()
        Files.walk(root).use { paths ->
            paths.filter { path ->
                scanContext.ensureActive()
                Files.isRegularFile(path) && path.extension().lowercase() in SUPPORTED_EXTENSIONS
            }.forEach { path ->
                runCatching { path.toMedia(rootId) }.onSuccess { candidates += it }.onFailure { errors++ }
                if (candidates.size % 32 == 0) onProgress(candidates.size)
            }
        }
        database.transaction { connection ->
            connection.prepareStatement("UPDATE media_items SET available=0 WHERE root_id=?").use { statement ->
                statement.setString(1, rootId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,1) ON CONFLICT(uri) DO UPDATE SET root_id=excluded.root_id,file_name=excluded.file_name," +
                    "title=excluded.title,kind=excluded.kind,mime_type=excluded.mime_type,size_bytes=excluded.size_bytes," +
                    "modified_ms=excluded.modified_ms,indexed_ms=excluded.indexed_ms,available=1",
            ).use { statement ->
                candidates.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                    chunk.forEach { item ->
                        statement.setString(1, item.id.value); statement.setString(2, rootId); statement.setString(3, item.uri)
                        statement.setString(4, item.fileName); statement.setString(5, item.title); statement.setString(6, item.kind.name)
                        statement.setString(7, item.mimeType); statement.setLong(8, item.sizeBytes ?: 0); statement.setLong(9, (item.dateModifiedEpochSeconds ?: 0) * 1000)
                        statement.setLong(10, System.currentTimeMillis()); statement.addBatch()
                    }
                    statement.executeBatch()
                }
            }
            connection.prepareStatement("UPDATE library_roots SET last_scan_ms=?, available=1 WHERE id=?").use { statement ->
                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, rootId); statement.executeUpdate()
            }
        }
        ScanResult(candidates.size, candidates.size, errors)
    }

    private fun Path.toMedia(rootId: String): MediaItem {
        val attributes = Files.readAttributes(this, BasicFileAttributes::class.java)
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
