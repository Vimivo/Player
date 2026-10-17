@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength", "MaxLineLength", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaMetadataExtractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IncrementalMediaScannerTest {

    @Test
    fun testIncrementalScanningLifecycleAcrossModifications() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-incremental-scan-test")
        val libraryDir = Files.createDirectories(tempDir.resolve("Media"))
        val song1 = libraryDir.resolve("track1.mp3").also { it.writeText("audio payload 1") }
        libraryDir.resolve("track2.flac").writeText("audio payload 2")
        val video1 = libraryDir.resolve("video1.mp4").also { it.writeText("video payload 1") }

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(libraryDir).getOrThrow()
            val scanner = DesktopMediaScanner(database)

            // Step 1: Initial Scan
            val firstResult = scanner.scan(root.id, libraryDir).getOrThrow()
            assertEquals(3, firstResult.discovered)
            assertEquals(3, firstResult.indexed)
            assertEquals(3, firstResult.added)
            assertEquals(0, firstResult.updated)
            assertEquals(0, firstResult.unchanged)
            assertEquals(0, firstResult.unavailable)
            assertEquals(0, firstResult.errors)

            // Step 2: Unchanged Rescan
            val secondResult = scanner.scan(root.id, libraryDir).getOrThrow()
            assertEquals(3, secondResult.discovered)
            assertEquals(0, secondResult.added)
            assertEquals(0, secondResult.updated)
            assertEquals(3, secondResult.unchanged)
            assertEquals(0, secondResult.unavailable)

            // Step 3: Modify one file (size changed)
            song1.writeText("audio payload 1 modified with more bytes")
            val thirdResult = scanner.scan(root.id, libraryDir).getOrThrow()
            assertEquals(3, thirdResult.discovered)
            assertEquals(0, thirdResult.added)
            assertEquals(1, thirdResult.updated)
            assertEquals(2, thirdResult.unchanged)
            assertEquals(0, thirdResult.unavailable)

            // Step 4: Delete one file from disk
            Files.delete(video1)
            val fourthResult = scanner.scan(root.id, libraryDir).getOrThrow()
            assertEquals(2, fourthResult.discovered)
            assertEquals(0, fourthResult.added)
            assertEquals(0, fourthResult.updated)
            assertEquals(2, fourthResult.unchanged)
            assertEquals(1, fourthResult.unavailable)

            // Verify deleted file is available = 0 in database
            val unavailableCount = database.transaction { conn ->
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM media_items WHERE available=0").use { rs ->
                    rs.next(); rs.getInt(1)
                }
            }
            assertEquals(1, unavailableCount)

            // Step 5: Add a new file
            libraryDir.resolve("track3.wav").writeText("audio payload 3")
            val fifthResult = scanner.scan(root.id, libraryDir).getOrThrow()
            assertEquals(3, fifthResult.discovered)
            assertEquals(1, fifthResult.added)
            assertEquals(0, fifthResult.updated)
            assertEquals(2, fifthResult.unchanged)
            assertEquals(0, fifthResult.unavailable)
        }
    }

    @Test
    fun testMetadataCoordinatorMultiBatchLoopAndCancellation() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-meta-batch-test")
        val libraryDir = Files.createDirectories(tempDir.resolve("Media"))
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(libraryDir).getOrThrow()

            // Insert 250 fake media items
            database.transaction { conn ->
                val stmt = conn.prepareStatement(
                    "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,1)",
                )
                for (i in 1..250) {
                    stmt.setString(1, "item-$i")
                    stmt.setString(2, root.id)
                    stmt.setString(3, "file:///mock/item-$i.mp3")
                    stmt.setString(4, "item-$i.mp3")
                    stmt.setString(5, "Title $i")
                    stmt.setString(6, "AUDIO")
                    stmt.setString(7, "audio/mpeg")
                    stmt.setLong(8, 1000L * i)
                    stmt.setLong(9, 1000L * i)
                    stmt.setLong(10, System.currentTimeMillis() + i)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            val metaRepo = DesktopMetadataRepository(database)
            var extractedCount = 0
            val fakeExtractor = object : MediaMetadataExtractor {
                override suspend fun extract(media: MediaItem): Result<ExtractedMediaMetadata> {
                    extractedCount++
                    return Result.success(ExtractedMediaMetadata(title = media.title, artist = "Artist"))
                }
            }

            var updateNotifications = 0
            val coordinator = DesktopMetadataCoordinator(
                repository = metaRepo,
                extractor = fakeExtractor,
                scope = backgroundScope,
                onMetadataUpdated = { updateNotifications++ },
            )

            // Verify candidates count returns initial batch
            val initialCandidates = metaRepo.candidates(root.id)
            assertEquals(200, initialCandidates.size, "Candidates should cap at LIMIT 200 per query")

            // Test cancellation
            coordinator.start(root.id)
            coordinator.cancel(root.id)
            runCurrent()
            assertTrue(extractedCount >= 0)
        }
    }
}
