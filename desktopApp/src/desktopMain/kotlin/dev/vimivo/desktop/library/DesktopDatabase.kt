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
            if (version < 4) migrateToVersion4(statement)
            if (version < 5) migrateToVersion5(statement)
            if (version < 6) migrateToVersion6(statement)
            ensurePerformanceIndexes(statement)
        }
    }

    private fun ensurePerformanceIndexes(statement: java.sql.Statement) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS favorites (target_type TEXT NOT NULL, target_id TEXT NOT NULL, created_at_ms INTEGER NOT NULL, PRIMARY KEY(target_type, target_id))",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playback_positions (media_id TEXT PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER, completed INTEGER NOT NULL DEFAULT 0, updated_at_ms INTEGER NOT NULL, FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE RESTRICT)",
        )
        statement.execute("CREATE INDEX IF NOT EXISTS favorites_target_created ON favorites(target_type, created_at_ms DESC)")
        statement.execute("CREATE INDEX IF NOT EXISTS playback_positions_updated ON playback_positions(updated_at_ms DESC)")
        statement.execute("CREATE INDEX IF NOT EXISTS media_items_available_kind_title ON media_items(available, kind, title COLLATE NOCASE)")
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

    private fun migrateToVersion4(statement: java.sql.Statement) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playback_queue_state (singleton INTEGER PRIMARY KEY CHECK(singleton=1), " +
                "current_queue_id INTEGER, shuffle_enabled INTEGER NOT NULL DEFAULT 0, repeat_mode TEXT NOT NULL, " +
                "source_label TEXT, updated_at_ms INTEGER NOT NULL)",
        )
        // Temporary queue entries must never block library-root removal, so they cascade with indexed media.
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playback_queue_items (queue_id INTEGER PRIMARY KEY, media_id TEXT NOT NULL, " +
                "source_position INTEGER NOT NULL, playback_position INTEGER NOT NULL, " +
                "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
        )
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS playback_queue_source_order ON playback_queue_items(source_position)")
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS playback_queue_playback_order ON playback_queue_items(playback_position)")
        statement.execute("UPDATE schema_version SET version=4")
    }

    private fun migrateToVersion5(statement: java.sql.Statement) {
        statement.execute("CREATE INDEX IF NOT EXISTS media_items_kind_available ON media_items(kind, available)")
        statement.execute("CREATE INDEX IF NOT EXISTS media_items_file_name ON media_items(file_name COLLATE NOCASE)")
        statement.execute("CREATE INDEX IF NOT EXISTS media_metadata_album_artist ON media_metadata(album_artist COLLATE NOCASE)")
        statement.execute("CREATE INDEX IF NOT EXISTS media_metadata_lookup ON media_metadata(album COLLATE NOCASE, artist COLLATE NOCASE)")
        statement.execute(
            "CREATE TABLE IF NOT EXISTS playlist_items (playlist_id TEXT NOT NULL, media_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "added_at_ms INTEGER NOT NULL, PRIMARY KEY(playlist_id, media_id))",
        )
        statement.execute("CREATE INDEX IF NOT EXISTS playlist_items_media_id ON playlist_items(media_id)")
        statement.execute("UPDATE schema_version SET version=5")
    }

    private fun migrateToVersion6(statement: java.sql.Statement) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS user_metadata_overrides (" +
                "media_id TEXT PRIMARY KEY, title TEXT, artist TEXT, album TEXT, album_artist TEXT, genre TEXT, " +
                "release_year INTEGER, track_number INTEGER, disc_number INTEGER, updated_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS user_artwork_overrides (" +
                "media_id TEXT PRIMARY KEY, artwork_uri TEXT NOT NULL, updated_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS smart_playlists (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL, rules_json TEXT NOT NULL, sort_field TEXT NOT NULL DEFAULT 'TITLE', " +
                "sort_descending INTEGER NOT NULL DEFAULT 0, item_limit INTEGER, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS media_hashes (" +
                "media_id TEXT PRIMARY KEY, content_hash TEXT NOT NULL, file_size_bytes INTEGER NOT NULL, modified_ms INTEGER NOT NULL, " +
                "updated_at_ms INTEGER NOT NULL, FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
        )
        statement.execute("CREATE INDEX IF NOT EXISTS media_hashes_lookup ON media_hashes(content_hash, file_size_bytes)")
        statement.execute(
            "CREATE TABLE IF NOT EXISTS batch_operation_journal (" +
                "job_id TEXT PRIMARY KEY, operation_type TEXT NOT NULL, stage TEXT NOT NULL, affected_count INTEGER NOT NULL DEFAULT 0, " +
                "details TEXT NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL)",
        )
        statement.execute("CREATE INDEX IF NOT EXISTS batch_journal_created ON batch_operation_journal(created_at_ms DESC)")
        statement.execute(
            "CREATE TABLE IF NOT EXISTS workflow_rules (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL, trigger_type TEXT NOT NULL, action_type TEXT NOT NULL, " +
                "enabled INTEGER NOT NULL DEFAULT 1, last_run_ms INTEGER, last_status TEXT)",
        )
        statement.execute(
            "CREATE TABLE IF NOT EXISTS dismissed_recommendations (" +
                "target_type TEXT NOT NULL, target_id TEXT NOT NULL, dismissed_at_ms INTEGER NOT NULL, PRIMARY KEY(target_type, target_id))",
        )
        statement.execute("CREATE TABLE IF NOT EXISTS personalization_preferences (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        statement.execute("UPDATE schema_version SET version=6")
    }

    override fun close() = connection.close()

    companion object {
        const val SCHEMA_VERSION = 6

        fun open(path: Path = defaultPath()): DesktopDatabase {
            Files.createDirectories(path.parent)
            Class.forName("org.sqlite.JDBC")
            return DesktopDatabase(DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}"))
        }

        fun defaultPath(): Path = DesktopDirectories.resolve().database
    }
}
