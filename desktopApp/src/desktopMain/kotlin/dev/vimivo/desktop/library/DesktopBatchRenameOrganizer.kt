@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.OrganizePlanItem
import dev.vimivo.core.model.RenamePlanItem
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal data class RenameExecutionResult(
    val succeeded: List<MediaId>,
    val failed: List<Pair<MediaId, String>>,
)

internal data class OrganizeExecutionResult(
    val succeeded: List<MediaId>,
    val failed: List<Pair<MediaId, String>>,
)

internal class DesktopBatchRenameOrganizer(private val database: DesktopDatabase) {

    fun evaluateRename(items: List<MediaItem>, template: String): List<RenamePlanItem> {
        val plannedNames = mutableSetOf<String>()
        return items.map { item ->
            val sourceFile = runCatching { File(URI(item.uri)) }.getOrNull()
            if (sourceFile == null || !sourceFile.isFile) {
                return@map RenamePlanItem(
                    mediaId = item.id,
                    sourceUri = item.uri,
                    originalFileName = item.fileName ?: item.title,
                    proposedFileName = "",
                    proposedUri = "",
                    conflict = true,
                    conflictReason = "Source file does not exist",
                )
            }

            val ext = sourceFile.extension
            val newBaseName = expandTemplate(template, item, sourceFile.nameWithoutExtension)
            val sanitized = sanitizeFileName(newBaseName)
            val newFileName = if (ext.isNotEmpty()) "$sanitized.$ext" else sanitized
            val parentDir = sourceFile.parentFile ?: File(".")
            val targetFile = File(parentDir, newFileName)
            val targetUri = targetFile.toPath().toUri().toString()

            val normalizedKey = targetFile.absolutePath.lowercase()
            val hasCollision = when {
                sanitized.isBlank() -> true
                plannedNames.contains(normalizedKey) -> true
                targetFile.exists() && !targetFile.absolutePath.equals(sourceFile.absolutePath, ignoreCase = false) -> true
                isReservedWindowsName(sanitized) -> true
                else -> false
            }
            val reason = when {
                sanitized.isBlank() -> "Proposed filename is empty"
                plannedNames.contains(normalizedKey) -> "Duplicate destination in batch"
                targetFile.exists() && !targetFile.absolutePath.equals(sourceFile.absolutePath, ignoreCase = false) ->
                    "Destination file already exists on disk"
                isReservedWindowsName(sanitized) -> "Filename uses a reserved system name"
                else -> null
            }

            if (!hasCollision) plannedNames.add(normalizedKey)

            RenamePlanItem(
                mediaId = item.id,
                sourceUri = item.uri,
                originalFileName = sourceFile.name,
                proposedFileName = newFileName,
                proposedUri = targetUri,
                conflict = hasCollision,
                conflictReason = reason,
            )
        }
    }

    fun executeRename(plans: List<RenamePlanItem>): RenameExecutionResult {
        val succeeded = mutableListOf<MediaId>()
        val failed = mutableListOf<Pair<MediaId, String>>()

        plans.forEach { plan ->
            if (plan.conflict) {
                failed.add(plan.mediaId to (plan.conflictReason ?: "Conflict present"))
                return@forEach
            }
            try {
                val source = File(URI(plan.sourceUri))
                val target = File(URI(plan.proposedUri))

                if (source.absolutePath == target.absolutePath) {
                    succeeded.add(plan.mediaId)
                    return@forEach
                }

                // If only case changes on case-insensitive filesystem, use temporary file
                if (source.absolutePath.equals(target.absolutePath, ignoreCase = true)) {
                    val temp = File(source.parentFile, "vimivo_tmp_${System.currentTimeMillis()}_${source.name}")
                    Files.move(source.toPath(), temp.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } else {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }

                // Reconcile database references
                database.transaction { connection ->
                    connection.prepareStatement(
                        "UPDATE media_items SET uri=?, file_name=?, modified_ms=? WHERE id=?",
                    ).use { statement ->
                        statement.setString(1, target.toPath().toUri().toString())
                        statement.setString(2, target.name)
                        statement.setLong(3, target.lastModified())
                        statement.setString(4, plan.mediaId.value)
                        statement.executeUpdate()
                    }
                }
                succeeded.add(plan.mediaId)
            } catch (error: Throwable) {
                failed.add(plan.mediaId to (error.message ?: "Rename failed"))
            }
        }
        return RenameExecutionResult(succeeded, failed)
    }

    fun evaluateOrganize(
        items: List<MediaItem>,
        targetRoot: Path,
        template: String = "{artist}/{album}/{track} - {title}",
    ): List<OrganizePlanItem> {
        val plannedDestinations = mutableSetOf<String>()
        return items.map { item ->
            val sourceFile = runCatching { File(URI(item.uri)) }.getOrNull()
            if (sourceFile == null || !sourceFile.isFile) {
                return@map OrganizePlanItem(
                    mediaId = item.id,
                    sourceUri = item.uri,
                    originalPath = item.uri,
                    proposedRelativePath = "",
                    proposedDestinationPath = "",
                    conflict = true,
                    conflictReason = "Source file does not exist",
                )
            }

            val ext = sourceFile.extension
            val relativeRaw = expandTemplate(template, item, sourceFile.nameWithoutExtension)
            val segments = relativeRaw.split('/', '\\').filter(String::isNotBlank).map(::sanitizeFileName)
            val relativePath = segments.joinToString(File.separator) + if (ext.isNotEmpty()) ".$ext" else ""
            val destFile = targetRoot.resolve(relativePath).toFile()

            val normalizedKey = destFile.absolutePath.lowercase()
            val hasCollision = when {
                relativePath.isBlank() -> true
                plannedDestinations.contains(normalizedKey) -> true
                destFile.exists() && !destFile.absolutePath.equals(sourceFile.absolutePath, ignoreCase = false) -> true
                else -> false
            }
            val reason = when {
                relativePath.isBlank() -> "Proposed path is empty"
                plannedDestinations.contains(normalizedKey) -> "Duplicate destination path in batch"
                destFile.exists() && !destFile.absolutePath.equals(sourceFile.absolutePath, ignoreCase = false) ->
                    "Target file already exists on disk"
                else -> null
            }

            if (!hasCollision) plannedDestinations.add(normalizedKey)

            OrganizePlanItem(
                mediaId = item.id,
                sourceUri = item.uri,
                originalPath = sourceFile.absolutePath,
                proposedRelativePath = relativePath,
                proposedDestinationPath = destFile.absolutePath,
                conflict = hasCollision,
                conflictReason = reason,
            )
        }
    }

    fun executeOrganize(plans: List<OrganizePlanItem>, isMove: Boolean): OrganizeExecutionResult {
        val succeeded = mutableListOf<MediaId>()
        val failed = mutableListOf<Pair<MediaId, String>>()

        plans.forEach { plan ->
            if (plan.conflict) {
                failed.add(plan.mediaId to (plan.conflictReason ?: "Conflict present"))
                return@forEach
            }
            try {
                val source = File(plan.originalPath)
                val target = File(plan.proposedDestinationPath)

                Files.createDirectories(target.parentFile.toPath())
                if (isMove) {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    // Reconcile database references
                    database.transaction { connection ->
                        connection.prepareStatement(
                            "UPDATE media_items SET uri=?, file_name=?, modified_ms=? WHERE id=?",
                        ).use { statement ->
                            statement.setString(1, target.toPath().toUri().toString())
                            statement.setString(2, target.name)
                            statement.setLong(3, target.lastModified())
                            statement.setString(4, plan.mediaId.value)
                            statement.executeUpdate()
                        }
                    }
                } else {
                    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                succeeded.add(plan.mediaId)
            } catch (error: Throwable) {
                failed.add(plan.mediaId to (error.message ?: "Organize failed"))
            }
        }
        return OrganizeExecutionResult(succeeded, failed)
    }

    companion object {
        fun expandTemplate(template: String, item: MediaItem, originalName: String): String {
            val trackPadded = item.trackNumber?.let { "%02d".format(it) } ?: "00"
            val discPadded = item.discNumber?.let { "%02d".format(it) } ?: "01"
            return template
                .replace("{artist}", item.artist?.trim() ?: "Unknown Artist")
                .replace("{albumArtist}", item.albumArtist?.trim() ?: item.artist?.trim() ?: "Unknown Artist")
                .replace("{album}", item.album?.trim() ?: "Unknown Album")
                .replace("{title}", item.title.trim())
                .replace("{track}", trackPadded)
                .replace("{disc}", discPadded)
                .replace("{year}", item.releaseYear?.toString() ?: "Unknown Year")
                .replace("{genre}", item.genre?.trim() ?: "Unknown Genre")
                .replace("{originalName}", originalName)
        }

        fun sanitizeFileName(name: String): String {
            val illegal = Regex("""[\\/:*?"<>|\x00-\x1F]""")
            return name.replace(illegal, "_").trim().trimEnd('.')
        }

        private val RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        )

        fun isReservedWindowsName(name: String): Boolean {
            val upper = name.substringBefore('.').uppercase()
            return upper in RESERVED_NAMES
        }
    }
}
