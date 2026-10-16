@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMissingMediaTest {

    @Test
    fun identifiesMissingMediaWhenFilesAreRemovedOrRelocated() {
        val tempDir = Files.createTempDirectory("vimivo-missing-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val relinker = DesktopMissingMediaRelinker(database)

            val existingFile = tempDir.resolve("present.mp3").toFile()
            existingFile.writeText("present audio")

            val missingFile = tempDir.resolve("ghost.mp3").toFile() // not created

            val presentItem = MediaItem(
                id = MediaId("present-1"),
                uri = existingFile.toURI().toString(),
                title = "Present Track",
                kind = MediaKind.AUDIO,
            )
            val missingItem = MediaItem(
                id = MediaId("missing-1"),
                uri = missingFile.toURI().toString(),
                title = "Ghost Track",
                kind = MediaKind.AUDIO,
            )

            val missingList = relinker.findMissingMedia(listOf(presentItem, missingItem))
            assertEquals(1, missingList.size)
            assertEquals(MediaId("missing-1"), missingList[0].id)
        }
    }

    @Test
    fun findsCandidatesByFilenameTitleAndSize() {
        val tempDir = Files.createTempDirectory("vimivo-candidates-test")
        val searchDir = tempDir.resolve("music_library").toFile()
        searchDir.mkdirs()

        val candidateFile1 = File(searchDir, "Lost Track.mp3")
        candidateFile1.writeText("1234567890")

        val candidateFile2 = File(searchDir, "New Name.mp3")
        candidateFile2.writeText("exact_length_bytes")

        DesktopDatabase.open(tempDir.resolve("vimivo.sqlite")).use { database ->
            val relinker = DesktopMissingMediaRelinker(database)

            val missingItem = MediaItem(
                id = MediaId("missing-candidate-1"),
                uri = "file:///Volumes/External/Lost%20Track.mp3",
                title = "Lost Track",
                fileName = "Lost Track.mp3",
                sizeBytes = 10L,
                kind = MediaKind.AUDIO,
            )

            val candidates = relinker.findCandidates(missingItem, listOf(searchDir))
            assertTrue(candidates.isNotEmpty())

            val exactMatch = candidates.find { it.candidateMedia.fileName == "Lost Track.mp3" }
            assertTrue(exactMatch != null)
            assertEquals("HIGH", exactMatch.confidence)
            assertTrue(exactMatch.reason.contains("Exact filename match"))
        }
    }

    @Test
    fun relinksMediaRecordUpdatingLocationAndPreservingMediaIdentity() {
        val tempDir = Files.createTempDirectory("vimivo-relink-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val relinker = DesktopMissingMediaRelinker(database)
            val mediaId = MediaId("relinked-media-1")

            // Seed media_items and a playlist referencing this item
            database.transaction { conn ->
                conn.createStatement().execute("INSERT INTO library_roots(id, uri, display_name, added_at_ms) VALUES ('root-1', 'file:///tmp/', 'Test', 0)")
                conn.prepareStatement("INSERT INTO media_items(id, root_id, uri, file_name, title, kind, indexed_ms, available) VALUES (?, 'root-1', 'file:///old/path.mp3', 'path.mp3', 'My Track', 'AUDIO', 0, 0)").use { stmt ->
                    stmt.setString(1, mediaId.value)
                    stmt.executeUpdate()
                }
                conn.createStatement().execute("INSERT INTO playlists(id, name, created_at_ms, updated_at_ms) VALUES ('pl-1', 'Favorites', 0, 0)")
                conn.createStatement().execute("INSERT INTO playlist_items(playlist_id, media_id, position, added_at_ms) VALUES ('pl-1', '${mediaId.value}', 0, 0)")
            }

            val newFile = tempDir.resolve("relocated_track.mp3").toFile()
            newFile.writeText("audio payload")

            val result = relinker.relink(mediaId, newFile)
            assertTrue(result.isSuccess)

            // Verify media_items updated
            database.transaction { conn ->
                conn.prepareStatement("SELECT uri, file_name, available, size_bytes FROM media_items WHERE id=?").use { stmt ->
                    stmt.setString(1, mediaId.value)
                    stmt.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(newFile.toURI().toString(), rows.getString(1))
                        assertEquals("relocated_track.mp3", rows.getString(2))
                        assertEquals(1, rows.getInt(3))
                        assertEquals(newFile.length(), rows.getLong(4))
                    }
                }
                // Playlist reference is completely intact!
                conn.createStatement().executeQuery("SELECT media_id FROM playlist_items WHERE playlist_id='pl-1'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(mediaId.value, rows.getString(1))
                    assertFalse(rows.next())
                }
            }
        }
    }
}
