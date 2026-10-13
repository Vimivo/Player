package dev.vimivo.platform.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizationDaoTest {
    private lateinit var database: VimivoDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VimivoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun playlistsPreserveOrderAndCascadeTheirItems() = runTest {
        val dao = database.playlistDao()
        dao.upsertPlaylist(PlaylistEntity("p1", "Mixed media", 1))
        dao.upsertItems(
            listOf(
                PlaylistItemEntity("p1", "video", 1),
                PlaylistItemEntity("p1", "audio", 0),
            ),
        )

        assertEquals(listOf("audio", "video"), dao.get("p1")!!.items.sortedBy { it.position }.map { it.mediaId })
        dao.delete("p1")
        assertEquals(null, dao.get("p1"))
    }

    @Test
    fun bookmarksAndHistoryAreObservableAndLocal() = runTest {
        database.libraryDao().upsertMedia(listOf(testMedia("lecture")))
        val dao = database.organizationDao()
        dao.upsertBookmark(BookmarkEntity("b1", "lecture", "Chapter two", 90_000, 10))
        database.libraryDao().upsertHistory(PlaybackHistoryEntity("lecture", 90_000, 20, 3, false))

        assertEquals("Chapter two", dao.observeBookmarks("lecture").first().single().name)
        assertEquals(3, dao.observeHistory(10).first().single().playCount)
        assertEquals(1, dao.renameBookmark("b1", "Key point"))
        assertEquals("Key point", dao.observeBookmarks("lecture").first().single().name)
        assertEquals(1, dao.deleteBookmark("b1"))
        assertTrue(dao.observeBookmarks("lecture").first().isEmpty())
    }
}

internal fun testMedia(id: String) = MediaEntity(
    id = id,
    uri = "content://test/$id",
    kind = "AUDIO",
    source = "MEDIA_STORE",
    volumeName = "external",
    mediaStoreId = 1,
    title = "Lecture",
    fileName = "lecture.mp3",
    artist = null,
    album = null,
    albumArtist = null,
    genre = null,
    durationMillis = 120_000,
    sizeBytes = 100,
    mimeType = "audio/mpeg",
    dateAddedEpochSeconds = 1,
    dateModifiedEpochSeconds = 1,
    width = null,
    height = null,
    frameRate = null,
    bitrate = null,
    trackNumber = null,
    discNumber = null,
    releaseYear = null,
    artworkUri = null,
    folderName = "Audio",
    folderPath = "Audio",
    scanToken = 1,
)
