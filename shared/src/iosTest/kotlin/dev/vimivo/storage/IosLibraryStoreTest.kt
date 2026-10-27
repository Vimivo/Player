@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.vimivo.storage

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The iOS driver over the system SQLite: the same behaviour the JVM tests check, plus files, blobs and migration. */
class IosLibraryStoreTest {
    private fun tempPath(): String = NSTemporaryDirectory() + "vimivo-test-" + NSUUID().UUIDString + ".db"

    private fun song(id: String, title: String = "Song $id") =
        StoredMedia(id, "file:///$id.mp3", title, "audio", durationMillis = 120_000, artist = "Artist")

    @Test
    fun mediaPlaylistsQueueAndHistoryWorkOverTheSystemSqlite() {
        val store = IosLibrary.openInMemory()
        store.saveMedia(song("b", "Beta"))
        store.saveMedia(song("a", "Alpha").copy(bookmarkData = byteArrayOf(1, 2, 3), width = 640, sizeBytes = 99))
        val all = store.allMedia()
        assertEquals(listOf("Alpha", "Beta"), all.map { it.title })
        assertContentEquals(byteArrayOf(1, 2, 3), all[0].bookmarkData)
        assertEquals(640, all[0].width)
        assertTrue(all[0].dateAddedMillis!! > 1_600_000_000_000L, "stamped with the real clock")

        store.savePlaylist(StoredPlaylist("p", "Chill", listOf("m1", "m2", "m3"), 7))
        assertEquals(listOf("m1", "m2", "m3"), store.playlists().single().mediaIds)
        store.deletePlaylist("p")
        assertTrue(store.playlists().isEmpty())

        store.saveQueue(listOf("a", "b", "c"), 1)
        assertEquals(StoredQueue(listOf("a", "b", "c"), 1), store.loadQueue())
        store.saveQueue(listOf("a"), 5)
        assertEquals(0, store.loadQueue().currentIndex)

        store.recordPlayStart("m")
        store.savePosition("m", 120_000, completed = false)
        store.recordPlayStart("m")
        assertEquals(StoredResume(120_000, false), store.resumeState("m"))
        assertEquals(2, store.history().single().playCount)

        store.setFavorite("x", true)
        assertEquals(setOf("x"), store.favorites())
        store.addBookmark(StoredBookmark("late", "m", "Late", 90_000, 1))
        store.addBookmark(StoredBookmark("early", "m", "Early", 10_000, 2))
        assertEquals(listOf("Early", "Late"), store.bookmarks("m").map { it.name })
    }

    @Test
    fun aFileDatabaseKeepsItsDataAcrossReopening() {
        val path = tempPath()
        try {
            IosLibrary.open(path).use { it.saveMedia(song("kept")) }
            IosLibrary.open(path).use { assertEquals("kept", it.allMedia().single().id) }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }

    @Test
    fun aDatabaseFromBeforeVersioningMigratesWithoutLosingRows() {
        val path = tempPath()
        try {
            IosSqliteDatabase(path).use { db ->
                db.execute(
                    "CREATE TABLE media_items (id TEXT PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, " +
                        "duration_millis INTEGER, artist TEXT, album TEXT, artwork_uri TEXT, album_artist TEXT, bookmark_data BLOB)",
                )
                db.execute(
                    "INSERT INTO media_items (id, uri, title, kind, bookmark_data) VALUES ('old', 'file:///old.mp3', 'Old', 'audio', ?)",
                    listOf(byteArrayOf(9, 8)),
                )
                assertEquals(0, db.userVersion)
            }
            IosLibrary.open(path).use { store ->
                val song = store.allMedia().single()
                assertEquals("Old", song.title)
                assertContentEquals(byteArrayOf(9, 8), song.bookmarkData)
                store.updateVideoMetadata("old", 320, 240, 5)
                assertEquals(320, store.allMedia().single().width)
            }
            IosSqliteDatabase(path).use { assertEquals(LibrarySchema.CURRENT_VERSION, it.userVersion) }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }

    @Test
    fun aFailingTransactionIsRolledBackAndSqliteErrorsAreThrown() {
        val db = IosSqliteDatabase(IosSqliteDatabase.IN_MEMORY)
        LibrarySchema.migrate(db)
        db.execute("INSERT INTO favorites VALUES ('kept')")
        assertFailsWith<SqliteException> {
            db.transaction {
                db.execute("INSERT INTO favorites VALUES ('lost')")
                db.execute("INSERT INTO favorites VALUES ('kept')") // duplicate primary key
            }
        }
        assertEquals(listOf("kept"), db.query("SELECT media_id FROM favorites") { it.string(0) })
        assertFailsWith<SqliteException> { db.execute("SELECT * FROM no_such_table") }
    }
}
