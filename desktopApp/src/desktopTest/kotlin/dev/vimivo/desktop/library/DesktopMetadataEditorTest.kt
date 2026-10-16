@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.FieldAction
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MetadataChangeset
import dev.vimivo.core.model.MetadataWriteMode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMetadataEditorTest {

    @Test
    fun persistsAndClearsMetadataOverridesInDatabase() {
        val tempDir = Files.createTempDirectory("vimivo-meta-override-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val service = DesktopMetadataEditorService(database)
            val mediaId = MediaId("song-123")
            seedMediaItem(database, mediaId)

            // Initial: no override
            assertNull(service.getOverride(mediaId))

            // Save override
            service.saveOverride(
                mediaId = mediaId,
                title = "Custom Title",
                artist = "Custom Artist",
                album = "Custom Album",
                albumArtist = "Custom Album Artist",
                genre = "Progressive Rock",
                releaseYear = 2024,
                trackNumber = 3,
                discNumber = 1,
            )

            // Verify retrieved values
            val override = service.getOverride(mediaId)
            assertNotNull(override)
            assertEquals("Custom Title", override.title)
            assertEquals("Custom Artist", override.artist)
            assertEquals("Custom Album", override.album)
            assertEquals("Custom Album Artist", override.albumArtist)
            assertEquals("Progressive Rock", override.genre)
            assertEquals(2024, override.releaseYear)
            assertEquals(3, override.trackNumber)
            assertEquals(1, override.discNumber)

            // Update partial override
            service.saveOverride(
                mediaId = mediaId,
                title = "Updated Title",
                genre = "Classical",
            )
            val updated = service.getOverride(mediaId)
            assertNotNull(updated)
            assertEquals("Updated Title", updated.title)
            assertEquals("Classical", updated.genre)

            // Clear override
            service.clearOverride(mediaId)
            assertNull(service.getOverride(mediaId))
        }
    }

    @Test
    fun persistsAndClearsArtworkOverridesInDatabase() {
        val tempDir = Files.createTempDirectory("vimivo-art-override-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val service = DesktopMetadataEditorService(database)
            val mediaId = MediaId("art-song-1")
            seedMediaItem(database, mediaId)

            assertNull(service.getArtworkOverride(mediaId))

            val artUri = "file:///Users/music/custom_cover.jpg"
            service.saveArtworkOverride(mediaId, artUri)
            assertEquals(artUri, service.getArtworkOverride(mediaId))

            service.clearArtworkOverride(mediaId)
            assertNull(service.getArtworkOverride(mediaId))
        }
    }

    @Test
    fun accuratelyDetectsSupportedFormatsForEmbeddedAudioTagWriting() {
        val tempDir = Files.createTempDirectory("vimivo-ext-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val service = DesktopMetadataEditorService(database)

            assertTrue(service.isFormatSupportedForEmbeddedWrite(File("song.mp3")))
            assertTrue(service.isFormatSupportedForEmbeddedWrite(File("song.flac")))
            assertTrue(service.isFormatSupportedForEmbeddedWrite(File("song.ogg")))
            assertTrue(service.isFormatSupportedForEmbeddedWrite(File("song.m4a")))
            assertTrue(service.isFormatSupportedForEmbeddedWrite(File("song.wav")))

            // Video formats or unsupported audio must return false
            assertFalse(service.isFormatSupportedForEmbeddedWrite(File("video.mkv")))
            assertFalse(service.isFormatSupportedForEmbeddedWrite(File("video.mp4")))
            assertFalse(service.isFormatSupportedForEmbeddedWrite(File("movie.avi")))
            assertFalse(service.isFormatSupportedForEmbeddedWrite(File("document.pdf")))
        }
    }

    @Test
    fun plansEditsDistinguishingOverrideAndEmbeddedModes() {
        val tempDir = Files.createTempDirectory("vimivo-plan-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val service = DesktopMetadataEditorService(database)

            // Create temporary test files
            val mp3File = tempDir.resolve("track.mp3").toFile()
            mp3File.writeText("fake mp3 content")

            val mkvFile = tempDir.resolve("movie.mkv").toFile()
            mkvFile.writeText("fake mkv content")

            val items = listOf(
                MediaItem(
                    id = MediaId("item-mp3"),
                    uri = mp3File.toURI().toString(),
                    title = "Original MP3",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("item-mkv"),
                    uri = mkvFile.toURI().toString(),
                    title = "Original MKV",
                    kind = MediaKind.VIDEO,
                ),
            )

            val changeset = MetadataChangeset(
                genre = FieldAction.Set("Synthwave"),
            )

            // In DATABASE_OVERRIDE mode, all files are ready
            val overridePlans = service.planEdits(items, changeset, MetadataWriteMode.DATABASE_OVERRIDE)
            assertEquals(2, overridePlans.size)
            assertEquals("Ready", overridePlans[0].statusMessage)
            assertEquals("Ready", overridePlans[1].statusMessage)

            // In EMBEDDED mode, MP3 is supported, MKV is flagged
            val embeddedPlans = service.planEdits(items, changeset, MetadataWriteMode.EMBEDDED_FILE_TAG)
            assertEquals(2, embeddedPlans.size)
            assertTrue(embeddedPlans[0].isSupportedFormat)
            assertEquals("Ready", embeddedPlans[0].statusMessage)

            assertFalse(embeddedPlans[1].isSupportedFormat)
            assertTrue(embeddedPlans[1].statusMessage.contains("Format not supported for embedded writing"))
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
