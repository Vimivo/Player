@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "ImportOrdering", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "NoUnusedImports", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.playlist.PlaylistM3uHandler
import dev.vimivo.core.playlist.PlaylistItemEntry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopPlaylistM3uTest {

    @Test
    fun exportsAndParsesM3uWithUnicodeAndBengaliMetadata() {
        val entries = listOf(
            PlaylistItemEntry(
                uri = "file:///music/amar_sonar_bangla.flac",
                title = "রবীন্দ্রনাথ ঠাকুর - আমার সোনার বাংলা",
                durationSeconds = 185,
            ),
            PlaylistItemEntry(
                uri = "file:///music/track2.mp3",
                title = "Artist - English Title",
                durationSeconds = 240,
            ),
        )

        val m3uContent = PlaylistM3uHandler.exportM3u8("বাংলা গান", entries)
        assertTrue(m3uContent.startsWith("#EXTM3U"))
        assertTrue(m3uContent.contains("#PLAYLIST:বাংলা গান"))
        assertTrue(m3uContent.contains("আমার সোনার বাংলা"))

        val parsed = PlaylistM3uHandler.parseM3u(m3uContent, "fallback_name")
        assertEquals("বাংলা গান", parsed.name)
        assertEquals(2, parsed.entries.size)
        assertEquals("file:///music/amar_sonar_bangla.flac", parsed.entries[0].uri)
        assertEquals("রবীন্দ্রনাথ ঠাকুর - আমার সোনার বাংলা", parsed.entries[0].title)
        assertEquals(185, parsed.entries[0].durationSeconds)
        assertEquals(240, parsed.entries[1].durationSeconds)
    }

    @Test
    fun matchesImportedM3uTracksAgainstIndexedDatabase() {
        val tempDir = Files.createTempDirectory("vimivo-m3u-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val personalRepo = DesktopPersonalMediaRepository(database)

            // Seed two media items
            database.transaction { connection ->
                connection.createStatement().execute(
                    "INSERT INTO library_roots(id,uri,display_name,added_at_ms) VALUES('root','file:///tmp/','Test',0)",
                )
                connection.prepareStatement(
                    "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,indexed_ms) VALUES(?,'root',?,?,?,'AUDIO',0)",
                ).use { stmt ->
                    stmt.setString(1, "media-1")
                    stmt.setString(2, "file:///tmp/track_one.flac")
                    stmt.setString(3, "track_one.flac")
                    stmt.setString(4, "Track One")
                    stmt.addBatch()

                    stmt.setString(1, "media-2")
                    stmt.setString(2, "file:///tmp/track_two.mp3")
                    stmt.setString(3, "track_two.mp3")
                    stmt.setString(4, "Track Two")
                    stmt.addBatch()

                    stmt.executeBatch()
                }
            }

            // Verify findMediaByUriOrFileName
            val foundByUri = libraryRepo.findMediaByUriOrFileName("file:///tmp/track_one.flac")
            assertNotNull(foundByUri)
            assertEquals("Track One", foundByUri.title)

            val foundByFileName = libraryRepo.findMediaByUriOrFileName("track_two.mp3")
            assertNotNull(foundByFileName)
            assertEquals("Track Two", foundByFileName.title)

            // Export playlist to M3U file
            val originalPlaylist = personalRepo.createPlaylist("Test Playlist")
            personalRepo.addToPlaylist(originalPlaylist.id, foundByUri.id)
            personalRepo.addToPlaylist(originalPlaylist.id, foundByFileName.id)

            val originalLoaded = personalRepo.playlists().single { it.id == originalPlaylist.id }
            val items = libraryRepo.media(originalLoaded.items)
            val entries = items.map { item ->
                PlaylistItemEntry(
                    uri = item.uri,
                    title = listOfNotNull(item.artist, item.title).joinToString(" - "),
                    durationSeconds = item.durationMillis?.let { (it / 1000).toInt() },
                )
            }
            val exportedContent = PlaylistM3uHandler.exportM3u8("Test Playlist", entries)
            val m3uFile = tempDir.resolve("exported.m3u8").toFile()
            m3uFile.writeText(exportedContent, Charsets.UTF_8)

            // Import M3U into new playlist
            val parsed = PlaylistM3uHandler.parseM3u(m3uFile.readText(Charsets.UTF_8), m3uFile.nameWithoutExtension)
            val importedPlaylist = personalRepo.createPlaylist(parsed.name)
            parsed.entries.forEach { entry ->
                val candidate = libraryRepo.findMediaByUriOrFileName(entry.uri)
                    ?: libraryRepo.findMediaByUriOrFileName(entry.uri.substringAfterLast('/').substringAfterLast('\\'))
                if (candidate != null) {
                    personalRepo.addToPlaylist(importedPlaylist.id, candidate.id)
                }
            }

            val reloaded = personalRepo.playlists().single { it.id == importedPlaylist.id }
            assertEquals("Test Playlist", reloaded.name)
            assertEquals(2, reloaded.items.size)
            assertEquals(listOf(foundByUri.id, foundByFileName.id), reloaded.items)
        }
    }

    @Test
    fun handlesMissingTracksGracefullyDuringImport() {
        val m3uText = """
            #EXTM3U
            #EXTINF:120,Missing Track
            /path/to/missing/file.mp3
        """.trimIndent()

        val parsed = PlaylistM3uHandler.parseM3u(m3uText, "Partial Playlist")
        assertEquals(1, parsed.entries.size)
        assertEquals("/path/to/missing/file.mp3", parsed.entries.first().uri)

        val tempDir = Files.createTempDirectory("vimivo-m3u-missing")
        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val candidate = libraryRepo.findMediaByUriOrFileName(parsed.entries.first().uri)
            assertEquals(null, candidate, "Candidate must be null when media not in database")
        }
    }
}
