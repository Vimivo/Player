package dev.vimivo.storage

import dev.vimivo.core.concurrency.PlatformLock

/**
 * A library entry as the iOS app keeps it. [kind] is `audio`, `video` or `stream`; [bookmarkData] is the
 * security-scoped bookmark that keeps access to a file the user picked. Plain types are used so the Swift side can
 * read the values.
 */
@Suppress("ArrayInDataClass") // equals and hashCode below compare the bookmark bytes by content
data class StoredMedia(
    val id: String,
    val uri: String,
    val title: String,
    val kind: String,
    val durationMillis: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val albumArtist: String? = null,
    val bookmarkData: ByteArray? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val dateAddedMillis: Long? = null,
) {
    override fun equals(other: Any?): Boolean = other is StoredMedia && copy(bookmarkData = null) ==
        other.copy(bookmarkData = null) && bookmarkData.contentEqualsOrBothNull(other.bookmarkData)

    override fun hashCode(): Int = copy(bookmarkData = null).toString().hashCode()
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this == other else contentEquals(other)

data class StoredPlaylist(val id: String, val name: String, val mediaIds: List<String>, val createdAtMillis: Long)

data class StoredBookmark(
    val id: String,
    val mediaId: String,
    val name: String,
    val positionMillis: Long,
    val createdAtMillis: Long,
)

data class StoredHistory(
    val id: String,
    val mediaId: String,
    val lastPlayedAtMillis: Long,
    val positionMillis: Long,
    val completed: Boolean,
    val playCount: Int,
)

data class StoredLyrics(val mediaId: String, val source: String, val text: String)

data class StoredResume(val positionMillis: Long, val completed: Boolean)

data class StoredQueue(val mediaIds: List<String>, val currentIndex: Int)

/**
 * The iOS app's persistent library: media, playlists, favourites, bookmarks, the play queue, and the resume and
 * history records. It runs over any [SqlDatabase], migrates the schema when opened ([LibrarySchema]), and is safe to
 * call from several threads (calls are serialised). Failures are thrown, not swallowed.
 */
class LocalLibraryStore(
    private val database: SqlDatabase,
    private val clock: () -> Long,
) : AutoCloseable {
    private val lock = PlatformLock()

    init {
        LibrarySchema.migrate(database)
    }

    // region media

    @Throws(Exception::class)
    fun saveMedia(item: StoredMedia) = lock.withLock {
        database.execute(
            "INSERT OR REPLACE INTO media_items (id, uri, title, kind, duration_millis, artist, album, artwork_uri, " +
                "album_artist, bookmark_data, width, height, size_bytes, date_added_millis) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                item.id, item.uri, item.title, item.kind, item.durationMillis, item.artist, item.album,
                item.artworkUri, item.albumArtist, item.bookmarkData, item.width, item.height, item.sizeBytes,
                item.dateAddedMillis ?: clock(),
            ),
        )
        Unit
    }

    /** Re-points an app-owned file at its current container path, which changes when the app is updated. */
    @Throws(Exception::class)
    fun updateMediaUri(id: String, uri: String) = lock.withLock {
        database.execute("UPDATE media_items SET uri = ? WHERE id = ?", listOf(uri, id))
        Unit
    }

    @Throws(Exception::class)
    fun allMedia(): List<StoredMedia> = lock.withLock {
        database.query(
            "SELECT id, uri, title, kind, duration_millis, artist, album, artwork_uri, album_artist, bookmark_data, " +
                "width, height, size_bytes, date_added_millis FROM media_items ORDER BY title ASC",
        ) { row ->
            StoredMedia(
                id = checkNotNull(row.string(0)),
                uri = checkNotNull(row.string(1)),
                title = checkNotNull(row.string(2)),
                kind = checkNotNull(row.string(3)),
                durationMillis = row.long(4),
                artist = row.string(5),
                album = row.string(6),
                artworkUri = row.string(7),
                albumArtist = row.string(8),
                bookmarkData = row.bytes(9),
                width = row.long(10)?.toInt(),
                height = row.long(11)?.toInt(),
                sizeBytes = row.long(12),
                dateAddedMillis = row.long(13),
            )
        }
    }

    /** Stores width, height and file size discovered after import without touching other fields. */
    @Throws(Exception::class)
    fun updateVideoMetadata(id: String, width: Int?, height: Int?, sizeBytes: Long?) = lock.withLock {
        database.execute(
            "UPDATE media_items SET width = COALESCE(?, width), height = COALESCE(?, height), " +
                "size_bytes = COALESCE(?, size_bytes) WHERE id = ?",
            listOf(width, height, sizeBytes, id),
        )
        Unit
    }

    @Throws(Exception::class)
    fun deleteMedia(id: String) = lock.withLock {
        database.execute("DELETE FROM media_items WHERE id = ?", listOf(id))
        database.execute("DELETE FROM lyrics WHERE media_id = ?", listOf(id))
        Unit
    }

    // endregion

    // region playlists and favourites

    @Throws(Exception::class)
    fun playlists(): List<StoredPlaylist> = lock.withLock {
        val items = database.query("SELECT playlist_id, media_id FROM playlist_items ORDER BY playlist_id, position") {
            checkNotNull(it.string(0)) to checkNotNull(it.string(1))
        }.groupBy({ it.first }, { it.second })
        database.query("SELECT id, name, created_at_millis FROM playlists ORDER BY name ASC") { row ->
            val id = checkNotNull(row.string(0))
            StoredPlaylist(id, checkNotNull(row.string(1)), items[id].orEmpty(), row.long(2) ?: 0L)
        }
    }

    /** Replaces a playlist and its items in one transaction. */
    @Throws(Exception::class)
    fun savePlaylist(playlist: StoredPlaylist) = lock.withLock {
        database.transaction {
            database.execute(
                "INSERT OR REPLACE INTO playlists (id, name, created_at_millis) VALUES (?, ?, ?)",
                listOf(playlist.id, playlist.name, playlist.createdAtMillis),
            )
            database.execute("DELETE FROM playlist_items WHERE playlist_id = ?", listOf(playlist.id))
            playlist.mediaIds.forEachIndexed { position, mediaId ->
                database.execute(
                    "INSERT INTO playlist_items (playlist_id, media_id, position) VALUES (?, ?, ?)",
                    listOf(playlist.id, mediaId, position),
                )
            }
        }
    }

    @Throws(Exception::class)
    fun deletePlaylist(id: String) = lock.withLock {
        database.execute("DELETE FROM playlists WHERE id = ?", listOf(id))
        Unit
    }

    @Throws(Exception::class)
    fun favorites(): Set<String> = lock.withLock {
        database.query("SELECT media_id FROM favorites") { checkNotNull(it.string(0)) }.toSet()
    }

    @Throws(Exception::class)
    fun setFavorite(mediaId: String, favorite: Boolean) = lock.withLock {
        if (favorite) {
            database.execute("INSERT OR IGNORE INTO favorites (media_id) VALUES (?)", listOf(mediaId))
        } else {
            database.execute("DELETE FROM favorites WHERE media_id = ?", listOf(mediaId))
        }
        Unit
    }

    // endregion

    // region bookmarks

    @Throws(Exception::class)
    fun addBookmark(bookmark: StoredBookmark) = lock.withLock {
        database.execute(
            "INSERT OR REPLACE INTO bookmarks (id, media_id, name, position_millis, created_at_millis) " +
                "VALUES (?, ?, ?, ?, ?)",
            listOf(bookmark.id, bookmark.mediaId, bookmark.name, bookmark.positionMillis, bookmark.createdAtMillis),
        )
        Unit
    }

    @Throws(Exception::class)
    fun bookmarks(mediaId: String): List<StoredBookmark> = lock.withLock {
        database.query(
            "SELECT id, media_id, name, position_millis, created_at_millis FROM bookmarks " +
                "WHERE media_id = ? ORDER BY position_millis",
            listOf(mediaId),
        ) { row ->
            StoredBookmark(
                id = checkNotNull(row.string(0)),
                mediaId = checkNotNull(row.string(1)),
                name = checkNotNull(row.string(2)),
                positionMillis = row.long(3) ?: 0L,
                createdAtMillis = row.long(4) ?: 0L,
            )
        }
    }

    @Throws(Exception::class)
    fun deleteBookmark(id: String) = lock.withLock {
        database.execute("DELETE FROM bookmarks WHERE id = ?", listOf(id))
        Unit
    }

    // endregion

    // region lyrics

    /** Keeps the lyrics text a person loaded for [mediaId], replacing any earlier text for that track. */
    @Throws(Exception::class)
    fun saveLyrics(mediaId: String, source: String, text: String) = lock.withLock {
        database.execute(
            "INSERT OR REPLACE INTO lyrics (media_id, source, text, saved_at_millis) VALUES (?, ?, ?, ?)",
            listOf(mediaId, source, text, clock()),
        )
        Unit
    }

    @Throws(Exception::class)
    fun lyrics(mediaId: String): StoredLyrics? = lock.withLock {
        database.query("SELECT media_id, source, text FROM lyrics WHERE media_id = ?", listOf(mediaId)) { row ->
            StoredLyrics(checkNotNull(row.string(0)), checkNotNull(row.string(1)), checkNotNull(row.string(2)))
        }.firstOrNull()
    }

    @Throws(Exception::class)
    fun deleteLyrics(mediaId: String) = lock.withLock {
        database.execute("DELETE FROM lyrics WHERE media_id = ?", listOf(mediaId))
        Unit
    }

    // endregion

    // region queue

    /** Replaces the persisted queue atomically so a crash never leaves a half-written queue. */
    @Throws(Exception::class)
    fun saveQueue(mediaIds: List<String>, currentIndex: Int) = lock.withLock {
        database.transaction {
            database.execute("DELETE FROM queue_items")
            database.execute("DELETE FROM queue_state")
            mediaIds.forEachIndexed { position, id ->
                database.execute("INSERT INTO queue_items (position, media_id) VALUES (?, ?)", listOf(position, id))
            }
            if (mediaIds.isNotEmpty()) {
                database.execute(
                    "INSERT INTO queue_state (id, current_index) VALUES (1, ?)",
                    listOf(maxOf(0, currentIndex)),
                )
            }
        }
    }

    /** The saved queue; the current index is kept inside it, and an empty queue is at index 0. */
    @Throws(Exception::class)
    fun loadQueue(): StoredQueue = lock.withLock {
        val ids = database.query("SELECT media_id FROM queue_items ORDER BY position") { checkNotNull(it.string(0)) }
        val index = database.query("SELECT current_index FROM queue_state WHERE id = 1") { it.long(0)?.toInt() ?: 0 }
            .firstOrNull() ?: 0
        StoredQueue(ids, if (ids.isEmpty()) 0 else index.coerceIn(0, ids.size - 1))
    }

    // endregion

    // region history and resume

    /** Counts a play and refreshes recency without touching the saved resume position. */
    @Throws(Exception::class)
    fun recordPlayStart(mediaId: String) = lock.withLock {
        database.execute(
            "INSERT INTO playback_history " +
                "(id, media_id, last_played_at_millis, position_millis, completed, play_count) " +
                "VALUES (?, ?, ?, 0, 0, 1) ON CONFLICT(id) DO UPDATE SET " +
                "last_played_at_millis = excluded.last_played_at_millis, play_count = play_count + 1",
            listOf(mediaId, mediaId, clock()),
        )
        Unit
    }

    /** Persists the resume position; never counts as an additional play. */
    @Throws(Exception::class)
    fun savePosition(mediaId: String, positionMillis: Long, completed: Boolean) = lock.withLock {
        database.execute(
            "INSERT INTO playback_history " +
                "(id, media_id, last_played_at_millis, position_millis, completed, play_count) " +
                "VALUES (?, ?, ?, ?, ?, 0) ON CONFLICT(id) DO UPDATE SET " +
                "last_played_at_millis = excluded.last_played_at_millis, " +
                "position_millis = excluded.position_millis, completed = excluded.completed",
            listOf(mediaId, mediaId, clock(), positionMillis, if (completed) 1 else 0),
        )
        Unit
    }

    @Throws(Exception::class)
    fun resumeState(mediaId: String): StoredResume? = lock.withLock {
        database.query("SELECT position_millis, completed FROM playback_history WHERE id = ?", listOf(mediaId)) {
            StoredResume(it.long(0) ?: 0L, (it.long(1) ?: 0L) != 0L)
        }.firstOrNull()
    }

    @Throws(Exception::class)
    fun history(): List<StoredHistory> = lock.withLock {
        database.query(
            "SELECT id, media_id, last_played_at_millis, position_millis, completed, play_count " +
                "FROM playback_history " +
                "ORDER BY last_played_at_millis DESC LIMIT $HISTORY_LIMIT",
        ) { row ->
            StoredHistory(
                id = checkNotNull(row.string(0)),
                mediaId = checkNotNull(row.string(1)),
                lastPlayedAtMillis = row.long(2) ?: 0L,
                positionMillis = row.long(3) ?: 0L,
                completed = (row.long(4) ?: 0L) != 0L,
                playCount = row.long(5)?.toInt() ?: 0,
            )
        }
    }

    // endregion

    override fun close() = lock.withLock { database.close() }

    private companion object {
        const val HISTORY_LIMIT = 100
    }
}
