package dev.vimivo.platform.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.MediaKind
import dev.vimivo.platform.android.library.LibraryQueryBuilder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var database: VimivoDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VimivoDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.libraryDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun upsertPreventsDuplicatesAndQueriesIndexedMetadata() = runTest {
        dao.upsertMedia(listOf(media("one", "First", "Album A"), media("two", "Second", "Album A")))
        dao.upsertMedia(listOf(media("one", "First updated", "Album A")))

        val pageQuery = LibraryQuery(kind = MediaKind.AUDIO, limit = 1)
        val page = dao.page(LibraryQueryBuilder.page(pageQuery))
        val count = dao.count(LibraryQueryBuilder.count(pageQuery))
        val albums = dao.categories(LibraryQueryBuilder.categories(MediaKind.AUDIO, LibraryCategoryField.ALBUM))

        assertEquals(2, count)
        assertEquals(1, page.size)
        assertEquals("First updated", page.single().title)
        assertEquals(2, albums.single().itemCount)
    }

    @Test
    fun missingRowsAreHiddenUntilExplicitCleanup() = runTest {
        dao.upsertMedia(listOf(media("old", "Removed", scanToken = 1), media("new", "Present", scanToken = 2)))
        dao.upsertHistory(PlaybackHistoryEntity("old", 500, 1, 1, false))

        assertEquals(1, dao.markUnseenUnavailable("MEDIA_STORE", "external", "AUDIO", 2))
        assertFalse(dao.page(LibraryQueryBuilder.page(LibraryQuery())).any { it.id == "old" })
        assertTrue(dao.mediaBySource("MEDIA_STORE").any { it.id == "new" })
        assertEquals(1, dao.deleteUnavailableWithHistory())
        assertEquals(null, dao.history("old"))
    }

    @Test
    fun favoritesPersistAndFilenameSearchUsesTheIndexedColumn() = runTest {
        dao.upsertMedia(listOf(media("one", "Unrelated title").copy(fileName = "recording.flac")))
        dao.setFavorite("one", true)

        val favorite = dao.page(LibraryQueryBuilder.page(LibraryQuery(favoritesOnly = true))).single()
        val byFilename = dao.page(LibraryQueryBuilder.page(LibraryQuery(text = "recording")))

        assertTrue(favorite.isFavorite)
        assertEquals("one", byFilename.single().id)
    }

    private fun media(id: String, title: String, album: String? = null, scanToken: Long = 1) = MediaEntity(
        id = id,
        uri = "content://test/$id",
        kind = "AUDIO",
        source = "MEDIA_STORE",
        volumeName = "external",
        mediaStoreId = id.hashCode().toLong(),
        title = title,
        fileName = "$title.mp3",
        artist = "Artist",
        album = album,
        albumArtist = null,
        genre = null,
        durationMillis = 1_000,
        sizeBytes = 100,
        mimeType = "audio/test",
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
        folderName = "Music",
        folderPath = "Music",
        scanToken = scanToken,
    )
}
