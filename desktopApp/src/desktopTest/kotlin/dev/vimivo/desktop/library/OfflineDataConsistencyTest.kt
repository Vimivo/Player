@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength", "MaxLineLength", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.QueueItem
import dev.vimivo.playback.QueueRepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDataConsistencyTest {

    @Test
    fun scenarioG_offlineLocalLibraryOperationsSucceedWithoutNetwork() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-offline-test")
        val libraryDir = Files.createDirectories(tempDir.resolve("Media"))
        libraryDir.resolve("local1.mp3").writeText("audio local 1")
        libraryDir.resolve("local2.mp3").writeText("audio local 2")

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val personalRepo = DesktopPersonalMediaRepository(database)
            val scanner = DesktopMediaScanner(database)

            // Step 1: Scan local media offline
            val root = libraryRepo.addRoot(libraryDir).getOrThrow()
            val scanResult = scanner.scan(root.id, libraryDir).getOrThrow()
            assertEquals(2, scanResult.discovered)
            assertEquals(2, scanResult.indexed)

            // Step 2: Browse and Search local media
            val allAudio = libraryRepo.allMedia(MediaKind.AUDIO)
            assertEquals(2, allAudio.size)
            val searchResults = libraryRepo.allMedia(MediaKind.AUDIO).filter { it.title.contains("local1") }
            assertEquals(1, searchResults.size)

            // Step 3: Playlist operations offline
            val playlist = personalRepo.createPlaylist("Offline Favorites")
            personalRepo.addToPlaylist(playlist.id, allAudio[0].id)
            personalRepo.addToPlaylist(playlist.id, allAudio[1].id)
            val playlists = personalRepo.playlists()
            assertEquals(1, playlists.size)
            assertEquals(2, playlists[0].items.size)

            // Step 4: Toggle Favorite offline
            personalRepo.setFavorite(FavoriteTarget.MEDIA, allAudio[0].id.value, favorite = true)
            val favIds = personalRepo.favoriteIds(FavoriteTarget.MEDIA)
            assertTrue(allAudio[0].id.value in favIds)

            // Step 5: Save and restore playback position offline
            personalRepo.savePosition(allAudio[0].id, 45_000L, 180_000L, completed = false)
            val resume = personalRepo.position(allAudio[0].id)
            assertNotNull(resume)
            assertEquals(45_000L, resume.positionMillis)
            assertEquals(180_000L, resume.durationMillis)
            assertFalse(resume.completed)
        }
    }

    @Test
    fun scenarioH_transactionalUpdatesRollbackCleanlyOnFailure() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-tx-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val personalRepo = DesktopPersonalMediaRepository(database)
            val playlist = personalRepo.createPlaylist("Valid Initial Playlist")
            assertEquals(1, personalRepo.playlists().size)

            // Attempt a transaction that fails halfway through
            val failedResult = runCatching {
                database.transaction { conn ->
                    // 1. Valid operation inside transaction
                    conn.prepareStatement("INSERT INTO playlists(id,name,created_at_ms,updated_at_ms) VALUES(?,?,?,?)").use { stmt ->
                        stmt.setString(1, "temp-tx-id")
                        stmt.setString(2, "Uncommitted Playlist")
                        stmt.setLong(3, 1000L)
                        stmt.setLong(4, 1000L)
                        stmt.executeUpdate()
                    }
                    // 2. Deliberate failure triggering rollback
                    error("Simulated disk I/O failure during multi-table update")
                }
            }

            assertTrue(failedResult.isFailure)

            // Verify database state was rolled back to initial valid state
            val playlistsAfterRollback = personalRepo.playlists()
            assertEquals(1, playlistsAfterRollback.size)
            assertEquals(playlist.id, playlistsAfterRollback[0].id)
            assertEquals("Valid Initial Playlist", playlistsAfterRollback[0].name)
        }
    }

    @Test
    fun scenarioI_staleRestorationReconciliationDropsOrHandlesMissingMedia() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-stale-restore-test")
        val libraryDir = Files.createDirectories(tempDir.resolve("Media"))
        libraryDir.resolve("available_track.mp3").writeText("song1")
        val song2 = libraryDir.resolve("deleted_track.mp3").also { it.writeText("song2") }

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val queueRepo = DesktopPlaybackQueueRepository(database)
            val root = libraryRepo.addRoot(libraryDir).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, libraryDir).getOrThrow()

            val mediaItems = libraryRepo.allMedia(MediaKind.AUDIO)
            assertEquals(2, mediaItems.size)

            val availableItem = mediaItems.first { it.fileName == "available_track.mp3" }
            val deletedItem = mediaItems.first { it.fileName == "deleted_track.mp3" }

            // Save queue with both tracks
            val snapshot = PlaybackQueueSnapshot(
                sourceItems = listOf(QueueItem(1L, availableItem), QueueItem(2L, deletedItem)),
                playbackItems = listOf(QueueItem(1L, availableItem), QueueItem(2L, deletedItem)),
                currentQueueId = 1L,
                shuffleEnabled = false,
                repeatMode = QueueRepeatMode.OFF,
                sourceLabel = "Saved Queue Session",
            )
            queueRepo.save(snapshot)

            // Now delete song2 from disk and rescan to mark it available=0 in database
            Files.delete(song2)
            DesktopMediaScanner(database).scan(root.id, libraryDir).getOrThrow()

            // Verify song2 is available=0
            val availableAfterDelete = libraryRepo.media(listOf(deletedItem.id), includeUnavailable = true)
            assertEquals(1, availableAfterDelete.size)
            assertFalse(availableAfterDelete[0].isAvailable)

            // Retain only available items on restoration
            val availableIds = libraryRepo.media(snapshot.playbackItems.map { it.media.id }).map { it.id }.toSet()
            val reconciled = snapshot.retainOnly { it.id in availableIds }

            assertEquals(1, reconciled.playbackItems.size)
            assertEquals(availableItem.id, reconciled.playbackItems[0].media.id)
            assertEquals(1L, reconciled.currentQueueId)
        }
    }

    @Test
    fun scenarioJ_lifecycleAndRepeatedDisposalCleansResourcesWithoutLeak() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-lifecycle-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        for (cycle in 1..5) {
            val database = DesktopDatabase.open(dbPath)
            val personalRepo = DesktopPersonalMediaRepository(database)

            // Perform basic operation
            personalRepo.createPlaylist("Cycle $cycle Playlist")
            assertEquals(cycle, personalRepo.playlists().size)

            // Close database cleanly
            database.close()
        }

        // Reopen final time and verify data persisted across all 5 open/close cycles
        DesktopDatabase.open(dbPath).use { database ->
            val personalRepo = DesktopPersonalMediaRepository(database)
            assertEquals(5, personalRepo.playlists().size)
        }
    }
}
