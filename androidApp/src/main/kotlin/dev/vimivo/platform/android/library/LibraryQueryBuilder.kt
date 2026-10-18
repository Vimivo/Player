@file:Suppress("CyclomaticComplexMethod", "Wrapping")

package dev.vimivo.platform.android.library

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.MediaKind

internal object LibraryQueryBuilder {
    /**
     * A folder granted through the Storage Access Framework often overlaps MediaStore (for example the
     * shared Movies folder). Hide the document-tree copy when MediaStore already indexes the same file.
     */
    const val NOT_SHADOWED_BY_MEDIA_STORE =
        "NOT (source != 'MEDIA_STORE' AND EXISTS (SELECT 1 FROM media AS indexed " +
            "WHERE indexed.source = 'MEDIA_STORE' AND indexed.isAvailable = 1 " +
            "AND indexed.fileName = media.fileName AND indexed.folderPath IS media.folderPath " +
            "AND indexed.sizeBytes = media.sizeBytes))"

    fun page(query: LibraryQuery): SupportSQLiteQuery {
        val where = mutableListOf("isAvailable = 1")
        val args = mutableListOf<Any>()
        appendFilters(query, where, args)
        val sort = when (query.sort) {
            LibrarySort.TITLE -> "title COLLATE NOCASE"
            LibrarySort.ARTIST -> "artist COLLATE NOCASE"
            LibrarySort.ALBUM -> "album COLLATE NOCASE"
            LibrarySort.DURATION -> "durationMillis"
            LibrarySort.DATE_ADDED -> "dateAddedEpochSeconds"
            LibrarySort.DATE_MODIFIED -> "dateModifiedEpochSeconds"
            LibrarySort.FILE_SIZE -> "sizeBytes"
            LibrarySort.TRACK_NUMBER -> "trackNumber"
            LibrarySort.LAST_PLAYED -> "lastPlayedAtEpochMillis"
        }
        val direction = if (query.descending) "DESC" else "ASC"
        args += query.limit
        args += query.offset
        return SimpleSQLiteQuery(
            "SELECT * FROM media WHERE ${where.joinToString(" AND ")} " +
                "ORDER BY $sort $direction, id ASC LIMIT ? OFFSET ?",
            args.toTypedArray(),
        )
    }

    fun count(query: LibraryQuery): SupportSQLiteQuery {
        val where = mutableListOf("isAvailable = 1")
        val args = mutableListOf<Any>()
        appendFilters(query, where, args)
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM media WHERE ${where.joinToString(" AND ")}",
            args.toTypedArray(),
        )
    }

    fun categories(kind: MediaKind, field: LibraryCategoryField): SupportSQLiteQuery {
        val column = when (field) {
            LibraryCategoryField.ALBUM -> "album"
            LibraryCategoryField.ARTIST -> "artist"
            LibraryCategoryField.ALBUM_ARTIST -> "albumArtist"
            LibraryCategoryField.GENRE -> "genre"
            LibraryCategoryField.FOLDER -> "folderPath"
        }
        return SimpleSQLiteQuery(
            "SELECT $column AS name, COUNT(*) AS itemCount, MAX(artworkUri) AS artworkUri " +
                "FROM media WHERE isAvailable = 1 AND $NOT_SHADOWED_BY_MEDIA_STORE AND kind = ? " +
                "AND $column IS NOT NULL AND $column != '' " +
                "GROUP BY $column ORDER BY $column COLLATE NOCASE",
            arrayOf(kind.name),
        )
    }

    private fun appendFilters(query: LibraryQuery, where: MutableList<String>, args: MutableList<Any>) {
        where += NOT_SHADOWED_BY_MEDIA_STORE
        query.kind?.let { where += "kind = ?"; args += it.name }
        query.album?.let { where += "album = ?"; args += it }
        query.artist?.let { where += "artist = ?"; args += it }
        query.albumArtist?.let { where += "albumArtist = ?"; args += it }
        query.genre?.let { where += "genre = ?"; args += it }
        query.folderPath?.let { where += "folderPath = ?"; args += it }
        query.minimumDurationMillis?.let { where += "durationMillis >= ?"; args += it }
        query.maximumDurationMillis?.let { where += "durationMillis <= ?"; args += it }
        query.mimeType?.let { where += "mimeType = ?"; args += it }
        if (query.favoritesOnly) where += "isFavorite = 1"
        if (query.recentlyWatchedOnly) {
            where += "kind = ?"
            args += MediaKind.VIDEO.name
            where += "lastPlayedAtEpochMillis IS NOT NULL"
        }
        if (query.recentlyPlayedOnly) where += "lastPlayedAtEpochMillis IS NOT NULL"
        if (query.continueWatchingOnly) {
            where += "kind = ?"
            args += MediaKind.VIDEO.name
            where += "lastPlaybackPositionMillis > 0"
        }
        if (query.text.isNotBlank()) {
            where += "(title LIKE ? ESCAPE '\\' OR artist LIKE ? ESCAPE '\\' OR album LIKE ? ESCAPE '\\' " +
                "OR folderPath LIKE ? ESCAPE '\\' OR fileName LIKE ? ESCAPE '\\')"
            val term = "${query.text.trim().escapeLike()}%"
            repeat(5) { args += term }
        }
    }
}

private fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
