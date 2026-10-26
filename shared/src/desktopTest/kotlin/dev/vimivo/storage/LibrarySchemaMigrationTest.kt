package dev.vimivo.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The schema moves forward without losing a row, from the database the first iOS releases created. */
class LibrarySchemaMigrationTest {
    /** What the Swift code created before schemas were versioned: no `user_version`, no optional media columns. */
    private fun legacyDatabase(): JdbcSqlDatabase = JdbcSqlDatabase().also { db ->
        listOf(
            "CREATE TABLE media_items (id TEXT PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, " +
                "duration_millis INTEGER, artist TEXT, album TEXT, artwork_uri TEXT, album_artist TEXT, bookmark_data BLOB)",
            "CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at_millis INTEGER NOT NULL)",
            "CREATE TABLE playlist_items (playlist_id TEXT NOT NULL, media_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "PRIMARY KEY (playlist_id, position), FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)",
            "CREATE TABLE favorites (media_id TEXT PRIMARY KEY)",
            "CREATE TABLE playback_history (id TEXT PRIMARY KEY, media_id TEXT NOT NULL, last_played_at_millis INTEGER NOT NULL, " +
                "position_millis INTEGER NOT NULL, completed INTEGER NOT NULL, play_count INTEGER NOT NULL)",
            "CREATE TABLE bookmarks (id TEXT PRIMARY KEY, media_id TEXT NOT NULL, name TEXT NOT NULL, " +
                "position_millis INTEGER NOT NULL, created_at_millis INTEGER NOT NULL)",
            "CREATE TABLE queue_items (position INTEGER PRIMARY KEY, media_id TEXT NOT NULL)",
            "CREATE TABLE queue_state (id INTEGER PRIMARY KEY CHECK (id = 1), current_index INTEGER NOT NULL)",
            "INSERT INTO media_items (id, uri, title, kind, duration_millis, artist, bookmark_data) " +
                "VALUES ('old', 'file:///old.mp3', 'Old Song', 'audio', 90000, 'Someone', x'0A0B')",
            "INSERT INTO playlists VALUES ('p', 'My list', 42)",
            "INSERT INTO playlist_items VALUES ('p', 'old', 0)",
            "INSERT INTO favorites VALUES ('old')",
            "INSERT INTO playback_history VALUES ('old', 'old', 777, 30000, 0, 3)",
            "INSERT INTO bookmarks VALUES ('b', 'old', 'Mark', 5000, 9)",
            "INSERT INTO queue_items VALUES (0, 'old')",
            "INSERT INTO queue_state VALUES (1, 0)",
        ).forEach { db.execute(it) }
    }

    @Test
    fun aDatabaseFromBeforeVersioningKeepsEveryRowAndGainsTheNewColumns() {
        val db = legacyDatabase()
        assertEquals(0, db.userVersion)
        val store = LocalLibraryStore(db, clock = { 1L })
        assertEquals(LibrarySchema.CURRENT_VERSION, db.userVersion)

        val song = store.allMedia().single()
        assertEquals("Old Song", song.title)
        assertEquals("Someone", song.artist)
        assertContentEquals(byteArrayOf(0x0A, 0x0B), song.bookmarkData)
        assertEquals(null, song.width)
        assertEquals(StoredPlaylist("p", "My list", listOf("old"), 42), store.playlists().single())
        assertEquals(setOf("old"), store.favorites())
        assertEquals(StoredResume(30_000, false), store.resumeState("old"))
        assertEquals(3, store.history().single().playCount)
        assertEquals("Mark", store.bookmarks("old").single().name)
        assertEquals(StoredQueue(listOf("old"), 0), store.loadQueue())

        // And the new columns work.
        store.updateVideoMetadata("old", 640, 360, 10)
        assertEquals(640, store.allMedia().single().width)
    }

    @Test
    fun aFreshDatabaseIsCreatedAtTheCurrentVersion() {
        val db = JdbcSqlDatabase()
        LocalLibraryStore(db, clock = { 1L })
        assertEquals(LibrarySchema.CURRENT_VERSION, db.userVersion)
        val columns = db.query("PRAGMA table_info(media_items)") { it.string(1) }
        assertTrue(listOf("width", "height", "size_bytes", "date_added_millis").all { it in columns })
    }

    @Test
    fun migratingTwiceChangesNothing() {
        val db = legacyDatabase()
        LibrarySchema.migrate(db)
        val before = db.query("SELECT sql FROM sqlite_master ORDER BY name") { it.string(0) }
        LibrarySchema.migrate(db)
        assertEquals(before, db.query("SELECT sql FROM sqlite_master ORDER BY name") { it.string(0) })
        assertEquals(1, db.query("SELECT COUNT(*) FROM media_items") { it.long(0) }.single()?.toInt())
    }

    @Test
    fun aDatabaseFromANewerVersionOfTheAppIsRefusedNotModified() {
        val db = JdbcSqlDatabase()
        LibrarySchema.migrate(db)
        db.userVersion = LibrarySchema.CURRENT_VERSION + 1
        assertFailsWith<IllegalArgumentException> { LibrarySchema.migrate(db) }
        assertEquals(LibrarySchema.CURRENT_VERSION + 1, db.userVersion)
    }

    @Test
    fun aDatabaseThatAlreadyHasSomeOptionalColumnsOnlyGetsTheMissingOnes() {
        val db = legacyDatabase()
        db.execute("ALTER TABLE media_items ADD COLUMN width INTEGER")
        db.execute("UPDATE media_items SET width = 99")
        LibrarySchema.migrate(db)
        assertEquals(99L, db.query("SELECT width FROM media_items") { it.long(0) }.single())
        assertEquals(LibrarySchema.CURRENT_VERSION, db.userVersion)
    }
}
