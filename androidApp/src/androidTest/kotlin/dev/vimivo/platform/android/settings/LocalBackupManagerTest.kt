package dev.vimivo.platform.android.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vimivo.platform.android.database.BookmarkEntity
import dev.vimivo.platform.android.database.PlaybackHistoryEntity
import dev.vimivo.platform.android.database.PlaylistEntity
import dev.vimivo.platform.android.database.PlaylistItemEntity
import dev.vimivo.platform.android.database.VimivoDatabase
import dev.vimivo.platform.android.database.testMedia
import dev.vimivo.platform.android.network.NetworkHistory
import dev.vimivo.platform.android.network.NetworkHistoryStore
import dev.vimivo.settings.AccentColor
import dev.vimivo.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: VimivoDatabase
    private lateinit var settings: AndroidSettingsRepository
    private lateinit var network: NetworkHistoryStore
    private lateinit var backup: LocalBackupManager

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, VimivoDatabase::class.java).allowMainThreadQueries().build()
        settings = AndroidSettingsRepository(context)
        network = NetworkHistoryStore(context)
        backup = LocalBackupManager(
            context,
            database,
            database.libraryDao(),
            database.playlistDao(),
            database.organizationDao(),
            settings,
            network,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun roundTripRestoresOrganizationSettingsAndSkipsMissingReferences() = runTest {
        val media = testMedia("audio").copy(uri = "content://library/audio", isFavorite = true)
        database.libraryDao().upsertMedia(listOf(media))
        database.playlistDao().upsertPlaylist(PlaylistEntity("playlist", "Study", 1))
        database.playlistDao().upsertItems(listOf(PlaylistItemEntity("playlist", media.id, 0)))
        database.organizationDao().upsertBookmark(BookmarkEntity("bookmark", media.id, "Chapter", 500, 1))
        database.libraryDao().upsertHistory(PlaybackHistoryEntity(media.id, 500, 2, 3, false))
        settings.update { it.copy(accentColor = AccentColor.GREEN, reduceAnimations = true) }
        network.save(NetworkHistory(emptyList(), setOf("https://example.test/radio.mp3")))
        val file = java.io.File(context.cacheDir, "backup-test.json")
        backup.export(Uri.fromFile(file)).getOrThrow()

        database.playlistDao().delete("playlist")
        database.organizationDao().deleteBookmark("bookmark")
        database.libraryDao().clearHistory()
        database.libraryDao().setFavorite(media.id, false)
        settings.update { AppSettings() }
        val report = backup.restore(Uri.fromFile(file)).getOrThrow()

        assertEquals(1, report.playlists)
        assertEquals(1, report.favorites)
        assertEquals(1, report.bookmarks)
        assertEquals(1, report.history)
        assertTrue(database.libraryDao().mediaByIds(listOf(media.id)).single().isFavorite)
        assertEquals("Study", database.playlistDao().get("playlist")?.playlist?.name)
        assertEquals(AccentColor.GREEN, settings.settings.first().accentColor)
        assertTrue(settings.settings.first().reduceAnimations)
        assertTrue("https://example.test/radio.mp3" in network.load().favorites)
        file.delete()
    }
}
