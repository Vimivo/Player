@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.library

import dev.vimivo.desktop.platform.DesktopDirectories
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** Desktop-only SQLite owner. It never deletes or recreates a database on initialization failure. */
internal class DesktopDatabase private constructor(private val connection: Connection) : AutoCloseable {
    init {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA busy_timeout = 5000")
        }
        migrate()
    }

    @Synchronized
    fun <T> transaction(block: (Connection) -> T): T {
        val wasAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block(connection).also { connection.commit() }
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = wasAutoCommit
        }
    }

    private fun migrate() = transaction { database ->
        database.createStatement().use { statement ->
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)")
            val version = statement.executeQuery("SELECT version FROM schema_version LIMIT 1").use { result ->
                if (result.next()) result.getInt(1) else 0
            }
            if (version == 0) {
                statement.execute("INSERT INTO schema_version(version) VALUES (1)")
                statement.execute(
                    "CREATE TABLE library_roots (id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, " +
                        "added_at_ms INTEGER NOT NULL, last_scan_ms INTEGER, available INTEGER NOT NULL DEFAULT 1)",
                )
                statement.execute(
                    "CREATE TABLE media_items (id TEXT PRIMARY KEY, root_id TEXT NOT NULL, uri TEXT NOT NULL UNIQUE, " +
                        "file_name TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, mime_type TEXT, size_bytes INTEGER, " +
                        "modified_ms INTEGER, indexed_ms INTEGER NOT NULL, available INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(root_id) REFERENCES library_roots(id))",
                )
                statement.execute("CREATE INDEX media_items_root_available ON media_items(root_id, available)")
                statement.execute("CREATE INDEX media_items_kind_title ON media_items(kind, title COLLATE NOCASE)")
                statement.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            }
            require(version <= SCHEMA_VERSION) { "Desktop database schema $version is newer than this Vimivo build" }
            if (version < 2) migrateToVersion2(statement)
            if (version < 3) migrateToVersion3(statement)
        }
    }

    private fun migrateToVersion2(statement: java.sql.Statement) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS media_metadata (media_id TEXT PRIMARY KEY, artist TEXT, album TEXT, " +
                "album_artist TEXT, genre TEXT, track_number INTEGER, disc_number INTEGER, release_year INTEGER, " +
                "duration_ms INTEGER, bitrate INTEGER, sample_rate INTEGER, channels INTEGER, video_width INTEGER, " +
                "video_height INTEGER, frame_rate REAL, video_codec TEXT, audio_codec TEXT, container TEXT, " +
                "audio_track_count INTEGER, subtitle_track_count INTEGER, artwork_uri TEXT, thumbnail_uri TEXT, " +
                "extraction_version INTEGER NOT NULL DEFAULT 1, extraction_status TEXT NOT NULL DEFAULT 'PENDING', " +
                "extraction_error TEXT, extracted_size_bytes INTEGER, extracted_modified_ms INTEGER, updated_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at_ms INTEGER NOT NULL, " +
                "updated_at_ms INTEGER NOT NULL)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playlist_items (playlist_id TEXT NOT NULL, media_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "added_at_ms INTEGER NOT NULL, PRIMARY KEY(playlist_id, media_id), " +
                "FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE RESTRICT)",
        )
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS playlist_items_order ON playlist_items(playlist_id, position)")
        statement.execute(
            "CREATE TABLE IF NOT EXISTS favorites (target_type TEXT NOT NULL, target_id TEXT NOT NULL, created_at_ms INTEGER NOT NULL, " +
                "PRIMARY KEY(target_type, target_id))",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playback_history (media_id TEXT PRIMARY KEY, last_played_ms INTEGER NOT NULL, " +
                "play_count INTEGER NOT NULL DEFAULT 0, recent_position_ms INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, " +
                "last_completed_ms INTEGER, FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE RESTRICT)",
        )
        statement.execute("CREATE INDEX IF NOT EXISTS playback_history_recent ON playback_history(last_played_ms DESC)")
        statement.execute("CREATE INDEX IF NOT EXISTS playback_history_popular ON playback_history(play_count DESC)")
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playback_positions (media_id TEXT PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER, " +
                "completed INTEGER NOT NULL DEFAULT 0, updated_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE RESTRICT)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS media_bookmarks (id TEXT PRIMARY KEY, media_id TEXT NOT NULL, position_ms INTEGER NOT NULL, " +
                "label TEXT, created_at_ms INTEGER NOT NULL, FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
        )
        statement.execute("CREATE INDEX IF NOT EXISTS media_bookmarks_media ON media_bookmarks(media_id, position_ms)")
        statement.execute("UPDATE schema_version SET version=2")
    }

    private fun migrateToVersion3(statement: java.sql.Statement) {
        statement.execute("ALTER TABLE media_metadata ADD COLUMN title TEXT")
        statement.execute("CREATE INDEX IF NOT EXISTS media_metadata_artist ON media_metadata(artist COLLATE NOCASE)")
        statement.execute("CREATE INDEX IF NOT EXISTS media_metadata_album ON media_metadata(album COLLATE NOCASE)")
        statement.execute("UPDATE schema_version SET version=3")
    }

    override fun close() = connection.close()

    companion object {
        private const val SCHEMA_VERSION = 3

        fun open(path: Path = defaultPath()): DesktopDatabase {
            Files.createDirectories(path.parent)
            Class.forName("org.sqlite.JDBC")
            return DesktopDatabase(DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}"))
        }

        fun defaultPath(): Path = DesktopDirectories.resolve().database
    }
}
