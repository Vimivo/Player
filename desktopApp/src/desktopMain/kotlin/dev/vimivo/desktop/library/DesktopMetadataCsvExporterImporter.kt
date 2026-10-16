@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "LongParameterList", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "NestedBlockDepth",
    "ParameterListWrapping", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.FieldAction
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MetadataChangeset
import dev.vimivo.core.model.MetadataWriteMode
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal data class CsvMetadataRecord(
    val mediaId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMs: Long? = null,
    val kind: String = "AUDIO",
    val uri: String? = null,
)

internal data class CsvImportPlan(
    val matchedItems: List<Pair<MediaItem, MetadataChangeset>>,
    val unmatchedRows: List<CsvMetadataRecord>,
    val validationErrors: List<String>,
)

internal data class CsvImportExecutionResult(
    val appliedCount: Int,
    val failedItems: List<Pair<String, String>>,
)

internal class DesktopMetadataCsvExporterImporter(
    private val editorService: DesktopMetadataEditorService,
) {

    fun exportCsv(
        items: List<MediaItem>,
        targetFile: File,
        includePaths: Boolean = false,
    ): Result<Unit> = runCatching {
        targetFile.parentFile?.mkdirs()
        val builder = StringBuilder()
        // Prepend UTF-8 BOM so spreadsheet viewers render Unicode/Bengali seamlessly
        builder.append('\uFEFF')

        val headers = mutableListOf(
            "media_id", "title", "artist", "album", "album_artist",
            "genre", "release_year", "track_number", "disc_number",
            "duration_ms", "kind",
        )
        if (includePaths) headers.add("uri")
        builder.append(headers.joinToString(",")).append("\r\n")

        items.forEach { item ->
            val row = mutableListOf(
                escapeCsvCell(item.id.value),
                escapeCsvCell(item.title),
                escapeCsvCell(item.artist ?: ""),
                escapeCsvCell(item.album ?: ""),
                escapeCsvCell(item.albumArtist ?: ""),
                escapeCsvCell(item.genre ?: ""),
                escapeCsvCell(item.releaseYear?.toString() ?: ""),
                escapeCsvCell(item.trackNumber?.toString() ?: ""),
                escapeCsvCell(item.discNumber?.toString() ?: ""),
                escapeCsvCell(item.durationMillis?.toString() ?: ""),
                escapeCsvCell(item.kind.name),
            )
            if (includePaths) {
                row.add(escapeCsvCell(item.uri))
            }
            builder.append(row.joinToString(",")).append("\r\n")
        }

        targetFile.writeText(builder.toString(), StandardCharsets.UTF_8)
    }

    fun previewImportCsv(
        csvFile: File,
        existingItems: List<MediaItem>,
    ): Result<CsvImportPlan> = runCatching {
        require(csvFile.isFile && csvFile.canRead()) { "CSV file cannot be read: ${csvFile.name}" }
        require(csvFile.length() <= MAX_CSV_SIZE_BYTES) { "CSV file exceeds maximum allowed size (10 MB)" }

        val rawText = csvFile.readText(StandardCharsets.UTF_8).removePrefix("\uFEFF")
        val lines = parseCsvLines(rawText)
        require(lines.isNotEmpty()) { "CSV file is empty" }

        val headerLine = lines.first()
        val headerMap = headerLine.mapIndexed { index, name -> name.trim().lowercase() to index }.toMap()
        val idCol = headerMap["media_id"] ?: error("Missing required 'media_id' header in CSV")

        val existingById = existingItems.associateBy { it.id.value }
        val matched = mutableListOf<Pair<MediaItem, MetadataChangeset>>()
        val unmatched = mutableListOf<CsvMetadataRecord>()
        val errors = mutableListOf<String>()

        lines.drop(1).forEachIndexed { lineIdx, row ->
            if (row.all(String::isBlank)) return@forEachIndexed
            if (row.size <= idCol) {
                errors.add("Row ${lineIdx + 2}: Insufficient columns")
                return@forEachIndexed
            }

            val mediaId = sanitizeImportFormula(row[idCol].trim())
            val title = headerMap["title"]?.let { if (it < row.size) sanitizeImportFormula(row[it].trim()) else "" } ?: ""
            val artist = headerMap["artist"]?.let { if (it < row.size) sanitizeImportFormula(row[it].trim()).takeIf(String::isNotBlank) else null }
            val album = headerMap["album"]?.let { if (it < row.size) sanitizeImportFormula(row[it].trim()).takeIf(String::isNotBlank) else null }
            val albumArtist = headerMap["album_artist"]?.let { if (it < row.size) sanitizeImportFormula(row[it].trim()).takeIf(String::isNotBlank) else null }
            val genre = headerMap["genre"]?.let { if (it < row.size) sanitizeImportFormula(row[it].trim()).takeIf(String::isNotBlank) else null }
            val year = headerMap["release_year"]?.let { if (it < row.size) row[it].trim().toIntOrNull() else null }
            val track = headerMap["track_number"]?.let { if (it < row.size) row[it].trim().toIntOrNull() else null }
            val disc = headerMap["disc_number"]?.let { if (it < row.size) row[it].trim().toIntOrNull() else null }
            val uri = headerMap["uri"]?.let { if (it < row.size) row[it].trim().takeIf(String::isNotBlank) else null }

            val record = CsvMetadataRecord(
                mediaId = mediaId,
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                releaseYear = year,
                trackNumber = track,
                discNumber = disc,
                uri = uri,
            )

            val existing = existingById[mediaId]
            if (existing != null) {
                val changeset = MetadataChangeset(
                    title = if (title.isNotBlank() && title != existing.title) FieldAction.Set(title) else FieldAction.Unchanged,
                    artist = when {
                        artist != null && artist != existing.artist -> FieldAction.Set(artist)
                        artist == null && existing.artist != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                    album = when {
                        album != null && album != existing.album -> FieldAction.Set(album)
                        album == null && existing.album != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                    albumArtist = when {
                        albumArtist != null && albumArtist != existing.albumArtist -> FieldAction.Set(albumArtist)
                        albumArtist == null && existing.albumArtist != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                    genre = when {
                        genre != null && genre != existing.genre -> FieldAction.Set(genre)
                        genre == null && existing.genre != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                    releaseYear = when {
                        year != null && year != existing.releaseYear -> FieldAction.Set(year)
                        year == null && existing.releaseYear != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                    trackNumber = when {
                        track != null && track != existing.trackNumber -> FieldAction.Set(track)
                        track == null && existing.trackNumber != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                    discNumber = when {
                        disc != null && disc != existing.discNumber -> FieldAction.Set(disc)
                        disc == null && existing.discNumber != null -> FieldAction.Clear
                        else -> FieldAction.Unchanged
                    },
                )
                matched.add(existing to changeset)
            } else {
                unmatched.add(record)
            }
        }

        CsvImportPlan(matchedItems = matched, unmatchedRows = unmatched, validationErrors = errors)
    }

    fun applyImport(
        plan: CsvImportPlan,
        mode: MetadataWriteMode,
        backupDir: Path? = null,
    ): CsvImportExecutionResult {
        var applied = 0
        val failed = mutableListOf<Pair<String, String>>()

        plan.matchedItems.forEach { (item, changeset) ->
            try {
                when (mode) {
                    MetadataWriteMode.DATABASE_OVERRIDE -> {
                        editorService.saveOverride(
                            mediaId = item.id,
                            title = (changeset.title as? FieldAction.Set)?.value ?: item.title,
                            artist = when (val a = changeset.artist) {
                                is FieldAction.Set -> a.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.artist
                            },
                            album = when (val a = changeset.album) {
                                is FieldAction.Set -> a.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.album
                            },
                            albumArtist = when (val a = changeset.albumArtist) {
                                is FieldAction.Set -> a.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.albumArtist
                            },
                            genre = when (val g = changeset.genre) {
                                is FieldAction.Set -> g.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.genre
                            },
                            releaseYear = when (val y = changeset.releaseYear) {
                                is FieldAction.Set -> y.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.releaseYear
                            },
                            trackNumber = when (val t = changeset.trackNumber) {
                                is FieldAction.Set -> t.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.trackNumber
                            },
                            discNumber = when (val d = changeset.discNumber) {
                                is FieldAction.Set -> d.value
                                is FieldAction.Clear -> null
                                FieldAction.Unchanged -> item.discNumber
                            },
                        )
                        applied++
                    }
                    MetadataWriteMode.EMBEDDED_FILE_TAG -> {
                        val file = runCatching { File(URI(item.uri)) }.getOrNull()
                        if (file != null && file.isFile && editorService.isFormatSupportedForEmbeddedWrite(file)) {
                            val res = editorService.applyEmbeddedTagWrite(file, changeset, backupDir)
                            if (res.isSuccess) {
                                applied++
                            } else {
                                failed.add(item.id.value to (res.exceptionOrNull()?.message ?: "Tag write failed"))
                            }
                        } else {
                            failed.add(item.id.value to "File unsupported or unreadable for embedded write")
                        }
                    }
                }
            } catch (error: Throwable) {
                failed.add(item.id.value to (error.message ?: "Failed applying CSV changes"))
            }
        }

        return CsvImportExecutionResult(appliedCount = applied, failedItems = failed)
    }

    companion object {
        const val MAX_CSV_SIZE_BYTES = 10L * 1024L * 1024L // 10 MB limit

        fun escapeCsvCell(raw: String): String {
            // Mitigate CSV Formula Injection: prefix sensitive trigger characters with a single quote
            val safeContent = if (raw.isNotEmpty() && raw[0] in listOf('=', '+', '-', '@', '\t', '\r')) {
                "'$raw"
            } else {
                raw
            }

            val needsQuotes = safeContent.contains(',') || safeContent.contains('"') ||
                safeContent.contains('\n') || safeContent.contains('\r')
            return if (needsQuotes) {
                "\"" + safeContent.replace("\"", "\"\"") + "\""
            } else {
                safeContent
            }
        }

        fun sanitizeImportFormula(cell: String): String {
            // Remove formula-escaping single quote if present
            return if (cell.startsWith('\'') && cell.length > 1 && cell[1] in listOf('=', '+', '-', '@')) {
                cell.substring(1)
            } else {
                cell
            }
        }

        fun parseCsvLines(csvText: String): List<List<String>> {
            val lines = mutableListOf<List<String>>()
            val currentLine = mutableListOf<String>()
            val currentCell = StringBuilder()
            var inQuotes = false
            var i = 0

            while (i < csvText.length) {
                val c = csvText[i]
                when {
                    c == '"' -> {
                        if (inQuotes && i + 1 < csvText.length && csvText[i + 1] == '"') {
                            currentCell.append('"')
                            i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    c == ',' && !inQuotes -> {
                        currentLine.add(currentCell.toString())
                        currentCell.clear()
                    }
                    (c == '\r' || c == '\n') && !inQuotes -> {
                        if (c == '\r' && i + 1 < csvText.length && csvText[i + 1] == '\n') {
                            i++
                        }
                        currentLine.add(currentCell.toString())
                        currentCell.clear()
                        lines.add(currentLine.toList())
                        currentLine.clear()
                    }
                    else -> currentCell.append(c)
                }
                i++
            }
            if (currentCell.isNotEmpty() || currentLine.isNotEmpty()) {
                currentLine.add(currentCell.toString())
                lines.add(currentLine.toList())
            }
            return lines
        }
    }
}
