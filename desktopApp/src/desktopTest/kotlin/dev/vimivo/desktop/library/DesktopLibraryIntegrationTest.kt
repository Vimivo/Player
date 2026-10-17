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
            val ordered = repository.media(
                listOf(repository.media(MediaKind.VIDEO).single().id, repository.media(MediaKind.AUDIO).single().id),
            )
            assertEquals(listOf(MediaKind.VIDEO, MediaKind.AUDIO), ordered.map { it.kind })
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
                    artworkUri = "file:///tmp/artwork.jpg",
                ),
            )

            val media = repository.media(MediaKind.AUDIO, "Vimivo Artist").single()
            assertEquals("Verified Title", media.title)
            assertEquals("Vimivo Artist", media.artist)
            assertEquals("Desktop Album", media.album)
            assertEquals(12_345, media.durationMillis)
            assertEquals(1_411_200, media.bitrate)
            assertEquals(3, media.trackNumber)
            assertEquals("file:///tmp/artwork.jpg", media.artworkUri)
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

    @Test
    fun userMetadataOverridesArePreservedAcrossAllProjectionsAndQueries() = runTest {
        val directory = Files.createTempDirectory("vimivo-overrides-consistency-test")
        val library = Files.createDirectories(directory.resolve("Music"))
        val song = library.resolve("raw_song.mp3")
        song.writeText("audio payload")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()

            val rawItem = repository.media(MediaKind.AUDIO).single()
            assertEquals("raw_song", rawItem.title)

            // Insert user metadata overrides and artwork override
            database.transaction { conn ->
                conn.createStatement().execute(
                    "INSERT INTO user_metadata_overrides(" +
                        "media_id, title, artist, album, album_artist, genre, track_number, updated_at_ms) " +
                        "VALUES ('${rawItem.id.value}', 'Overridden Title', 'Custom Artist', " +
                        "'Remastered Album', 'Custom Artist', 'Acoustic', 7, 5000)",
                )
                conn.createStatement().execute(
                    "INSERT INTO user_artwork_overrides(media_id, artwork_uri, updated_at_ms) " +
                        "VALUES ('${rawItem.id.value}', 'file:///custom/art.png', 5000)",
                )
            }
            repository.invalidate()

            // 1. media(kind, query)
            val byKind = repository.media(MediaKind.AUDIO).single()
            assertEquals("Overridden Title", byKind.title)
            assertEquals("Custom Artist", byKind.artist)
            assertEquals("Remastered Album", byKind.album)
            assertEquals("file:///custom/art.png", byKind.artworkUri)

            // 2. media(ids) lookup (used by queue and playlist details)
            val byId = repository.media(listOf(rawItem.id)).single()
            assertEquals("Overridden Title", byId.title)
            assertEquals("Custom Artist", byId.artist)
            assertEquals("Remastered Album", byId.album)
            assertEquals("file:///custom/art.png", byId.artworkUri)

            // 3. findMediaByUriOrFileName
            val byUri = repository.findMediaByUriOrFileName(rawItem.uri)
            assertEquals("Overridden Title", byUri?.title)
            assertEquals("Custom Artist", byUri?.artist)

            // 4. albums() projection
            val albumList = repository.albums()
            assertEquals(1, albumList.size)
            assertEquals("Remastered Album", albumList[0].title)
            assertEquals("Custom Artist", albumList[0].artist)

            // 5. albumDetails() lookup
            val albumDet = repository.albumDetails(albumList[0].id)
            assertEquals(1, albumDet?.tracks?.size)
            assertEquals("Overridden Title", albumDet?.tracks?.get(0)?.title)

            // 6. artists() projection
            val artistList = repository.artists()
            assertEquals(1, artistList.size)
            assertEquals("Custom Artist", artistList[0].name)

            // 7. artistDetails() lookup
            val artistDet = repository.artistDetails(artistList[0].id)
            assertEquals(1, artistDet?.tracks?.size)
            assertEquals("Overridden Title", artistDet?.tracks?.get(0)?.title)
        }
    }
}
