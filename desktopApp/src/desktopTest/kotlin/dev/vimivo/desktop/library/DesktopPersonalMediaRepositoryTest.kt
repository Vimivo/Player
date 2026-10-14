@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopPersonalMediaRepositoryTest {
    @Test
    fun migratesVersionOneWithoutLosingExistingRecords() {
        val path = Files.createTempDirectory("vimivo-migration-test").resolve("vimivo.sqlite")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys=ON")
                statement.execute("CREATE TABLE schema_version(version INTEGER NOT NULL)")
                statement.execute("INSERT INTO schema_version VALUES(1)")
                statement.execute("CREATE TABLE library_roots(id TEXT PRIMARY KEY,uri TEXT NOT NULL UNIQUE,display_name TEXT NOT NULL,added_at_ms INTEGER NOT NULL,last_scan_ms INTEGER,available INTEGER NOT NULL DEFAULT 1)")
                statement.execute("CREATE TABLE media_items(id TEXT PRIMARY KEY,root_id TEXT NOT NULL,uri TEXT NOT NULL UNIQUE,file_name TEXT NOT NULL,title TEXT NOT NULL,kind TEXT NOT NULL,mime_type TEXT,size_bytes INTEGER,modified_ms INTEGER,indexed_ms INTEGER NOT NULL,available INTEGER NOT NULL DEFAULT 1,FOREIGN KEY(root_id) REFERENCES library_roots(id))")
                statement.execute("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                statement.execute("INSERT INTO settings VALUES('theme','dark')")
            }
        }

        DesktopDatabase.open(path).use { database ->
            database.transaction { connection ->
                val version = connection.createStatement().executeQuery("SELECT version FROM schema_version").use { rows -> rows.next(); rows.getInt(1) }
                val theme = connection.createStatement().executeQuery("SELECT value FROM settings WHERE key='theme'").use { rows -> rows.next(); rows.getString(1) }
                assertEquals(2, version)
                assertEquals("dark", theme)
                assertTrue(connection.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='playlists'").next())
            }
        }
    }

    @Test
    fun persistsPlaylistsFavoritesHistoryResumeBookmarksAndSettingsAcrossRestart() {
        val path = Files.createTempDirectory("vimivo-personal-data-test").resolve("vimivo.sqlite")
        val first = MediaId("media-1")
        val second = MediaId("media-2")
        var playlistId = ""
        DesktopDatabase.open(path).use { database ->
            seedMedia(database, listOf(first, second))
            val repository = DesktopPersonalMediaRepository(database)
            val playlist = repository.createPlaylist("Road Trip", now = 10)
            playlistId = playlist.id
            repository.addToPlaylist(playlist.id, first, now = 11)
            repository.addToPlaylist(playlist.id, second, now = 12)
            repository.movePlaylistItem(playlist.id, second, 0, now = 13)
            repository.setFavorite(FavoriteTarget.MEDIA, first.value, true, now = 14)
            repository.recordPlayback(first, positionMillis = 1_500, completed = false, now = 15)
            repository.savePosition(first, positionMillis = 1_500, durationMillis = 10_000, completed = false, now = 16)
            repository.addBookmark(first, positionMillis = 2_000, label = "Chorus", now = 17)
            repository.putSetting("theme", "amoled")
        }

        DesktopDatabase.open(path).use { database ->
            val repository = DesktopPersonalMediaRepository(database)
            val playlist = repository.playlists().single()
            assertEquals(playlistId, playlist.id)
            assertEquals(listOf(second, first), playlist.items)
            assertEquals(setOf(first.value), repository.favoriteIds(FavoriteTarget.MEDIA))
            assertEquals(1, repository.history().single().playCount)
            assertFalse(repository.history().single().completed)
            assertEquals(1_500, repository.position(first)?.positionMillis)
            assertNotNull(repository.bookmarks(first).single())
            assertEquals("Chorus", repository.bookmarks(first).single().name)
            assertEquals("amoled", repository.setting("theme"))
        }
    }

    private fun seedMedia(database: DesktopDatabase, ids: List<MediaId>) {
        database.transaction { connection ->
            connection.createStatement().execute("INSERT INTO library_roots(id,uri,display_name,added_at_ms) VALUES('root','file:///tmp/','Test',0)")
            connection.prepareStatement("INSERT INTO media_items(id,root_id,uri,file_name,title,kind,indexed_ms) VALUES(?,'root',?,?,?,'AUDIO',0)").use { statement ->
                ids.forEach { id ->
                    statement.setString(1, id.value); statement.setString(2, "file:///tmp/${id.value}.mp3")
                    statement.setString(3, "${id.value}.mp3"); statement.setString(4, id.value); statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }
}
