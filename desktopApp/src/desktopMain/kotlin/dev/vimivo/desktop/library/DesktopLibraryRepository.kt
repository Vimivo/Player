@file:Suppress("ArgumentListWrapping", "Indentation", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping", "TooGenericExceptionCaught", "Wrapping")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal data class LibraryRoot(
    val id: String,
    val path: Path,
    val displayName: String,
    val available: Boolean,
    val lastScanMillis: Long?,
    val mediaCount: Int = 0,
)

/** Explicit invalidation is used because JDBC does not provide observable table queries. */
internal class DesktopLibraryRepository(private val database: DesktopDatabase) {
    private val mutableGeneration = MutableStateFlow(0L)
    val generation: StateFlow<Long> = mutableGeneration

    fun roots(): List<LibraryRoot> = database.transaction { connection ->
        connection.createStatement().executeQuery("SELECT id,uri,display_name,available,last_scan_ms,(SELECT COUNT(*) FROM media_items WHERE root_id=library_roots.id AND available=1) FROM library_roots ORDER BY display_name COLLATE NOCASE").use { rows ->
            buildList { while (rows.next()) add(LibraryRoot(rows.getString(1), Path.of(java.net.URI(rows.getString(2))), rows.getString(3), rows.getInt(4) != 0, rows.getLong(5).takeIf { !rows.wasNull() }, rows.getInt(6))) }
        }
    }

    fun addRoot(path: Path): Result<LibraryRoot> = runCatching {
        val normalized = path.toRealPath()
        require(Files.isDirectory(normalized)) { "Selected location is not a readable folder" }
        val uri = normalized.toUri().toString()
        database.transaction { connection ->
            connection.prepareStatement("SELECT id,display_name,available,last_scan_ms FROM library_roots WHERE uri=?").use { query ->
                query.setString(1, uri); query.executeQuery().use { rows ->
                    if (rows.next()) return@transaction LibraryRoot(rows.getString(1), normalized, rows.getString(2), rows.getInt(3) != 0, rows.getLong(4).takeIf { !rows.wasNull() })
                }
            }
            val root = LibraryRoot(UUID.randomUUID().toString(), normalized, normalized.fileName.toString(), true, null)
            connection.prepareStatement("INSERT INTO library_roots(id,uri,display_name,added_at_ms,available) VALUES(?,?,?,?,1)").use { insert ->
                insert.setString(1, root.id); insert.setString(2, uri); insert.setString(3, root.displayName); insert.setLong(4, System.currentTimeMillis()); insert.executeUpdate()
            }
            root
        }.also { invalidate() }
    }

    fun media(kind: MediaKind, query: String = ""): List<MediaItem> = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                "x.bitrate,x.track_number,x.disc_number,x.release_year FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                "WHERE m.available=1 AND m.kind=? AND (COALESCE(NULLIF(x.title,''),m.title) LIKE ? OR m.file_name LIKE ? " +
                "OR x.artist LIKE ? OR x.album LIKE ?) ORDER BY COALESCE(NULLIF(x.title,''),m.title) COLLATE NOCASE LIMIT 500",
        ).use { statement ->
            statement.setString(1, kind.name)
            repeat(4) { statement.setString(it + 2, "%$query%") }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toMedia()) } }
        }
    }

    fun root(id: String): LibraryRoot? = roots().firstOrNull { it.id == id }
    fun removeRoot(id: String): Result<Unit> = runCatching {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM media_items WHERE root_id=?").use { statement -> statement.setString(1, id); statement.executeUpdate() }
            connection.prepareStatement("DELETE FROM library_roots WHERE id=?").use { statement -> statement.setString(1, id); require(statement.executeUpdate() == 1) { "Library root was not found" } }
        }
        invalidate()
    }

    fun refreshAvailability(): Result<Unit> = runCatching {
        val currentRoots = roots()
        database.transaction { connection ->
            currentRoots.forEach { root ->
                connection.prepareStatement("UPDATE library_roots SET available=? WHERE id=?").use { statement ->
                    statement.setInt(1, if (Files.isDirectory(root.path) && Files.isReadable(root.path)) 1 else 0)
                    statement.setString(2, root.id)
                    statement.executeUpdate()
                }
            }
        }
        invalidate()
    }
    fun invalidate() { mutableGeneration.value++ }

    private fun java.sql.ResultSet.toMedia() = MediaItem(
        id = MediaId(getString(1)), uri = getString(2), title = getString(3), kind = MediaKind.valueOf(getString(4)),
        fileName = getString(5), sizeBytes = nullableLong(6), mimeType = getString(7),
        dateModifiedEpochSeconds = nullableLong(8)?.div(1_000), durationMillis = nullableLong(9), artist = getString(10),
        album = getString(11), artworkUri = getString(12), albumArtist = getString(13), genre = getString(14),
        width = nullableInt(15), height = nullableInt(16), frameRate = nullableFloat(17), bitrate = nullableInt(18),
        trackNumber = nullableInt(19), discNumber = nullableInt(20), releaseYear = nullableInt(21), source = MediaSource.SAF_TREE,
    )

    private fun java.sql.ResultSet.nullableLong(column: Int) = getLong(column).takeIf { !wasNull() }
    private fun java.sql.ResultSet.nullableInt(column: Int) = getInt(column).takeIf { !wasNull() }
    private fun java.sql.ResultSet.nullableFloat(column: Int) = getFloat(column).takeIf { !wasNull() }
}
