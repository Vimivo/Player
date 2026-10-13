package dev.vimivo.platform.android.library

import androidx.sqlite.db.SupportSQLiteProgram
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
        assertEquals("%100\\%\\_mix%", bindings[2])
        assertEquals(100L, bindings[5])
        assertEquals(200L, bindings[6])
    }

    @Test
    fun recentlyWatchedIsRestrictedToPlayedVideos() {
        val sql = LibraryQueryBuilder.count(LibraryQuery(recentlyWatchedOnly = true))
        val bindings = RecordingProgram().also(sql::bindTo).values

        assertTrue(sql.sql.contains("lastPlayedAtEpochMillis IS NOT NULL"))
        assertEquals(mapOf(1 to "VIDEO"), bindings)
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
