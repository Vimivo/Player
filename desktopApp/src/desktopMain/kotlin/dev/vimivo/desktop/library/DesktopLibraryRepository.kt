@file:Suppress(
    "ArgumentListWrapping", "Indentation", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse",
    "ParameterListWrapping", "SpacingBetweenDeclarationsWithAnnotations", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.AlbumDetails
import dev.vimivo.core.model.AlbumItem
import dev.vimivo.core.model.ArtistDetails
import dev.vimivo.core.model.ArtistItem
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

    @Volatile private var cachedAlbumGeneration = -1L
    @Volatile private var cachedAlbums: List<AlbumItem> = emptyList()
    @Volatile private var cachedArtistGeneration = -1L
    @Volatile private var cachedArtists: List<ArtistItem> = emptyList()

    fun media(kind: MediaKind, query: String = ""): List<MediaItem> = database.transaction { connection ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            connection.prepareStatement(
                "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                    "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                    "x.bitrate,x.track_number,x.disc_number,x.release_year FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                    "WHERE m.available=1 AND m.kind=? ORDER BY COALESCE(NULLIF(x.title,''),m.title) COLLATE NOCASE LIMIT 500",
            ).use { statement ->
                statement.setString(1, kind.name)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toMedia()) } }
            }
        } else {
            connection.prepareStatement(
                "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                    "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                    "x.bitrate,x.track_number,x.disc_number,x.release_year FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                    "WHERE m.available=1 AND m.kind=? AND (COALESCE(NULLIF(x.title,''),m.title) LIKE ? OR m.file_name LIKE ? " +
                    "OR x.artist LIKE ? OR x.album LIKE ?) ORDER BY COALESCE(NULLIF(x.title,''),m.title) COLLATE NOCASE LIMIT 500",
            ).use { statement ->
                statement.setString(1, kind.name)
                repeat(4) { statement.setString(it + 2, "%$trimmed%") }
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toMedia()) } }
            }
        }
    }

    fun media(ids: Collection<MediaId>, includeUnavailable: Boolean = false): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val orderedIds = ids.map(MediaId::value)
        return database.transaction { connection ->
            val placeholders = orderedIds.distinct().joinToString(",") { "?" }
            connection.prepareStatement(
                "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                    "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                    "x.bitrate,x.track_number,x.disc_number,x.release_year,m.available FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                    "WHERE (m.available=1 OR ?) AND m.id IN ($placeholders)",
            ).use { statement ->
                statement.setInt(1, if (includeUnavailable) 1 else 0)
                orderedIds.distinct().forEachIndexed { index, id -> statement.setString(index + 2, id) }
                val byId = statement.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.toMedia().copy(isAvailable = rows.getInt(22) != 0)) } }
                orderedIds.mapNotNull(byId::get)
            }
        }
    }

    fun findMediaByUriOrFileName(value: String): MediaItem? = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                "x.bitrate,x.track_number,x.disc_number,x.release_year,m.available FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                "WHERE m.available=1 AND (m.uri=? OR m.file_name=? OR m.uri LIKE ?) LIMIT 1",
        ).use { statement ->
            statement.setString(1, value)
            statement.setString(2, value)
            statement.setString(3, "%$value%")
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toMedia().copy(isAvailable = rows.getInt(22) != 0) else null
            }
        }
    }

    @Synchronized
    fun albums(query: String = ""): List<AlbumItem> {
        val trimmed = query.trim()
        val currentGen = mutableGeneration.value
        val all = if (cachedAlbumGeneration == currentGen) {
            cachedAlbums
        } else {
            val loaded = database.transaction { connection ->
                connection.prepareStatement(
                    "SELECT COALESCE(NULLIF(TRIM(x.album), ''), 'Unknown Album') AS album_title, " +
                        "COALESCE(NULLIF(TRIM(x.album_artist), ''), NULLIF(TRIM(x.artist), ''), 'Unknown Artist') AS album_artist, " +
                        "COUNT(m.id) AS track_count, " +
                        "COALESCE(SUM(COALESCE(x.duration_ms, 0)), 0) AS total_duration, " +
                        "MAX(x.artwork_uri) AS artwork_uri, " +
                        "MAX(x.release_year) AS release_year " +
                        "FROM media_items m " +
                        "LEFT JOIN media_metadata x ON x.media_id=m.id " +
                        "WHERE m.available=1 AND m.kind='AUDIO' " +
                        "GROUP BY 1, 2 " +
                        "ORDER BY album_title COLLATE NOCASE",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                val title = rows.getString(1)
                                val artist = rows.getString(2)
                                val id = UUID.nameUUIDFromBytes("$title::$artist".toByteArray(Charsets.UTF_8)).toString()
                                add(
                                    AlbumItem(
                                        id = id,
                                        title = title,
                                        artist = artist.takeIf { it != "Unknown Artist" },
                                        albumArtist = artist.takeIf { it != "Unknown Artist" },
                                        trackCount = rows.getInt(3),
                                        totalDurationMillis = rows.getLong(4),
                                        artworkUri = rows.getString(5),
                                        releaseYear = rows.getInt(6).takeIf { !rows.wasNull() },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            cachedAlbums = loaded
            cachedAlbumGeneration = currentGen
            loaded
        }
        return if (trimmed.isEmpty()) all else all.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                (it.albumArtist ?: it.artist).orEmpty().contains(trimmed, ignoreCase = true)
        }
    }

    fun albumDetails(albumId: String): AlbumDetails? {
        val album = albums().firstOrNull { it.id == albumId } ?: return null
        val tracks = database.transaction { connection ->
            connection.prepareStatement(
                "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                    "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                    "x.bitrate,x.track_number,x.disc_number,x.release_year FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                    "WHERE m.available=1 AND m.kind='AUDIO' AND COALESCE(NULLIF(TRIM(x.album), ''), 'Unknown Album')=? " +
                    "AND COALESCE(NULLIF(TRIM(x.album_artist), ''), NULLIF(TRIM(x.artist), ''), 'Unknown Artist')=? " +
                    "ORDER BY COALESCE(x.disc_number, 999999) ASC, COALESCE(x.track_number, 999999) ASC, COALESCE(NULLIF(x.title,''),m.title) COLLATE NOCASE",
            ).use { statement ->
                statement.setString(1, album.title)
                statement.setString(2, album.albumArtist ?: album.artist ?: "Unknown Artist")
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toMedia()) } }
            }
        }
        return AlbumDetails(album, tracks)
    }

    @Synchronized
    fun artists(query: String = ""): List<ArtistItem> {
        val trimmed = query.trim()
        val currentGen = mutableGeneration.value
        val all = if (cachedArtistGeneration == currentGen) {
            cachedArtists
        } else {
            val loaded = database.transaction { connection ->
                connection.prepareStatement(
                    "SELECT COALESCE(NULLIF(TRIM(x.artist), ''), NULLIF(TRIM(x.album_artist), ''), 'Unknown Artist') AS artist_name, " +
                        "COUNT(DISTINCT COALESCE(NULLIF(TRIM(x.album), ''), 'Unknown Album')) AS album_count, " +
                        "COUNT(m.id) AS track_count, " +
                        "MAX(x.artwork_uri) AS artwork_uri " +
                        "FROM media_items m " +
                        "LEFT JOIN media_metadata x ON x.media_id=m.id " +
                        "WHERE m.available=1 AND m.kind='AUDIO' " +
                        "GROUP BY 1 " +
                        "ORDER BY artist_name COLLATE NOCASE",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                val name = rows.getString(1)
                                val id = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)).toString()
                                add(
                                    ArtistItem(
                                        id = id,
                                        name = name,
                                        albumCount = rows.getInt(2),
                                        trackCount = rows.getInt(3),
                                        artworkUri = rows.getString(4),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            cachedArtists = loaded
            cachedArtistGeneration = currentGen
            loaded
        }
        return if (trimmed.isEmpty()) all else all.filter {
            it.name.contains(trimmed, ignoreCase = true)
        }
    }

    fun artistDetails(artistId: String): ArtistDetails? {
        val artist = artists().firstOrNull { it.id == artistId } ?: return null
        val artistAlbums = albums().filter {
            it.artist.equals(artist.name, ignoreCase = true) || it.albumArtist.equals(artist.name, ignoreCase = true)
        }
        val tracks = database.transaction { connection ->
            connection.prepareStatement(
                "SELECT m.id,m.uri,COALESCE(NULLIF(x.title,''),m.title),m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms," +
                    "x.duration_ms,x.artist,x.album,x.artwork_uri,x.album_artist,x.genre,x.video_width,x.video_height,x.frame_rate," +
                    "x.bitrate,x.track_number,x.disc_number,x.release_year FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id " +
                    "WHERE m.available=1 AND m.kind='AUDIO' AND (" +
                    "COALESCE(NULLIF(TRIM(x.artist), ''), '')=? OR COALESCE(NULLIF(TRIM(x.album_artist), ''), '')=? " +
                    ") ORDER BY COALESCE(NULLIF(x.title,''),m.title) COLLATE NOCASE",
            ).use { statement ->
                statement.setString(1, artist.name)
                statement.setString(2, artist.name)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toMedia()) } }
            }
        }
        return ArtistDetails(artist, artistAlbums, tracks)
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
