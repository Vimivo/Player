@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.FieldAction
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MetadataWriteMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopMetadataCsvTest {

    @Test
    fun exportsCsvWithUnicodeBengaliAndFormulaInjectionProtection() {
        val tempDir = Files.createTempDirectory("vimivo-csv-export-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")
        val csvFile = tempDir.resolve("exported_metadata.csv").toFile()

        DesktopDatabase.open(dbPath).use { database ->
            val editorService = DesktopMetadataEditorService(database)
            val exporter = DesktopMetadataCsvExporterImporter(editorService)

            val items = listOf(
                MediaItem(
                    id = MediaId("song-1"),
                    uri = "file:///music/song1.mp3",
                    title = "মন মাঝি",
                    kind = MediaKind.AUDIO,
                    artist = "লালন ফকির",
                    album = "বাউল গান",
                    albumArtist = "লালন সাঁই",
                    genre = "Folk",
                    releaseYear = 2022,
                    trackNumber = 1,
                    discNumber = 1,
                    durationMillis = 180000,
                ),
                MediaItem(
                    id = MediaId("song-2"),
                    uri = "file:///music/song2.mp3",
                    title = "=1+2\"special,comma",
                    kind = MediaKind.AUDIO,
                    artist = "@FormulaArtist",
                    album = "Safe Album",
                ),
            )

            val res = exporter.exportCsv(items, csvFile, includePaths = true)
            assertTrue(res.isSuccess)
            assertTrue(csvFile.exists())

            val content = csvFile.readText()
            // Check BOM
            assertTrue(content.startsWith('\uFEFF'))
            // Check Bengali text
            assertTrue(content.contains("মন মাঝি"))
            assertTrue(content.contains("লালন ফকির"))
            // Check formula injection escaping
            assertTrue(content.contains("''=1+2\"\"special,comma\"") || content.contains("\"'=1+2\"\"special,comma\""))
            assertTrue(content.contains("'@FormulaArtist"))
        }
    }

    @Test
    fun previewsAndAppliesCsvImportToDatabaseOverrides() {
        val tempDir = Files.createTempDirectory("vimivo-csv-import-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")
        val csvFile = tempDir.resolve("import_metadata.csv").toFile()

        DesktopDatabase.open(dbPath).use { database ->
            val editorService = DesktopMetadataEditorService(database)
            val importer = DesktopMetadataCsvExporterImporter(editorService)

            val mediaId1 = MediaId("track-101")
            val mediaId2 = MediaId("track-102")
            seedMediaItem(database, mediaId1)
            seedMediaItem(database, mediaId2)

            val existingItems = listOf(
                MediaItem(
                    id = mediaId1,
                    uri = "file:///tmp/track-101.mp3",
                    title = "Original Title 101",
                    kind = MediaKind.AUDIO,
                    artist = "Old Artist",
                    album = "Old Album",
                ),
                MediaItem(
                    id = mediaId2,
                    uri = "file:///tmp/track-102.mp3",
                    title = "Original Title 102",
                    kind = MediaKind.AUDIO,
                ),
            )

            // Write CSV with edits for track-101, an unknown track-999, and track-102
            csvFile.writeText(
                """
                media_id,title,artist,album,album_artist,genre,release_year,track_number,disc_number
                track-101,নূতন গান,নূতন শিল্পী,নূতন অ্যালবাম,অ্যালবাম শিল্পী,লোকগীতি,2024,5,1
                track-999,Unmatched Track,Ghost Artist,,,,,,
                """.trimIndent(),
            )

            val previewResult = importer.previewImportCsv(csvFile, existingItems)
            assertTrue(previewResult.isSuccess)
            val plan = previewResult.getOrThrow()

            assertEquals(1, plan.matchedItems.size)
            assertEquals(1, plan.unmatchedRows.size)
            assertEquals("track-999", plan.unmatchedRows[0].mediaId)

            val (matchedItem, changeset) = plan.matchedItems[0]
            assertEquals(mediaId1, matchedItem.id)
            assertEquals(FieldAction.Set("নূতন গান"), changeset.title)
            assertEquals(FieldAction.Set("নূতন শিল্পী"), changeset.artist)
            assertEquals(FieldAction.Set("নূতন অ্যালবাম"), changeset.album)
            assertEquals(FieldAction.Set(2024), changeset.releaseYear)
            assertEquals(FieldAction.Set(5), changeset.trackNumber)

            // Apply import in DATABASE_OVERRIDE mode
            val applyResult = importer.applyImport(plan, MetadataWriteMode.DATABASE_OVERRIDE)
            assertEquals(1, applyResult.appliedCount)
            assertEquals(0, applyResult.failedItems.size)

            // Verify override in database
            val override = editorService.getOverride(mediaId1)
            assertNotNull(override)
            assertEquals("নূতন গান", override.title)
            assertEquals("নূতন শিল্পী", override.artist)
            assertEquals("নূতন অ্যালবাম", override.album)
            assertEquals(2024, override.releaseYear)
            assertEquals(5, override.trackNumber)
        }
    }

    private fun seedMediaItem(database: DesktopDatabase, mediaId: MediaId) {
        database.transaction { conn ->
            conn.createStatement().execute("INSERT OR IGNORE INTO library_roots(id, uri, display_name, added_at_ms) VALUES ('root-1', 'file:///tmp/', 'Test', 0)")
            conn.prepareStatement("INSERT OR IGNORE INTO media_items(id, root_id, uri, file_name, title, kind, indexed_ms) VALUES (?, 'root-1', ?, ?, ?, 'AUDIO', 0)").use { stmt ->
                stmt.setString(1, mediaId.value)
                stmt.setString(2, "file:///tmp/${mediaId.value}.mp3")
                stmt.setString(3, "${mediaId.value}.mp3")
                stmt.setString(4, mediaId.value)
                stmt.executeUpdate()
            }
        }
    }
}
