@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MagicNumber", "LongMethod",
)

package dev.vimivo.desktop.performance

import dev.vimivo.core.model.MediaId
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopMediaCache
import dev.vimivo.desktop.library.DesktopPersonalMediaRepository
import dev.vimivo.desktop.library.ResumePosition
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LargeMediaLibraryOptimizationTest {

    @Test
    fun testBatchAllPositionsEliminatesNPlusOneQueriesAtScale() {
        val tempDir = Files.createTempDirectory("vimivo-positions-scale")
        val dbPath = tempDir.resolve("positions_bench.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val personalRepo = DesktopPersonalMediaRepository(database)
            val itemCount = 3_000

            // 1. Populate database with root, media items, and playback positions
            database.transaction { connection ->
                connection.createStatement().execute(
                    "INSERT INTO library_roots VALUES ('root-pos', 'file:///media', 'Positions Bench', 1000, 1000, 1)",
                )
                val insertMedia = connection.prepareStatement(
                    "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,1)",
                )
                val insertPos = connection.prepareStatement(
                    "INSERT INTO playback_positions(media_id,position_ms,duration_ms,completed,updated_at_ms) " +
                        "VALUES(?,?,?,?,?)",
                )

                for (i in 1..itemCount) {
                    val id = "track-$i"
                    insertMedia.setString(1, id)
                    insertMedia.setString(2, "root-pos")
                    insertMedia.setString(3, "file:///media/track_$i.mp3")
                    insertMedia.setString(4, "track_$i.mp3")
                    insertMedia.setString(5, "Track $i")
                    insertMedia.setString(6, "AUDIO")
                    insertMedia.setString(7, "audio/mpeg")
                    insertMedia.setLong(8, 5_000_000L)
                    insertMedia.setLong(9, 1_700_000_000_000L)
                    insertMedia.setLong(10, 1_700_000_000_000L)
                    insertMedia.addBatch()

                    insertPos.setString(1, id)
                    insertPos.setLong(2, (i * 1000L) % 200_000L)
                    insertPos.setLong(3, 240_000L)
                    insertPos.setInt(4, if (i % 5 == 0) 1 else 0)
                    insertPos.setLong(5, 1_700_000_000_000L + i)
                    insertPos.addBatch()

                    if (i % 500 == 0) {
                        insertMedia.executeBatch()
                        insertPos.executeBatch()
                    }
                }
                insertMedia.executeBatch()
                insertPos.executeBatch()
            }

            // 2. Measure Single Batch allPositions() retrieval
            var positionsMap: Map<String, ResumePosition>
            val batchTimeMs = measureTimeMillis {
                positionsMap = personalRepo.allPositions()
            }
            println("Batch allPositions() on $itemCount items took: ${batchTimeMs}ms (loaded ${positionsMap.size} positions)")

            assertEquals(itemCount, positionsMap.size)
            PerformanceBudget.check("Batch allPositions()", batchTimeMs, 100)

            // Verify accuracy of parsed positions
            val sample = positionsMap["track-42"]
            assertNotNull(sample)
            assertEquals(MediaId("track-42"), sample.mediaId)
            assertEquals(42_000L, sample.positionMillis)
            assertEquals(240_000L, sample.durationMillis)
        }
    }

    @Test
    fun testDesktopMediaCacheFastPathEliminatesRedundantImageEncoding() {
        val cacheDir = Files.createTempDirectory("vimivo-cache-bench")
        val cache = DesktopMediaCache(cacheDir)

        // Generate synthetic image
        val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.MAGENTA
        graphics.fillRect(0, 0, 128, 128)
        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        val imageBytes = output.toByteArray()

        val cacheId = "test-album-art-42"

        // 1. First store: requires scaling and disk write
        val firstStoreMs = measureTimeMillis {
            val result = cache.storeArtwork(cacheId, imageBytes)
            assertTrue(result.isSuccess)
        }
        println("First image store took: ${firstStoreMs}ms")

        // 2. Second store: hit fast-path (destination exists, size > 0)
        var cachedUri: String?
        val secondStoreMs = measureTimeMillis {
            val result = cache.storeArtwork(cacheId, imageBytes)
            assertTrue(result.isSuccess)
            cachedUri = result.getOrNull()
        }
        println("Second image store (fast-path hit) took: ${secondStoreMs}ms")

        assertNotNull(cachedUri)
        PerformanceBudget.check("Fast-path store", secondStoreMs, 5)
    }
}
