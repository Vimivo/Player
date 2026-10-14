@file:Suppress("MaximumLineLength", "MaxLineLength", "TooGenericExceptionCaught")

package dev.vimivo.desktop.library

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
        }
    }

    override fun close() = connection.close()

    companion object {
        private const val SCHEMA_VERSION = 1

        fun open(path: Path = defaultPath()): DesktopDatabase {
            Files.createDirectories(path.parent)
            Class.forName("org.sqlite.JDBC")
            return DesktopDatabase(DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}"))
        }

        fun defaultPath(): Path {
            val home = System.getProperty("user.home") ?: error("The macOS user home directory is unavailable")
            return Path.of(home, "Library", "Application Support", "Vimivo", "vimivo.sqlite")
        }
    }
}
