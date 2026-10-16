@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MagicNumber", "LongMethod",
)

package dev.vimivo.desktop.performance

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopLibraryRepository
import dev.vimivo.desktop.library.DesktopPlaybackQueueRepository
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.QueueItem
import dev.vimivo.playback.QueueRepeatMode
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LargeMediaLibraryBenchmarkTest {

    @Test
    fun benchmark100MediaLibrary() {
        runBenchmark(size = 100, maxQueryMs = 30, maxAlbumMs = 50)
    }

    @Test
    fun benchmark1000MediaLibrary() {
        runBenchmark(size = 1_000, maxQueryMs = 50, maxAlbumMs = 80)
    }

    @Test
    fun benchmark10000MediaLibraryScalability() {
        runBenchmark(size = 10_000, maxQueryMs = 100, maxAlbumMs = 250)
    }

    private fun runBenchmark(size: Int, maxQueryMs: Long, maxAlbumMs: Long) {
        val tempDir = Files.createTempDirectory("vimivo-bench-$size")
        val dbPath = tempDir.resolve("bench.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopLibraryRepository(database)
            val queueRepo = DesktopPlaybackQueueRepository(database)

            // 1. Measure Batch Insertion
            val rootId = "root-bench"
            val insertTimeMs = measureTimeMillis {
                database.transaction { connection ->
                    connection.createStatement().execute("INSERT INTO library_roots VALUES ('$rootId', 'file:///media', 'Bench Media', 1000, 1000, 1)")
                    val insertMedia = connection.prepareStatement(
                        "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,1)",
                    )
                    val insertMeta = connection.prepareStatement(
                        "INSERT INTO media_metadata(media_id,artist,album,album_artist,genre,track_number,duration_ms,updated_at_ms) " +
                            "VALUES(?,?,?,?,?,?,?,?)",
                    )

                    for (i in 1..size) {
                        val id = "media-$i"
                        val artistNum = (i % 50) + 1
                        val albumNum = (i % 200) + 1
                        val artistName = "Artist $artistNum"
                        val albumName = "Album $albumNum"

                        insertMedia.setString(1, id)
                        insertMedia.setString(2, rootId)
                        insertMedia.setString(3, "file:///media/track_$i.mp3")
                        insertMedia.setString(4, "track_$i.mp3")
                        insertMedia.setString(5, if (i % 10 == 0) "Symphony No. $i" else "Track $i")
                        insertMedia.setString(6, "AUDIO")
                        insertMedia.setString(7, "audio/mpeg")
                        insertMedia.setLong(8, 4_000_000L + i)
                        insertMedia.setLong(9, 1_700_000_000_000L)
                        insertMedia.setLong(10, 1_700_000_000_000L)
                        insertMedia.addBatch()

                        insertMeta.setString(1, id)
                        insertMeta.setString(2, artistName)
                        insertMeta.setString(3, albumName)
                        insertMeta.setString(4, artistName)
                        insertMeta.setString(5, "Classical")
                        insertMeta.setInt(6, (i % 12) + 1)
                        insertMeta.setLong(7, 240_000L)
                        insertMeta.setLong(8, 1_700_000_000_000L)
                        insertMeta.addBatch()

                        if (i % 500 == 0) {
                            insertMedia.executeBatch()
                            insertMeta.executeBatch()
                        }
                    }
                    insertMedia.executeBatch()
                    insertMeta.executeBatch()
                }
            }

            println("Benchmark [$size items] - Insertion Time: ${insertTimeMs}ms")
            assertTrue(insertTimeMs < 10_000, "Insertion of $size items took too long: ${insertTimeMs}ms")

            // 2. Measure Library Browsing (Unfiltered media query)
            var mediaList: List<MediaItem>
            val browseTimeMs = measureTimeMillis {
                mediaList = repository.media(MediaKind.AUDIO, query = "")
            }
            println("Benchmark [$size items] - Unfiltered Media Query: ${browseTimeMs}ms (returned ${mediaList.size} items)")
            assertTrue(browseTimeMs <= maxQueryMs, "Browsing query exceeded $maxQueryMs ms: ${browseTimeMs}ms")
            assertEquals(minOf(size, 500), mediaList.size)

            // 3. Measure Targeted Search Query
            var searchResults: List<MediaItem>
            val searchTimeMs = measureTimeMillis {
                searchResults = repository.media(MediaKind.AUDIO, query = "Symphony")
            }
            println("Benchmark [$size items] - Search Query ('Symphony'): ${searchTimeMs}ms (returned ${searchResults.size} items)")
            assertTrue(searchTimeMs <= maxQueryMs * 2, "Search query exceeded threshold: ${searchTimeMs}ms")

            // 4. Measure Album Aggregation
            var albums: List<dev.vimivo.core.model.AlbumItem>
            val albumTimeMs = measureTimeMillis {
                albums = repository.albums()
            }
            println("Benchmark [$size items] - Album Aggregation: ${albumTimeMs}ms (found ${albums.size} albums)")
            assertTrue(albumTimeMs <= maxAlbumMs, "Album aggregation exceeded $maxAlbumMs ms: ${albumTimeMs}ms")

            // 5. Measure Cached Album Details Lookup
            val sampleAlbum = albums.firstOrNull()
            assertNotNull(sampleAlbum)
            val albumDetailMs = measureTimeMillis {
                val details = repository.albumDetails(sampleAlbum.id)
                assertNotNull(details)
                assertTrue(details.tracks.isNotEmpty())
            }
            println("Benchmark [$size items] - Album Details: ${albumDetailMs}ms")
            assertTrue(albumDetailMs <= 50, "Album details lookup must be under 50ms: ${albumDetailMs}ms")

            // 6. Measure Artist Aggregation
            var artists: List<dev.vimivo.core.model.ArtistItem>
            val artistTimeMs = measureTimeMillis {
                artists = repository.artists()
            }
            println("Benchmark [$size items] - Artist Aggregation: ${artistTimeMs}ms (found ${artists.size} artists)")
            assertTrue(artistTimeMs <= maxAlbumMs, "Artist aggregation exceeded $maxAlbumMs ms: ${artistTimeMs}ms")

            // 7. Measure Queue Persistence & Restoration
            val sampleMedia = mediaList.take(minOf(size, 200))
            val queueItems = sampleMedia.mapIndexed { index, media -> QueueItem(index.toLong(), media) }
            val snapshot = PlaybackQueueSnapshot(
                sourceItems = queueItems,
                playbackItems = queueItems,
                currentQueueId = 0L,
                shuffleEnabled = false,
                repeatMode = QueueRepeatMode.ALL,
                sourceLabel = "Benchmark Playlist",
            )

            val queueSaveMs = measureTimeMillis {
                queueRepo.save(snapshot)
            }
            println("Benchmark [$size items] - Queue Save: ${queueSaveMs}ms")
            assertTrue(queueSaveMs <= 100, "Queue save took too long: ${queueSaveMs}ms")

            val queueLoadMs = measureTimeMillis {
                val loaded = queueRepo.load()
                assertNotNull(loaded)
                assertEquals(queueItems.size, loaded.items.size)
            }
            println("Benchmark [$size items] - Queue Load: ${queueLoadMs}ms")
            assertTrue(queueLoadMs <= 50, "Queue load took too long: ${queueLoadMs}ms")

            // 8. Database Size on Disk
            val fileSize = Files.size(dbPath)
            println("Benchmark [$size items] - SQLite DB File Size: ${fileSize / 1024} KB")
            assertTrue(fileSize > 0, "Database file must have non-zero size")
        }
    }
}
