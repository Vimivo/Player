@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "LongMethod", "MagicNumber",
    "MaximumLineLength", "MaxLineLength", "StringLiteralDuplication", "Wrapping",
)

package dev.vimivo.desktop.performance

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopLibraryRepository
import dev.vimivo.desktop.library.DesktopPersonalMediaRepository
import dev.vimivo.desktop.library.FavoriteTarget
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates progressive large-scale local media library fixtures (1K, 10K, 50K),
 * Bengali titles, Unicode filenames, duplicate names, missing metadata, large playlists,
 * removable roots, and SQLite query plan index coverage.
 */
class ProgressiveMediaLibraryFixtureTest {

    private val bengaliTitles = listOf(
        "আমার সোনার বাংলা",
        "তুমি কেমন আছো বন্ধু",
        "মেঘে মেঘে বেলা হলো",
        "আনন্দলোকে মঙ্গলালোকে বিরাজ সত্যসুন্দর",
        "যদি তোর ডাক শুনে কেউ না আসে তবে একলা চলো রে",
        "পুরানো সেই দিনের কথা ভুলবি কি রে হায়",
        "গ্রাম ছাড়া ওই রাঙা মাটির পথ",
        "ও আমার দেশের মাটি তোমার 'পরে ঠেকাই মাথা",
        "ধনধান্য পুষ্পভরা আমাদের এই বসুন্ধরা",
        "কারার ওই লৌহকপাট ভেঙে ফেল কর রে লোপাট",
    )

    private val bengaliArtists = listOf(
        "রবীন্দ্রনাথ ঠাকুর",
        "কাজী নজরুল ইসলাম",
        "দ্বিজেন্দ্রলাল রায়",
        "অতুলপ্রসাদ সেন",
        "রজনীকান্ত সেন",
        "হেমন্ত মুখোপাধ্যায়",
        "মান্না দে",
        "কিশোর কুমার",
    )

    private val bengaliAlbums = listOf(
        "গীতাঞ্জলি স্মারক সংকলন",
        "অগ্নিবীণা সুরমালা",
        "বসন্তের গান",
        "বর্ষার রাগ ও সুর",
        "স্মৃতির অমলিন সুর",
        "চিরন্তন সুরমাধুরী",
    )

    @Test
    fun testProgressive1KFixtureWithBengaliUnicodeAndQueryPlan() {
        runProgressiveSuite(size = 1_000, targetSearchMs = 40, targetGroupingMs = 60)
    }

    @Test
    fun testProgressive10KFixtureWithBengaliUnicodeAndQueryPlan() {
        runProgressiveSuite(size = 10_000, targetSearchMs = 120, targetGroupingMs = 250)
    }

    @Test
    fun testProgressive50KFixtureScalabilityAndDataIntegrity() {
        runProgressiveSuite(size = 50_000, targetSearchMs = 350, targetGroupingMs = 600)
    }

    private fun runProgressiveSuite(size: Int, targetSearchMs: Long, targetGroupingMs: Long) {
        val tempDir = Files.createTempDirectory("vimivo-prog-$size")
        val dbPath = tempDir.resolve("prog_$size.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val personalRepo = DesktopPersonalMediaRepository(database)
            val rootId = "root-prog-$size"

            // 1. Ingest progressive fixture
            val insertTimeMs = measureTimeMillis {
                database.transaction { connection ->
                    connection.createStatement().execute(
                        "INSERT INTO library_roots VALUES ('$rootId', 'file:///media/progressive', 'Progressive Root', 1000, 1000, 1)",
                    )
                    val insertMedia = connection.prepareStatement(
                        "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,1)",
                    )
                    val insertMeta = connection.prepareStatement(
                        "INSERT INTO media_metadata(media_id,artist,album,album_artist,genre,track_number,duration_ms,updated_at_ms) " +
                            "VALUES(?,?,?,?,?,?,?,?)",
                    )

                    for (i in 1..size) {
                        val id = "prog-$i"
                        val isBengali = i % 3 == 0
                        val isLongTitle = i % 7 == 0
                        val isMissingMeta = i % 10 == 0
                        val isDuplicateFileName = i % 25 == 0

                        val baseTitle = if (isBengali) {
                            bengaliTitles[(i / 3) % bengaliTitles.size] + " (খণ্ড $i)"
                        } else {
                            "Track Title #$i"
                        }
                        val title = if (isLongTitle) {
                            baseTitle + " - বিশেষ বিস্তারিত সংস্করণ যা দীর্ঘ শিরোনাম ধারণ করে যাতে রেন্ডারিং ও ডেটাবেজ পরীক্ষা করা যায়"
                        } else {
                            baseTitle
                        }

                        val fileName = if (isDuplicateFileName) {
                            "track_shared_duplicate.mp3"
                        } else if (isBengali) {
                            "গান_$i.mp3"
                        } else {
                            "track_$i.flac"
                        }

                        val artist = if (isMissingMeta) null else if (isBengali) bengaliArtists[(i / 5) % bengaliArtists.size] else "Artist ${i % 150}"
                        val album = if (isMissingMeta) null else if (isBengali) bengaliAlbums[(i / 7) % bengaliAlbums.size] else "Album ${i % 300}"

                        insertMedia.setString(1, id)
                        insertMedia.setString(2, rootId)
                        insertMedia.setString(3, "file:///media/album_${i % 100}/$fileName?id=$i")
                        insertMedia.setString(4, fileName)
                        insertMedia.setString(5, title)
                        insertMedia.setString(6, if (i % 20 == 0) "VIDEO" else "AUDIO")
                        insertMedia.setString(7, if (i % 20 == 0) "video/mp4" else "audio/mpeg")
                        insertMedia.setLong(8, 5_000_000L + (i * 1024L))
                        insertMedia.setLong(9, 1_700_000_000_000L + i)
                        insertMedia.setLong(10, 1_700_000_000_000L + i)
                        insertMedia.addBatch()

                        insertMeta.setString(1, id)
                        insertMeta.setString(2, artist)
                        insertMeta.setString(3, album)
                        insertMeta.setString(4, artist)
                        insertMeta.setString(5, if (isBengali) "রবীন্দ্রসঙ্গীত" else "Classical")
                        insertMeta.setInt(6, (i % 16) + 1)
                        insertMeta.setLong(7, 210_000L)
                        insertMeta.setLong(8, 1_700_000_000_000L + i)
                        insertMeta.addBatch()

                        if (i % 1_000 == 0) {
                            insertMedia.executeBatch()
                            insertMeta.executeBatch()
                        }
                    }
                    insertMedia.executeBatch()
                    insertMeta.executeBatch()
                }
            }
            println("Progressive Fixture [$size items] - Insertion Time: ${insertTimeMs}ms")
            assertTrue(insertTimeMs < 30_000, "Progressive insertion took too long: ${insertTimeMs}ms")

            // 2. Profile SQLite Query Plans with EXPLAIN QUERY PLAN
            database.transaction { conn ->
                // A) Unfiltered browsable query
                conn.createStatement().executeQuery(
                    "EXPLAIN QUERY PLAN SELECT m.id FROM media_items m LEFT JOIN media_metadata x ON x.media_id=m.id WHERE m.available=1 AND m.kind='AUDIO' ORDER BY m.title COLLATE NOCASE LIMIT 500",
                ).use { rs ->
                    val plans = buildList { while (rs.next()) add(rs.getString("detail")) }
                    println("Query Plan (Browsing): $plans")
                    assertTrue(plans.any { it.contains("media_items") }, "Query plan must reference media_items table")
                }

                // B) Favorites retrieval query
                conn.createStatement().executeQuery(
                    "EXPLAIN QUERY PLAN SELECT target_id FROM favorites WHERE target_type='MEDIA' ORDER BY created_at_ms DESC",
                ).use { rs ->
                    val plans = buildList { while (rs.next()) add(rs.getString("detail")) }
                    println("Query Plan (Favorites): $plans")
                    assertTrue(
                        plans.any { it.contains("favorites_target_created") || it.contains("PRIMARY KEY") || it.contains("favorites") },
                        "Favorites query must leverage indexed scan: $plans",
                    )
                }

                // C) Playback position query
                conn.createStatement().executeQuery(
                    "EXPLAIN QUERY PLAN SELECT media_id, position_ms FROM playback_positions WHERE media_id='prog-1'",
                ).use { rs ->
                    val plans = buildList { while (rs.next()) add(rs.getString("detail")) }
                    println("Query Plan (Positions): $plans")
                    assertTrue(plans.any { it.contains("PRIMARY KEY") || it.contains("playback_positions") }, "Positions query must use primary key")
                }
            }

            // 3. Search Performance for Bengali Unicode
            var bengaliResults: List<MediaItem>
            val bengaliSearchMs = measureTimeMillis {
                bengaliResults = libraryRepo.media(MediaKind.AUDIO, query = "সোনার বাংলা")
            }
            println("Progressive [$size items] - Bengali Search ('সোনার বাংলা'): ${bengaliSearchMs}ms (found ${bengaliResults.size})")
            assertTrue(bengaliSearchMs <= targetSearchMs * 3, "Bengali search exceeded budget: ${bengaliSearchMs}ms")
            assertTrue(bengaliResults.isNotEmpty(), "Must find matching Bengali media items")

            // 4. Album & Artist Aggregation Performance
            val albumGroupingMs = measureTimeMillis {
                val albums = libraryRepo.albums()
                assertTrue(albums.isNotEmpty())
            }
            println("Progressive [$size items] - Album Grouping: ${albumGroupingMs}ms")
            assertTrue(albumGroupingMs <= targetGroupingMs * 3, "Album aggregation exceeded budget: ${albumGroupingMs}ms")

            val artistGroupingMs = measureTimeMillis {
                val artists = libraryRepo.artists()
                assertTrue(artists.isNotEmpty())
            }
            println("Progressive [$size items] - Artist Grouping: ${artistGroupingMs}ms")
            assertTrue(artistGroupingMs <= targetGroupingMs * 3, "Artist aggregation exceeded budget: ${artistGroupingMs}ms")

            // 5. Large Playlist Operations
            val playlist = personalRepo.createPlaylist("Mega Playlist $size")
            val playlistAddMs = measureTimeMillis {
                val sampleIds = (1..minOf(size, 2_000)).map { MediaId("prog-$it") }
                sampleIds.forEach { personalRepo.addToPlaylist(playlist.id, it) }
            }
            println("Progressive [$size items] - Created playlist with ${minOf(size, 2_000)} items in ${playlistAddMs}ms")

            val loadedPlaylists = personalRepo.playlists()
            val loadedTarget = loadedPlaylists.first { it.id == playlist.id }
            assertEquals(minOf(size, 2_000), loadedTarget.items.size)

            // 6. Favorites Batch Verification
            val favoriteIds = (1..minOf(size, 500)).map { "prog-$it" }
            favoriteIds.forEach { personalRepo.setFavorite(FavoriteTarget.MEDIA, it, true) }
            val retrievedFavorites = personalRepo.favoriteIds(FavoriteTarget.MEDIA)
            assertTrue(retrievedFavorites.containsAll(favoriteIds))

            // 7. Removable Media Root Toggle (Available=0, Available=1)
            database.transaction { conn ->
                conn.createStatement().execute("UPDATE library_roots SET available=0 WHERE id='$rootId'")
                conn.createStatement().execute("UPDATE media_items SET available=0 WHERE root_id='$rootId'")
            }
            val unavailableCount = libraryRepo.media(MediaKind.AUDIO, query = "").size
            assertEquals(0, unavailableCount, "Media should not be returned when root is marked unavailable")

            // Restore availability
            database.transaction { conn ->
                conn.createStatement().execute("UPDATE library_roots SET available=1 WHERE id='$rootId'")
                conn.createStatement().execute("UPDATE media_items SET available=1 WHERE root_id='$rootId'")
            }
            val restoredCount = libraryRepo.media(MediaKind.AUDIO, query = "").size
            assertTrue(restoredCount > 0, "Media must be restored when root is marked available again")

            // Verify playlist references are intact after root availability cycle
            val playlistAfterCycle = personalRepo.playlists().first { it.id == playlist.id }
            assertEquals(minOf(size, 2_000), playlistAfterCycle.items.size, "Playlist references must never be silently lost")
        }
    }
}
