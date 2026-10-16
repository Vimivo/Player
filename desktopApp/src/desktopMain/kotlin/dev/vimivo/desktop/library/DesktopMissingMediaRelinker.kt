@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MissingMediaCandidate
import java.io.File
import java.net.URI

internal class DesktopMissingMediaRelinker(private val database: DesktopDatabase) {

    fun findMissingMedia(items: List<MediaItem>): List<MediaItem> = items.filter { item ->
        val file = runCatching { File(URI(item.uri)) }.getOrNull()
        file == null || !file.exists()
    }

    fun findCandidates(
        missing: MediaItem,
        searchRoots: List<File>,
        maxDepth: Int = 4,
    ): List<MissingMediaCandidate> {
        val candidates = mutableListOf<MissingMediaCandidate>()
        val missingFileName = missing.fileName?.lowercase()
        val missingTitle = missing.title.lowercase().trim()

        searchRoots.filter { it.isDirectory && it.canRead() }.forEach { root ->
            root.walkTopDown().maxDepth(maxDepth).filter { it.isFile && it.canRead() }.forEach { file ->
                val nameLower = file.name.lowercase()
                val baseLower = file.nameWithoutExtension.lowercase().trim()

                when {
                    missingFileName != null && nameLower == missingFileName -> {
                        candidates.add(
                            MissingMediaCandidate(
                                candidateMedia = missing.copy(uri = file.toURI().toString(), fileName = file.name),
                                confidence = "HIGH",
                                reason = "Exact filename match: ${file.name}",
                            ),
                        )
                    }
                    baseLower == missingTitle -> {
                        candidates.add(
                            MissingMediaCandidate(
                                candidateMedia = missing.copy(uri = file.toURI().toString(), fileName = file.name),
                                confidence = "HIGH",
                                reason = "Exact title match: ${file.nameWithoutExtension}",
                            ),
                        )
                    }
                    missing.sizeBytes != null && file.length() == missing.sizeBytes -> {
                        candidates.add(
                            MissingMediaCandidate(
                                candidateMedia = missing.copy(uri = file.toURI().toString(), fileName = file.name),
                                confidence = "MEDIUM",
                                reason = "Exact file size match (${file.length()} bytes)",
                            ),
                        )
                    }
                }
            }
        }
        return candidates.distinctBy { it.candidateMedia.uri }
    }

    fun relink(mediaId: MediaId, newFile: File): Result<Unit> = runCatching {
        require(newFile.isFile && newFile.canRead()) { "Relink target must be a readable file: ${newFile.absolutePath}" }
        database.transaction { connection ->
            connection.prepareStatement(
                "UPDATE media_items SET uri=?, file_name=?, size_bytes=?, modified_ms=?, available=1 WHERE id=?",
            ).use { statement ->
                statement.setString(1, newFile.toURI().toString())
                statement.setString(2, newFile.name)
                statement.setLong(3, newFile.length())
                statement.setLong(4, newFile.lastModified())
                statement.setString(5, mediaId.value)
                require(statement.executeUpdate() == 1) { "Media record $mediaId was not found" }
            }
        }
    }
}
