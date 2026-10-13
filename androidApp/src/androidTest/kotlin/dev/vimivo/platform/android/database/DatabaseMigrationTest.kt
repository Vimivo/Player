package dev.vimivo.platform.android.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VimivoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateTwoToThreePreservesMediaAndAddsOrganizationTables() = runTest {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO media (id, uri, kind, source, volumeName, mediaStoreId, title, artist, album, " +
                    "albumArtist, genre, durationMillis, sizeBytes, mimeType, dateAddedEpochSeconds, " +
                    "dateModifiedEpochSeconds, width, height, frameRate, bitrate, trackNumber, discNumber, " +
                    "releaseYear, artworkUri, folderName, folderPath, isFavorite, isAvailable, " +
                    "lastPlaybackPositionMillis, lastPlayedAtEpochMillis, scanToken) VALUES " +
                    "('one','content://test/one','AUDIO','MEDIA_STORE','external',1,'Song',NULL,NULL,NULL,NULL," +
                    "1000,10,'audio/mpeg',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'Music','Music',1,1,0,NULL,1)",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, VimivoDatabase::class.java, TEST_DB).build()
        try {
            val item = migrated.libraryDao().mediaByIds(listOf("one")).single()
            assertEquals("Song", item.title)
            assertEquals(null, item.fileName)
            migrated.organizationDao().upsertBookmark(BookmarkEntity("mark", "one", "Point", 500, 2))
            assertEquals(1, migrated.organizationDao().observeBookmarks("one").first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    private companion object { const val TEST_DB = "migration-2-3" }
}
