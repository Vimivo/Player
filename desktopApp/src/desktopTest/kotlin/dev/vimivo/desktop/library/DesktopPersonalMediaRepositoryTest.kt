@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopPersonalMediaRepositoryTest {
    @Test
    fun appliesConservativeResumeRules() {
        val video = MediaItem(MediaId("video"), "file:///video.mp4", "Video", MediaKind.VIDEO, durationMillis = 600_000)
        val song = MediaItem(MediaId("song"), "file:///song.flac", "Song", MediaKind.AUDIO, durationMillis = 240_000)
        val lecture = MediaItem(
            MediaId("lecture"), "file:///lecture.flac", "Lecture", MediaKind.AUDIO, durationMillis = 3_600_000,
        )

        assertEquals(120_000, DesktopLibraryController.resumePositionMillis(video, ResumePosition(video.id, 120_000, 600_000, false, 1)))
        assertEquals(0, DesktopLibraryController.resumePositionMillis(video, ResumePosition(video.id, 595_000, 600_000, false, 1)))
        assertEquals(0, DesktopLibraryController.resumePositionMillis(video, ResumePosition(video.id, 120_000, 600_000, true, 1)))
        assertEquals(0, DesktopLibraryController.resumePositionMillis(song, ResumePosition(song.id, 120_000, 240_000, false, 1)))
        assertEquals(120_000, DesktopLibraryController.resumePositionMillis(lecture, ResumePosition(lecture.id, 120_000, 3_600_000, false, 1)))
    }

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
                assertEquals(3, version)
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

    @Test
    fun completingPlaybackDoesNotDoubleCountTheSession() {
        val path = Files.createTempDirectory("vimivo-completion-test").resolve("vimivo.sqlite")
        val mediaId = MediaId("media-1")
        DesktopDatabase.open(path).use { database ->
            seedMedia(database, listOf(mediaId))
            val repository = DesktopPersonalMediaRepository(database)
            repository.recordPlayback(mediaId, positionMillis = 1_000, completed = false, now = 10)
            repository.markPlaybackCompleted(mediaId, positionMillis = 9_900, now = 20)

            val history = repository.history().single()
            assertEquals(1, history.playCount)
            assertEquals(9_900, history.positionMillis)
            assertTrue(history.completed)
        }
    }

    @Test
    fun duplicatesPlaylistsAndSupportsHistoryAndBookmarkCleanup() {
        val path = Files.createTempDirectory("vimivo-personal-actions-test").resolve("vimivo.sqlite")
        val mediaId = MediaId("media-1")
        DesktopDatabase.open(path).use { database ->
            seedMedia(database, listOf(mediaId))
            val repository = DesktopPersonalMediaRepository(database)
            val playlist = repository.createPlaylist("Original", now = 1)
            repository.addToPlaylist(playlist.id, mediaId, now = 2)
            val duplicate = repository.duplicatePlaylist(playlist.id, now = 3)
            assertEquals("Original copy", duplicate.name)
            assertEquals(listOf(mediaId), duplicate.items)

            repository.recordPlayback(mediaId, positionMillis = 4_000, completed = false, now = 4)
            repository.removeHistory(mediaId)
            assertTrue(repository.history().isEmpty())

            val bookmark = repository.addBookmark(mediaId, 5_000, "Old", now = 5)
            repository.renameBookmark(bookmark.id, "New")
            assertEquals("New", repository.allBookmarks().single().name)
            repository.deleteBookmark(bookmark.id)
            assertTrue(repository.allBookmarks().isEmpty())
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
