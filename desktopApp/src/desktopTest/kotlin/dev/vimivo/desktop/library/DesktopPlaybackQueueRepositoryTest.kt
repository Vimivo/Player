package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.QueueItem
import dev.vimivo.playback.QueueRepeatMode
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPlaybackQueueRepositoryTest {
    @Test
    fun persistsDuplicateOccurrencesAndPlaybackOrder() = runTest {
        val directory = Files.createTempDirectory("vimivo-queue-test")
        val library = Files.createDirectories(directory.resolve("Library"))
        val first = library.resolve("first.mp3").also { it.writeText("audio") }
        val second = library.resolve("second.mp3").also { it.writeText("audio") }
        val databasePath = directory.resolve("vimivo.sqlite")

        DesktopDatabase.open(databasePath).use { database ->
            val libraryRepository = DesktopLibraryRepository(database)
            val root = libraryRepository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            val media = libraryRepository.media(MediaKind.AUDIO).associateBy { it.fileName }
            val firstMedia = requireNotNull(media[first.fileName.toString()])
            val secondMedia = requireNotNull(media[second.fileName.toString()])
            val snapshot = PlaybackQueueSnapshot(
                sourceItems = listOf(QueueItem(4, firstMedia), QueueItem(7, secondMedia), QueueItem(9, firstMedia)),
                playbackItems = listOf(QueueItem(9, firstMedia), QueueItem(4, firstMedia), QueueItem(7, secondMedia)),
                currentQueueId = 4,
                shuffleEnabled = true,
                repeatMode = QueueRepeatMode.ALL,
                sourceLabel = "Test Queue",
            )

            DesktopPlaybackQueueRepository(database).save(snapshot, now = 42)
            val restored = requireNotNull(DesktopPlaybackQueueRepository(database).load())
            assertEquals(listOf(9L, 4L, 7L), restored.items.map(StoredQueueItem::queueId))
            assertEquals(listOf(2, 0, 1), restored.items.map(StoredQueueItem::sourcePosition))
            assertEquals(4L, restored.currentQueueId)
            assertEquals(true, restored.shuffleEnabled)
            assertEquals(QueueRepeatMode.ALL, restored.repeatMode)
            assertEquals("Test Queue", restored.sourceLabel)
        }

        DesktopDatabase.open(databasePath).use { database ->
            assertEquals(3, requireNotNull(DesktopPlaybackQueueRepository(database).load()).items.size)
        }
    }

    @Test
    fun returnsNullForAnEmptyOrClearedQueue() {
        val directory = Files.createTempDirectory("vimivo-empty-queue-test")
        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopPlaybackQueueRepository(database)
            assertNull(repository.load())
            repository.clear()
            assertNull(repository.load())
        }
    }
}
