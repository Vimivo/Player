@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import java.nio.file.Files
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopDatabaseMigrationTest {

    @Test
    fun freshDatabaseInitializesDirectlyToLatestSchemaVersion() {
        val tempDir = Files.createTempDirectory("vimivo-fresh-db")
        val dbPath = tempDir.resolve("fresh.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            database.transaction { conn ->
                conn.createStatement().executeQuery("SELECT version FROM schema_version").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(4, rs.getInt(1), "Fresh database must initialize to schema version 4")
                }
            }
        }
    }

    @Test
    fun upgradeFromSchemaVersion1PreservesDataAndEnforcesForeignKeys() {
        val tempDir = Files.createTempDirectory("vimivo-v1-upgrade")
        val dbPath = tempDir.resolve("v1.sqlite")

        // Construct a raw Schema Version 1 database
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                stmt.execute("INSERT INTO schema_version(version) VALUES (1)")
                stmt.execute(
                    "CREATE TABLE library_roots (id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, " +
                        "added_at_ms INTEGER NOT NULL, last_scan_ms INTEGER, available INTEGER NOT NULL DEFAULT 1)",
                )
                stmt.execute(
                    "CREATE TABLE media_items (id TEXT PRIMARY KEY, root_id TEXT NOT NULL, uri TEXT NOT NULL UNIQUE, " +
                        "file_name TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, mime_type TEXT, size_bytes INTEGER, " +
                        "modified_ms INTEGER, indexed_ms INTEGER NOT NULL, available INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(root_id) REFERENCES library_roots(id))",
                )
                stmt.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

                // Seed user data in v1
                stmt.execute("INSERT INTO library_roots VALUES ('root-1', 'file:///music', 'My Music', 1000, 1000, 1)")
                stmt.execute("INSERT INTO media_items VALUES ('song-1', 'root-1', 'file:///music/s1.mp3', 's1.mp3', 'Song 1', 'AUDIO', 'audio/mpeg', 1024, 1000, 1000, 1)")
                stmt.execute("INSERT INTO settings VALUES ('theme', 'dark')")
                stmt.execute("INSERT INTO settings VALUES ('volume', '80')")
            }
        }

        // Open with DesktopDatabase, triggering migration 1 -> 2 -> 3 -> 4
        DesktopDatabase.open(dbPath).use { database ->
            database.transaction { conn ->
                // Check schema version
                conn.createStatement().executeQuery("SELECT version FROM schema_version").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(4, rs.getInt(1))
                }

                // Verify original data is intact
                conn.createStatement().executeQuery("SELECT display_name FROM library_roots WHERE id = 'root-1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("My Music", rs.getString(1))
                }
                conn.createStatement().executeQuery("SELECT title FROM media_items WHERE id = 'song-1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Song 1", rs.getString(1))
                }
                conn.createStatement().executeQuery("SELECT value FROM settings WHERE key = 'theme'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("dark", rs.getString(1))
                }

                // Verify v2, v3, v4 tables exist and are functional
                conn.createStatement().execute("INSERT INTO playlists VALUES ('pl-1', 'Favorites Mix', 2000, 2000)")
                conn.createStatement().execute("INSERT INTO playlist_items VALUES ('pl-1', 'song-1', 0, 2000)")
                conn.createStatement().execute("INSERT INTO favorites VALUES ('MEDIA', 'song-1', 2000)")
                conn.createStatement().execute("INSERT INTO playback_history VALUES ('song-1', 2500, 1, 12000, 0, NULL)")
                conn.createStatement().execute("INSERT INTO playback_positions VALUES ('song-1', 12000, 240000, 0, 2500)")
                conn.createStatement().execute("INSERT INTO media_bookmarks VALUES ('bm-1', 'song-1', 5000, 'Intro Solo', 2500)")

                // Verify foreign key integrity: invalid media_id must fail
                assertFailsWith<SQLException> {
                    conn.createStatement().execute("INSERT INTO playlist_items VALUES ('pl-1', 'nonexistent-song', 1, 2100)")
                }
            }
        }
    }

    @Test
    fun upgradeFromSchemaVersion2PreservesMetadataAndPlaylists() {
        val tempDir = Files.createTempDirectory("vimivo-v2-upgrade")
        val dbPath = tempDir.resolve("v2.sqlite")

        // Build a Schema Version 2 database
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                stmt.execute("INSERT INTO schema_version(version) VALUES (2)")
                stmt.execute(
                    "CREATE TABLE library_roots (id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, " +
                        "added_at_ms INTEGER NOT NULL, last_scan_ms INTEGER, available INTEGER NOT NULL DEFAULT 1)",
                )
                stmt.execute(
                    "CREATE TABLE media_items (id TEXT PRIMARY KEY, root_id TEXT NOT NULL, uri TEXT NOT NULL UNIQUE, " +
                        "file_name TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, mime_type TEXT, size_bytes INTEGER, " +
                        "modified_ms INTEGER, indexed_ms INTEGER NOT NULL, available INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(root_id) REFERENCES library_roots(id))",
                )
                stmt.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                stmt.execute(
                    "CREATE TABLE media_metadata (media_id TEXT PRIMARY KEY, artist TEXT, album TEXT, " +
                        "album_artist TEXT, genre TEXT, track_number INTEGER, disc_number INTEGER, release_year INTEGER, " +
                        "duration_ms INTEGER, bitrate INTEGER, sample_rate INTEGER, channels INTEGER, video_width INTEGER, " +
                        "video_height INTEGER, frame_rate REAL, video_codec TEXT, audio_codec TEXT, container TEXT, " +
                        "audio_track_count INTEGER, subtitle_track_count INTEGER, artwork_uri TEXT, thumbnail_uri TEXT, " +
                        "extraction_version INTEGER NOT NULL DEFAULT 1, extraction_status TEXT NOT NULL DEFAULT 'PENDING', " +
                        "extraction_error TEXT, extracted_size_bytes INTEGER, extracted_modified_ms INTEGER, updated_at_ms INTEGER NOT NULL, " +
                        "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
                )
                stmt.execute("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL)")
                stmt.execute(
                    "CREATE TABLE playlist_items (playlist_id TEXT NOT NULL, media_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                        "added_at_ms INTEGER NOT NULL, PRIMARY KEY(playlist_id, media_id))",
                )

                stmt.execute("INSERT INTO library_roots VALUES ('root-2', 'file:///music2', 'Music 2', 1000, 1000, 1)")
                stmt.execute("INSERT INTO media_items VALUES ('song-2', 'root-2', 'file:///music2/s2.mp3', 's2.mp3', 'Original Title', 'AUDIO', 'audio/mpeg', 2048, 1000, 1000, 1)")
                stmt.execute("INSERT INTO media_metadata(media_id, artist, album, updated_at_ms) VALUES ('song-2', 'Artist Two', 'Album Two', 1500)")
                stmt.execute("INSERT INTO playlists VALUES ('pl-2', 'Road Trip', 1500, 1500)")
                stmt.execute("INSERT INTO playlist_items VALUES ('pl-2', 'song-2', 0, 1500)")
            }
        }

        // Open with DesktopDatabase, migrating 2 -> 3 -> 4
        DesktopDatabase.open(dbPath).use { database ->
            database.transaction { conn ->
                // Schema version should now be 4
                conn.createStatement().executeQuery("SELECT version FROM schema_version").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(4, rs.getInt(1))
                }

                // Verify metadata title column added by v3 migration exists
                conn.createStatement().execute("UPDATE media_metadata SET title = 'New Tagged Title' WHERE media_id = 'song-2'")
                conn.createStatement().executeQuery("SELECT artist, album, title FROM media_metadata WHERE media_id = 'song-2'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Artist Two", rs.getString("artist"))
                    assertEquals("Album Two", rs.getString("album"))
                    assertEquals("New Tagged Title", rs.getString("title"))
                }

                // Verify playback queue v4 tables exist
                conn.createStatement().execute(
                    "INSERT INTO playback_queue_state VALUES (1, NULL, 0, 'ALL', 'Test Queue', 2000)",
                )
                conn.createStatement().execute(
                    "INSERT INTO playback_queue_items VALUES (1, 'song-2', 0, 0)",
                )
            }
        }
    }

    @Test
    fun upgradeFromSchemaVersion3PreservesQueueAndIndices() {
        val tempDir = Files.createTempDirectory("vimivo-v3-upgrade")
        val dbPath = tempDir.resolve("v3.sqlite")

        // Build Schema Version 3 database
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                stmt.execute("INSERT INTO schema_version(version) VALUES (3)")
                stmt.execute(
                    "CREATE TABLE library_roots (id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, " +
                        "added_at_ms INTEGER NOT NULL, last_scan_ms INTEGER, available INTEGER NOT NULL DEFAULT 1)",
                )
                stmt.execute(
                    "CREATE TABLE media_items (id TEXT PRIMARY KEY, root_id TEXT NOT NULL, uri TEXT NOT NULL UNIQUE, " +
                        "file_name TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, mime_type TEXT, size_bytes INTEGER, " +
                        "modified_ms INTEGER, indexed_ms INTEGER NOT NULL, available INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(root_id) REFERENCES library_roots(id))",
                )
                stmt.execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                stmt.execute(
                    "CREATE TABLE media_metadata (media_id TEXT PRIMARY KEY, artist TEXT, album TEXT, title TEXT, " +
                        "album_artist TEXT, genre TEXT, track_number INTEGER, disc_number INTEGER, release_year INTEGER, " +
                        "duration_ms INTEGER, bitrate INTEGER, sample_rate INTEGER, channels INTEGER, video_width INTEGER, " +
                        "video_height INTEGER, frame_rate REAL, video_codec TEXT, audio_codec TEXT, container TEXT, " +
                        "audio_track_count INTEGER, subtitle_track_count INTEGER, artwork_uri TEXT, thumbnail_uri TEXT, " +
                        "extraction_version INTEGER NOT NULL DEFAULT 1, extraction_status TEXT NOT NULL DEFAULT 'PENDING', " +
                        "extraction_error TEXT, extracted_size_bytes INTEGER, extracted_modified_ms INTEGER, updated_at_ms INTEGER NOT NULL, " +
                        "FOREIGN KEY(media_id) REFERENCES media_items(id) ON DELETE CASCADE)",
                )
                stmt.execute("INSERT INTO library_roots VALUES ('root-3', 'file:///music3', 'Music 3', 1000, 1000, 1)")
                stmt.execute("INSERT INTO media_items VALUES ('song-3', 'root-3', 'file:///music3/s3.mp3', 's3.mp3', 'Song 3', 'AUDIO', 'audio/mpeg', 1024, 1000, 1000, 1)")
            }
        }

        // Open with DesktopDatabase, migrating 3 -> 4
        DesktopDatabase.open(dbPath).use { database ->
            database.transaction { conn ->
                conn.createStatement().executeQuery("SELECT version FROM schema_version").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(4, rs.getInt(1))
                }

                // Verify queue tables exist and cascade delete when media is removed
                conn.createStatement().execute("INSERT INTO playback_queue_state VALUES (1, 1, 1, 'ONE', 'Active Queue', 3000)")
                conn.createStatement().execute("INSERT INTO playback_queue_items VALUES (1, 'song-3', 0, 0)")

                // Deleting media_item should cascade delete from queue_items
                conn.createStatement().execute("DELETE FROM media_items WHERE id = 'song-3'")
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM playback_queue_items").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(0, rs.getInt(1), "Queue items must cascade delete when media item is deleted")
                }
            }
        }
    }

    @Test
    fun databaseWithNewerVersionFailsFastToProtectUserData() {
        val tempDir = Files.createTempDirectory("vimivo-future-db")
        val dbPath = tempDir.resolve("future.sqlite")

        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)")
                stmt.execute("INSERT INTO schema_version(version) VALUES (5)")
            }
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            DesktopDatabase.open(dbPath)
        }
        assertTrue(exception.message!!.contains("is newer than this Vimivo build"), "Should refuse to open newer database")
    }

    @Test
    fun transactionRollbackRevertsChangesOnFailure() {
        val tempDir = Files.createTempDirectory("vimivo-tx-rollback")
        val dbPath = tempDir.resolve("rollback.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            database.transaction { conn ->
                conn.createStatement().execute("INSERT INTO settings VALUES ('key1', 'value1')")
            }

            assertFailsWith<IllegalStateException> {
                database.transaction { conn ->
                    conn.createStatement().execute("INSERT INTO settings VALUES ('key2', 'value2')")
                    error("Simulated transaction fault")
                }
            }

            database.transaction { conn ->
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM settings WHERE key = 'key2'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(0, rs.getInt(1), "Key2 must have been rolled back")
                }
                conn.createStatement().executeQuery("SELECT value FROM settings WHERE key = 'key1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("value1", rs.getString(1), "Key1 must be retained")
                }
            }
        }
    }
}
