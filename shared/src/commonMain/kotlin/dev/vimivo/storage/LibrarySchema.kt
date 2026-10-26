package dev.vimivo.storage

/**
 * The versioned schema of the local library database used by the iOS app (the file is kept under the app's
 * Application Support folder). Migrations only ever move forward and never drop or rewrite data:
 *
 * 1. the baseline: the tables the first iOS releases created with `CREATE TABLE IF NOT EXISTS` and no version number,
 *    so a database from before versioning is recognised and kept as it is;
 * 2. the optional columns of `media_items` (video size, file size, date added) added where they are missing;
 * 3. the `lyrics` table, which keeps the lyrics file a person loaded for a track.
 *
 * `PRAGMA user_version` records how far a database has come, so a step runs once.
 */
object LibrarySchema {
    /** The version a fully migrated database reports. */
    const val CURRENT_VERSION = 3

    private val BASELINE = listOf(
        """CREATE TABLE IF NOT EXISTS media_items (
            id TEXT PRIMARY KEY,
            uri TEXT NOT NULL,
            title TEXT NOT NULL,
            kind TEXT NOT NULL,
            duration_millis INTEGER,
            artist TEXT,
            album TEXT,
            artwork_uri TEXT,
            album_artist TEXT,
            bookmark_data BLOB
        )""",
        """CREATE TABLE IF NOT EXISTS playlists (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            created_at_millis INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS playlist_items (
            playlist_id TEXT NOT NULL,
            media_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            PRIMARY KEY (playlist_id, position),
            FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
        )""",
        "CREATE TABLE IF NOT EXISTS favorites (media_id TEXT PRIMARY KEY)",
        """CREATE TABLE IF NOT EXISTS playback_history (
            id TEXT PRIMARY KEY,
            media_id TEXT NOT NULL,
            last_played_at_millis INTEGER NOT NULL,
            position_millis INTEGER NOT NULL,
            completed INTEGER NOT NULL,
            play_count INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS bookmarks (
            id TEXT PRIMARY KEY,
            media_id TEXT NOT NULL,
            name TEXT NOT NULL,
            position_millis INTEGER NOT NULL,
            created_at_millis INTEGER NOT NULL
        )""",
        "CREATE TABLE IF NOT EXISTS queue_items (position INTEGER PRIMARY KEY, media_id TEXT NOT NULL)",
        """CREATE TABLE IF NOT EXISTS queue_state (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            current_index INTEGER NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS bookmarks_media ON bookmarks(media_id, position_millis)",
    )

    private val OPTIONAL_MEDIA_COLUMNS = listOf(
        "width" to "INTEGER",
        "height" to "INTEGER",
        "size_bytes" to "INTEGER",
        "date_added_millis" to "INTEGER",
    )

    /** Brings [database] up to [CURRENT_VERSION]; each step and its version number are one transaction. */
    fun migrate(database: SqlDatabase) {
        val from = database.userVersion
        require(from <= CURRENT_VERSION) {
            "This database is from a newer version of Vimivo (schema $from, this build understands $CURRENT_VERSION)"
        }
        if (from < 1) {
            database.transaction {
                BASELINE.forEach { database.execute(it) }
                database.userVersion = 1
            }
        }
        if (from < 2) {
            database.transaction {
                val existing = database.query("PRAGMA table_info(media_items)") { it.string(1) }.toSet()
                OPTIONAL_MEDIA_COLUMNS.filter { (name, _) -> name !in existing }.forEach { (name, type) ->
                    database.execute("ALTER TABLE media_items ADD COLUMN $name $type")
                }
                database.userVersion = 2
            }
        }
        if (from < 3) {
            database.transaction {
                database.execute(
                    "CREATE TABLE IF NOT EXISTS lyrics (media_id TEXT PRIMARY KEY, source TEXT NOT NULL, " +
                        "text TEXT NOT NULL, saved_at_millis INTEGER NOT NULL)",
                )
                database.userVersion = 3
            }
        }
    }
}
