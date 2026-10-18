package dev.vimivo.platform.android.library

import androidx.sqlite.db.SupportSQLiteProgram
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryBuilderTest {
    @Test
    fun pageBuildsParameterizedSearchAndPagingQuery() {
        val query = LibraryQuery(
            text = "100%_mix",
            kind = MediaKind.AUDIO,
            sort = LibrarySort.DATE_ADDED,
            descending = true,
            offset = 200,
            limit = 100,
        )

        val sql = LibraryQueryBuilder.page(query)
        val bindings = RecordingProgram().also(sql::bindTo).values

        assertTrue(sql.sql.contains("ORDER BY dateAddedEpochSeconds DESC"))
        assertTrue(sql.sql.contains("LIKE ? ESCAPE"))
        assertFalse(sql.sql.contains(query.text))
        assertEquals("AUDIO", bindings[1])
        assertEquals("100\\%\\_mix%", bindings[2])
        assertEquals(100L, bindings[7])
        assertEquals(200L, bindings[8])
    }

    @Test
    fun documentTreeCopiesOfMediaStoreFilesAreHidden() {
        val page = LibraryQueryBuilder.page(LibraryQuery(kind = MediaKind.VIDEO)).sql
        val count = LibraryQueryBuilder.count(LibraryQuery(kind = MediaKind.VIDEO)).sql
        val categories = LibraryQueryBuilder.categories(MediaKind.VIDEO, LibraryCategoryField.FOLDER).sql

        listOf(page, count, categories).forEach { sql ->
            assertTrue(sql.contains(LibraryQueryBuilder.NOT_SHADOWED_BY_MEDIA_STORE))
        }
    }

    @Test
    fun recentlyWatchedIsRestrictedToPlayedVideos() {
        val sql = LibraryQueryBuilder.count(LibraryQuery(recentlyWatchedOnly = true))
        val bindings = RecordingProgram().also(sql::bindTo).values

        assertTrue(sql.sql.contains("lastPlayedAtEpochMillis IS NOT NULL"))
        assertEquals(mapOf(1 to "VIDEO"), bindings)
    }

    @Test
    fun continueWatchingRequiresANonZeroResumePosition() {
        val sql = LibraryQueryBuilder.count(LibraryQuery(continueWatchingOnly = true))
        val bindings = RecordingProgram().also(sql::bindTo).values

        assertTrue(sql.sql.contains("lastPlaybackPositionMillis > 0"))
        assertEquals(mapOf(1 to "VIDEO"), bindings)
    }

    @Test
    fun everyMusicSortUsesAWhitelistedIndexedColumn() {
        val expected = mapOf(
            LibrarySort.TITLE to "title COLLATE NOCASE",
            LibrarySort.ARTIST to "artist COLLATE NOCASE",
            LibrarySort.ALBUM to "album COLLATE NOCASE",
            LibrarySort.DURATION to "durationMillis",
            LibrarySort.DATE_ADDED to "dateAddedEpochSeconds",
            LibrarySort.FILE_SIZE to "sizeBytes",
            LibrarySort.TRACK_NUMBER to "trackNumber",
        )
        expected.forEach { (sort, column) ->
            assertTrue(LibraryQueryBuilder.page(LibraryQuery(sort = sort)).sql.contains("ORDER BY $column"))
        }
    }

    @Test
    fun albumArtistFilterIsParameterized() {
        val sql = LibraryQueryBuilder.count(LibraryQuery(kind = MediaKind.AUDIO, albumArtist = "Various Artists"))
        val bindings = RecordingProgram().also(sql::bindTo).values
        assertTrue(sql.sql.contains("albumArtist = ?"))
        assertEquals("Various Artists", bindings[2])
    }

    @Test
    fun organizationFiltersAndSearchColumnsAreParameterized() {
        val query = LibraryQuery(
            text = "Movies",
            folderPath = "Videos/Movies",
            minimumDurationMillis = 60_000,
            maximumDurationMillis = 7_200_000,
            mimeType = "video/mp4",
        )
        val sql = LibraryQueryBuilder.count(query)
        val bindings = RecordingProgram().also(sql::bindTo).values

        assertTrue(sql.sql.contains("fileName LIKE"))
        assertTrue(sql.sql.contains("folderPath LIKE"))
        assertTrue(sql.sql.contains("durationMillis >= ?"))
        assertTrue(sql.sql.contains("durationMillis <= ?"))
        assertTrue(sql.sql.contains("mimeType = ?"))
        assertFalse(sql.sql.contains("Videos/Movies"))
        assertEquals("Videos/Movies", bindings[1])
    }
}

private class RecordingProgram : SupportSQLiteProgram {
    val values = mutableMapOf<Int, Any?>()
    override fun bindNull(index: Int) { values[index] = null }
    override fun bindLong(index: Int, value: Long) { values[index] = value }
    override fun bindDouble(index: Int, value: Double) { values[index] = value }
    override fun bindString(index: Int, value: String) { values[index] = value }
    override fun bindBlob(index: Int, value: ByteArray) { values[index] = value }
    override fun clearBindings() = values.clear()
    override fun close() = Unit
}
