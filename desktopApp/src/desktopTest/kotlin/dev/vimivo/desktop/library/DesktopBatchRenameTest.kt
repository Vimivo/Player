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

class DesktopBatchRenameTest {

    @Test
    fun expandsTemplateTokensAndSanitizesForbiddenCharacters() {
        val tempDir = Files.createTempDirectory("vimivo-rename-template-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val organizer = DesktopBatchRenameOrganizer(database)

            val file = tempDir.resolve("track1.mp3").toFile()
            file.writeText("audio bytes")

            val item = MediaItem(
                id = MediaId("song-1"),
                uri = file.toURI().toString(),
                title = "AC/DC: Thunder? <Live>",
                artist = "AC/DC",
                album = "Live *Special*",
                trackNumber = 2,
                releaseYear = 1992,
                kind = MediaKind.AUDIO,
                fileName = "track1.mp3",
            )

            val plans = organizer.evaluateRename(
                listOf(item),
                "{track} - {artist} - {title} ({year})",
            )

            assertEquals(1, plans.size)
            val plan = plans[0]
            assertFalse(plan.conflict)

            // Track number padded to 2 digits: "02"
            // AC/DC sanitized to AC_DC
            // Colons, question marks, angle brackets, asterisks sanitized to underscores
            assertEquals("02 - AC_DC - AC_DC_ Thunder_ _Live_ (1992).mp3", plan.proposedFileName)
        }
    }

    @Test
    fun flagsWindowsReservedDeviceNamesAsConflicts() {
        val tempDir = Files.createTempDirectory("vimivo-reserved-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val organizer = DesktopBatchRenameOrganizer(database)

            val file = tempDir.resolve("file.mp3").toFile()
            file.writeText("audio")

            val reservedTitles = listOf("CON", "PRN", "AUX", "NUL", "COM1", "LPT1")
            reservedTitles.forEach { reserved ->
                val item = MediaItem(
                    id = MediaId("id-$reserved"),
                    uri = file.toURI().toString(),
                    title = reserved,
                    kind = MediaKind.AUDIO,
                )
                val plans = organizer.evaluateRename(listOf(item), "{title}")
                assertTrue(plans[0].conflict, "Title '$reserved' must be flagged as conflict")
                assertEquals("Filename uses a reserved system name", plans[0].conflictReason)
            }
        }
    }

    @Test
    fun detectsCollisionsInBatchAndOnDisk() {
        val tempDir = Files.createTempDirectory("vimivo-collision-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val organizer = DesktopBatchRenameOrganizer(database)

            val file1 = tempDir.resolve("songA.mp3").toFile()
            file1.writeText("audio 1")
            val file2 = tempDir.resolve("songB.mp3").toFile()
            file2.writeText("audio 2")

            // Existing file on disk that neither item is currently named
            val existing = tempDir.resolve("Duplicate Destination.mp3").toFile()
            existing.writeText("existing")

            val item1 = MediaItem(
                id = MediaId("song-1"),
                uri = file1.toURI().toString(),
                title = "Duplicate Destination",
                kind = MediaKind.AUDIO,
            )
            val item2 = MediaItem(
                id = MediaId("song-2"),
                uri = file2.toURI().toString(),
                title = "Duplicate Destination",
                kind = MediaKind.AUDIO,
            )

            val plans = organizer.evaluateRename(listOf(item1, item2), "{title}")
            assertEquals(2, plans.size)

            // item1 collides with existing file on disk
            assertTrue(plans[0].conflict)
            assertEquals("Destination file already exists on disk", plans[0].conflictReason)

            // item2 collides with item1's planned name or disk
            assertTrue(plans[1].conflict)
        }
    }

    @Test
    fun executesSafeRenameAndReconcilesDatabaseRecord() {
        val tempDir = Files.createTempDirectory("vimivo-execute-rename-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val organizer = DesktopBatchRenameOrganizer(database)

            val sourceFile = tempDir.resolve("original_song.mp3").toFile()
            sourceFile.writeText("audio data")

            val mediaId = MediaId("song-reconcile-1")
            // Seed media_items
            database.transaction { conn ->
                conn.createStatement().execute("INSERT INTO library_roots(id, uri, display_name, added_at_ms) VALUES ('root-1', 'file:///tmp/', 'Test', 0)")
                conn.prepareStatement("INSERT INTO media_items(id, root_id, uri, file_name, title, kind, indexed_ms) VALUES (?, 'root-1', ?, ?, 'Song Title', 'AUDIO', 0)").use { stmt ->
                    stmt.setString(1, mediaId.value)
                    stmt.setString(2, sourceFile.toURI().toString())
                    stmt.setString(3, sourceFile.name)
                    stmt.executeUpdate()
                }
            }

            val item = MediaItem(
                id = mediaId,
                uri = sourceFile.toURI().toString(),
                title = "Polished Track",
                kind = MediaKind.AUDIO,
                fileName = sourceFile.name,
            )

            val plans = organizer.evaluateRename(listOf(item), "{title}")
            assertFalse(plans[0].conflict)

            val result = organizer.executeRename(plans)
            assertEquals(listOf(mediaId), result.succeeded)
            assertTrue(result.failed.isEmpty())

            // File on disk was renamed
            assertFalse(sourceFile.exists())
            val targetFile = File(tempDir.toFile(), "Polished Track.mp3")
            assertTrue(targetFile.exists())

            // Database was reconciled
            database.transaction { conn ->
                conn.prepareStatement("SELECT uri, file_name FROM media_items WHERE id=?").use { stmt ->
                    stmt.setString(1, mediaId.value)
                    stmt.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(targetFile.toURI().toString(), rows.getString(1))
                        assertEquals("Polished Track.mp3", rows.getString(2))
                    }
                }
            }
        }
    }
}
