@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSearchUnicodeBengaliTest {

    @Test
    fun normalizesUnicodeComposedAndDecomposedEquivalents() {
        // e + combining acute accent (\u0065\u0301) vs precomposed é (\u00E9)
        val decomposed = "caf\u0065\u0301"
        val composed = "caf\u00E9"

        val normDecomposed = DesktopLibraryRepository.normalizeForSearch(decomposed)
        val normComposed = DesktopLibraryRepository.normalizeForSearch(composed)

        assertEquals(normComposed, normDecomposed)
        assertEquals("café", normComposed)
    }

    @Test
    fun scoresRankedMatchesCorrectlyForExactPrefixAndSubstringMatches() {
        val item1 = MediaItem(
            id = MediaId("1"),
            uri = "file:///1.mp3",
            title = "Bengali Song",
            artist = "Rabindranath Tagore",
            kind = MediaKind.AUDIO,
        )
        val item2 = MediaItem(
            id = MediaId("2"),
            uri = "file:///2.mp3",
            title = "Bengali Anthem",
            artist = "Tagore",
            kind = MediaKind.AUDIO,
        )
        val item3 = MediaItem(
            id = MediaId("3"),
            uri = "file:///3.mp3",
            title = "Another Track with Bengali Song inside",
            artist = "Tagore",
            kind = MediaKind.AUDIO,
        )

        // Exact title match -> 100
        assertEquals(100, DesktopLibraryRepository.scoreSearchMatch(item1, "Bengali Song"))
        // Title startsWith -> 80
        assertEquals(80, DesktopLibraryRepository.scoreSearchMatch(item1, "Bengali"))
        // Title contains -> 60
        assertEquals(60, DesktopLibraryRepository.scoreSearchMatch(item3, "Bengali Song"))
        // Artist match -> 50
        assertEquals(50, DesktopLibraryRepository.scoreSearchMatch(item2, "Tagore"))
        // Non-match -> 0
        assertEquals(0, DesktopLibraryRepository.scoreSearchMatch(item1, "Unrelated"))
    }

    @Test
    fun searchesAndRanksBengaliScriptMediaInDatabase() {
        val tempDir = Files.createTempDirectory("vimivo-bengali-search-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopLibraryRepository(database)

            // Seed media items with Bengali titles and artists
            database.transaction { conn ->
                conn.createStatement().execute("INSERT INTO library_roots(id, uri, display_name, added_at_ms) VALUES ('root-1', 'file:///music/', 'Music', 0)")
                val sql = "INSERT INTO media_items(id, root_id, uri, file_name, title, kind, indexed_ms) VALUES (?, 'root-1', ?, ?, ?, 'AUDIO', 0)"
                conn.prepareStatement(sql).use { stmt ->
                    // 1: তুমি রবে নীরবে
                    stmt.setString(1, "b-1")
                    stmt.setString(2, "file:///music/tumi_robe.mp3")
                    stmt.setString(3, "tumi_robe.mp3")
                    stmt.setString(4, "তুমি রবে নীরবে")
                    stmt.addBatch()

                    // 2: আমার সোনার বাংলা
                    stmt.setString(1, "b-2")
                    stmt.setString(2, "file:///music/sonar_bangla.mp3")
                    stmt.setString(3, "sonar_bangla.mp3")
                    stmt.setString(4, "আমার সোনার বাংলা")
                    stmt.addBatch()

                    // 3: মেঘের কোলে রোদ হেসেছে
                    stmt.setString(1, "b-3")
                    stmt.setString(2, "file:///music/megher_kole.mp3")
                    stmt.setString(3, "megher_kole.mp3")
                    stmt.setString(4, "মেঘের কোলে রোদ হেসেছে")
                    stmt.addBatch()

                    stmt.executeBatch()
                }

                // Add metadata for artist: রবীন্দ্রনাথ ঠাকুর
                val metaSql = "INSERT INTO media_metadata(media_id, title, artist, album, updated_at_ms) VALUES (?, ?, ?, ?, ?)"
                conn.prepareStatement(metaSql).use { stmt ->
                    stmt.setString(1, "b-1")
                    stmt.setString(2, "তুমি রবে নীরবে")
                    stmt.setString(3, "রবীন্দ্রনাথ ঠাকুর")
                    stmt.setString(4, "গীতবিতান")
                    stmt.setLong(5, 1000L)
                    stmt.addBatch()

                    stmt.setString(1, "b-2")
                    stmt.setString(2, "আমার সোনার বাংলা")
                    stmt.setString(3, "রবীন্দ্রনাথ ঠাকুর")
                    stmt.setString(4, "গীতবিতান")
                    stmt.setLong(5, 1000L)
                    stmt.addBatch()

                    stmt.executeBatch()
                }
            }

            // Search by Bengali title: 'সোনার'
            val titleResults = repository.media(MediaKind.AUDIO, query = "সোনার")
            assertEquals(1, titleResults.size)
            assertEquals("আমার সোনার বাংলা", titleResults[0].title)

            // Search by Bengali artist: 'রবীন্দ্রনাথ ঠাকুর'
            val artistResults = repository.media(MediaKind.AUDIO, query = "রবীন্দ্রনাথ ঠাকুর")
            assertEquals(2, artistResults.size)
            val titles = artistResults.map { it.title }.toSet()
            assertTrue(titles.contains("তুমি রবে নীরবে"))
            assertTrue(titles.contains("আমার সোনার বাংলা"))
        }
    }
}
