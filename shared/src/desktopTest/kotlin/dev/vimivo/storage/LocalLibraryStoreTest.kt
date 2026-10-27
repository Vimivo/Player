package dev.vimivo.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iOS library store's behaviour, run over JDBC SQLite; the iOS driver gets the same checks on a simulator. */
class LocalLibraryStoreTest {
    private var now = 1_000_000L
    private val store = LocalLibraryStore(JdbcSqlDatabase(), clock = { now })

    private fun song(id: String, title: String = "Song $id") =
        StoredMedia(id, "file:///$id.mp3", title, "audio", durationMillis = 120_000, artist = "Artist", album = "Album")

    @Test
    fun mediaIsSavedReadBackOrderedByTitleAndDeleted() {
        store.saveMedia(song("b", "Beta"))
        store.saveMedia(song("a", "Alpha").copy(bookmarkData = byteArrayOf(1, 2, 3), albumArtist = "Band"))
        val all = store.allMedia()
        assertEquals(listOf("Alpha", "Beta"), all.map { it.title })
        assertContentEquals(byteArrayOf(1, 2, 3), all[0].bookmarkData)
        assertEquals("Band", all[0].albumArtist)
        assertEquals(1_000_000L, all[0].dateAddedMillis, "a new entry is stamped with the clock")
        store.deleteMedia("a")
        assertEquals(listOf("b"), store.allMedia().map { it.id })
    }

    @Test
    fun savingAgainReplacesTheEntryAndKeepsAGivenDateAdded() {
        store.saveMedia(song("a").copy(dateAddedMillis = 5L))
        store.saveMedia(song("a", "Renamed").copy(dateAddedMillis = 5L))
        val saved = store.allMedia().single()
        assertEquals("Renamed", saved.title)
        assertEquals(5L, saved.dateAddedMillis)
    }

    @Test
    fun theUriCanBeRepointedAndVideoMetadataFilledInWithoutErasingWhatIsThere() {
        store.saveMedia(song("v").copy(kind = "video"))
        store.updateMediaUri("v", "file:///moved/v.mp4")
        assertEquals("file:///moved/v.mp4", store.allMedia().single().uri)
        assertNull(store.allMedia().single().width)
        store.updateVideoMetadata("v", 1920, 1080, 2_048)
        store.updateVideoMetadata("v", null, null, null)
        val saved = store.allMedia().single()
        assertEquals(1920, saved.width)
        assertEquals(1080, saved.height)
        assertEquals(2_048L, saved.sizeBytes)
    }

    @Test
    fun favouritesToggle() {
        store.setFavorite("x", true)
        store.setFavorite("x", true)
        assertEquals(setOf("x"), store.favorites())
        store.setFavorite("x", false)
        assertTrue(store.favorites().isEmpty())
    }

    @Test
    fun playlistsKeepTheirItemOrderAndAreReplacedAsAWhole() {
        store.savePlaylist(StoredPlaylist("p", "Chill", listOf("m1", "m2", "m3"), 7L))
        store.savePlaylist(StoredPlaylist("q", "Another", listOf("m9"), 8L))
        assertEquals(listOf("Another", "Chill"), store.playlists().map { it.name })
        assertEquals(listOf("m1", "m2", "m3"), store.playlists().first { it.id == "p" }.mediaIds)
        store.savePlaylist(StoredPlaylist("p", "Chill", listOf("m3", "m1"), 7L))
        assertEquals(listOf("m3", "m1"), store.playlists().first { it.id == "p" }.mediaIds)
        store.deletePlaylist("p")
        assertEquals(listOf("q"), store.playlists().map { it.id })
        assertEquals(listOf("m9"), store.playlists().single().mediaIds, "deleting one playlist leaves the other's items")
    }

    @Test
    fun bookmarksComeBackInPositionOrderAndCanBeDeleted() {
        store.addBookmark(StoredBookmark("late", "m", "Late", 90_000, 1))
        store.addBookmark(StoredBookmark("early", "m", "Early", 10_000, 2))
        store.addBookmark(StoredBookmark("other", "n", "Other", 5_000, 3))
        assertEquals(listOf("Early", "Late"), store.bookmarks("m").map { it.name })
        store.deleteBookmark("early")
        assertEquals(listOf("late"), store.bookmarks("m").map { it.id })
    }

    @Test
    fun theQueueRoundTripsClampsItsIndexAndCanBeEmptied() {
        store.saveQueue(listOf("a", "b", "c"), 1)
        assertEquals(StoredQueue(listOf("a", "b", "c"), 1), store.loadQueue())
        store.saveQueue(listOf("a"), 5)
        assertEquals(0, store.loadQueue().currentIndex)
        store.saveQueue(emptyList(), 0)
        assertEquals(StoredQueue(emptyList(), 0), store.loadQueue())
    }

    /** Regression: starting playback used to overwrite the saved position with 0, so nothing could resume. */
    @Test
    fun startingAPlayKeepsTheResumePositionAndCountsOnePlay() {
        store.recordPlayStart("m")
        store.savePosition("m", 120_000, completed = false)
        now += 10
        store.savePosition("m", 130_000, completed = false)
        store.recordPlayStart("m")
        assertEquals(StoredResume(130_000, false), store.resumeState("m"))
        assertEquals(2, store.history().single { it.mediaId == "m" }.playCount)
        assertEquals(1_000_010L, store.history().single().lastPlayedAtMillis)
    }

    @Test
    fun savingAPositionNeverCountsAsAPlayAndCompletionIsRemembered() {
        store.savePosition("m", 45_000, completed = false)
        assertEquals(0, store.history().single().playCount)
        store.savePosition("m", 99_000, completed = true)
        assertTrue(store.resumeState("m")!!.completed)
        assertNull(store.resumeState("unknown"))
    }

    @Test
    fun historyIsNewestFirstAndLimitedToAHundred() {
        repeat(105) { index ->
            now += 1
            store.savePosition("m$index", index.toLong(), completed = false)
        }
        val history = store.history()
        assertEquals(100, history.size)
        assertEquals("m104", history.first().mediaId)
    }

    @Test
    fun aFailedTransactionLeavesNothingBehind() {
        val database = JdbcSqlDatabase()
        val local = LocalLibraryStore(database, clock = { now })
        local.savePlaylist(StoredPlaylist("p", "Keep", listOf("a", "b"), 1))
        // A duplicate position cannot be stored, so the second save fails half way through.
        val failing = runCatching {
            database.transaction {
                database.execute("DELETE FROM playlist_items WHERE playlist_id = 'p'")
                database.execute("INSERT INTO playlist_items (playlist_id, media_id, position) VALUES ('p', 'x', 0)")
                database.execute("INSERT INTO playlist_items (playlist_id, media_id, position) VALUES ('p', 'y', 0)")
            }
        }
        assertTrue(failing.isFailure)
        assertEquals(listOf("a", "b"), local.playlists().single().mediaIds)
    }

    @Test
    fun deletingAPlaylistCascadesItsItems() {
        val database = JdbcSqlDatabase()
        val local = LocalLibraryStore(database, clock = { now })
        local.savePlaylist(StoredPlaylist("p", "Gone", listOf("a"), 1))
        local.deletePlaylist("p")
        assertEquals(0, database.query("SELECT COUNT(*) FROM playlist_items") { it.long(0) }.single()?.toInt())
        assertFalse(local.playlists().any())
        assertNotNull(local)
    }

    @Test
    fun lyricsAreKeptPerTrackReplacedAndRemovedWithTheTrack() {
        store.saveMedia(song("a"))
        assertNull(store.lyrics("a"))
        store.saveLyrics("a", "file:///a.lrc", "[00:01.00]One")
        store.saveLyrics("a", "file:///b.lrc", "[00:02.00]Two")
        assertEquals(StoredLyrics("a", "file:///b.lrc", "[00:02.00]Two"), store.lyrics("a"))
        store.deleteMedia("a")
        assertNull(store.lyrics("a"), "lyrics go with the track")
        store.saveLyrics("x", "memory:", "plain")
        store.deleteLyrics("x")
        assertNull(store.lyrics("x"))
    }
}
