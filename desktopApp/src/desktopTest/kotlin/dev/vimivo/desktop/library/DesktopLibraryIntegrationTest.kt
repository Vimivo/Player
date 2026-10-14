package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLibraryIntegrationTest {
    @Test
    fun scansPersistsSearchesAndAvoidsDuplicates() = runTest {
        val directory = Files.createTempDirectory("vimivo-library-test")
        val library = Files.createDirectories(directory.resolve("Média Folder"))
        Files.createDirectories(library.resolve("Nested"))
        library.resolve("Song One.MP3").writeText("synthetic audio candidate")
        library.resolve("Nested").resolve("Movie One.mkv").writeText("synthetic video candidate")
        library.resolve("notes.txt").writeText("unsupported")
        val databasePath = directory.resolve("vimivo.sqlite")

        DesktopDatabase.open(databasePath).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            assertEquals(root.id, repository.addRoot(library).getOrThrow().id)
            val scanner = DesktopMediaScanner(database)
            assertEquals(2, scanner.scan(root.id, library).getOrThrow().indexed)
            assertEquals(1, repository.media(MediaKind.AUDIO).size)
            assertEquals("Movie One", repository.media(MediaKind.VIDEO, "movie").single().title)
            assertEquals(2, scanner.scan(root.id, library).getOrThrow().indexed)
            assertEquals(1, repository.media(MediaKind.AUDIO).size)
        }

        DesktopDatabase.open(databasePath).use { database ->
            val repository = DesktopLibraryRepository(database)
            assertEquals(1, repository.roots().size)
            assertTrue(repository.media(MediaKind.VIDEO).single().uri.contains("Movie%20One.mkv"))
        }
    }

    @Test
    fun exposesExtractedMetadataAndSearchesArtistAndAlbum() = runTest {
        val directory = Files.createTempDirectory("vimivo-metadata-query-test")
        val library = Files.createDirectories(directory.resolve("Library"))
        library.resolve("track.wav").writeText("synthetic audio candidate")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            val indexed = repository.media(MediaKind.AUDIO).single()
            DesktopMetadataRepository(database).save(
                indexed,
                ExtractedMediaMetadata(
                    title = "Verified Title",
                    artist = "Vimivo Artist",
                    album = "Desktop Album",
                    durationMillis = 12_345,
                    bitrate = 1_411_200,
                    sampleRate = 44_100,
                    channels = 2,
                    trackNumber = 3,
                ),
            )

            val media = repository.media(MediaKind.AUDIO, "Vimivo Artist").single()
            assertEquals("Verified Title", media.title)
            assertEquals("Vimivo Artist", media.artist)
            assertEquals("Desktop Album", media.album)
            assertEquals(12_345, media.durationMillis)
            assertEquals(1_411_200, media.bitrate)
            assertEquals(3, media.trackNumber)
            assertEquals(media, repository.media(MediaKind.AUDIO, "Desktop Album").single())
        }
    }

    @Test
    fun removalOnlyDeletesIndexRecords() = runTest {
        val directory = Files.createTempDirectory("vimivo-root-removal-test")
        val library = Files.createDirectories(directory.resolve("Library"))
        val original = library.resolve("keep-me.flac")
        original.writeText("synthetic audio candidate")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            assertEquals(1, repository.roots().single().mediaCount)
            repository.removeRoot(root.id).getOrThrow()
            assertTrue(repository.roots().isEmpty())
            assertTrue(repository.media(MediaKind.AUDIO).isEmpty())
            assertTrue(Files.exists(original))
            assertFalse(Files.isDirectory(directory.resolve("not-created")))
        }
    }

    @Test
    fun rescanMarksMissingMediaUnavailableWithoutDeletingItsRecord() = runTest {
        val directory = Files.createTempDirectory("vimivo-reconcile-test")
        val library = Files.createDirectories(directory.resolve("Library"))
        val original = library.resolve("removable.mp3")
        original.writeText("synthetic audio candidate")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            val scanner = DesktopMediaScanner(database)
            scanner.scan(root.id, library).getOrThrow()
            assertEquals(1, repository.media(MediaKind.AUDIO).size)

            Files.delete(original)
            scanner.scan(root.id, library).getOrThrow()

            assertTrue(repository.media(MediaKind.AUDIO).isEmpty())
            database.transaction { connection ->
                connection.prepareStatement("SELECT available FROM media_items WHERE root_id=?").use { statement ->
                    statement.setString(1, root.id)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(0, rows.getInt(1))
                    }
                }
            }
        }
    }
}
