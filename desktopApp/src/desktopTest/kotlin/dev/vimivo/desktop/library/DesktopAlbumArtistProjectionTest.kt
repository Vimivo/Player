@file:Suppress(
    "ArgumentListWrapping", "Indentation", "LongMethod", "MaximumLineLength", "MaxLineLength",
    "ParameterListWrapping", "PropertyWrapping", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAlbumArtistProjectionTest {
    @Test
    fun groupsAlbumsWithMultiDiscAndTrackOrdering() = runTest {
        val directory = Files.createTempDirectory("vimivo-album-test")
        val library = Files.createDirectories(directory.resolve("Music"))
        library.resolve("track1.mp3").writeText("synthetic 1")
        library.resolve("track2.mp3").writeText("synthetic 2")
        library.resolve("track3.mp3").writeText("synthetic 3")
        library.resolve("track4.mp3").writeText("synthetic 4")
        library.resolve("bonus.mp3").writeText("synthetic bonus")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val metadataRepo = DesktopMetadataRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            val media = repository.media(MediaKind.AUDIO).associateBy { it.fileName }

            metadataRepo.save(
                media.getValue("track1.mp3"),
                ExtractedMediaMetadata(title = "Intro", album = "Double Album", artist = "Lead Artist", discNumber = 1, trackNumber = 1, durationMillis = 60_000L),
            )
            metadataRepo.save(
                media.getValue("track2.mp3"),
                ExtractedMediaMetadata(title = "Song A", album = "Double Album", artist = "Lead Artist", discNumber = 1, trackNumber = 2, durationMillis = 180_000L),
            )
            metadataRepo.save(
                media.getValue("track3.mp3"),
                ExtractedMediaMetadata(title = "Interlude", album = "Double Album", artist = "Lead Artist", discNumber = 2, trackNumber = 1, durationMillis = 90_000L),
            )
            metadataRepo.save(
                media.getValue("track4.mp3"),
                ExtractedMediaMetadata(title = "Song B", album = "Double Album", artist = "Lead Artist", discNumber = 2, trackNumber = 2, durationMillis = 200_000L),
            )
            metadataRepo.save(
                media.getValue("bonus.mp3"),
                ExtractedMediaMetadata(title = "Bonus Track", album = "Double Album", artist = "Lead Artist", discNumber = null, trackNumber = null, durationMillis = 120_000L),
            )

            val albums = repository.albums()
            assertEquals(1, albums.size)
            val album = albums.single()
            assertEquals("Double Album", album.title)
            assertEquals("Lead Artist", album.artist)
            assertEquals(5, album.trackCount)
            assertEquals(650_000L, album.totalDurationMillis)

            val details = repository.albumDetails(album.id)
            assertNotNull(details)
            assertEquals(5, details.tracks.size)
            val titles = details.tracks.map { it.title }
            assertEquals(listOf("Intro", "Song A", "Interlude", "Song B", "Bonus Track"), titles)
        }
    }

    @Test
    fun handlesCompilationAlbumsGroupedUnderAlbumArtist() = runTest {
        val directory = Files.createTempDirectory("vimivo-compilation-test")
        val library = Files.createDirectories(directory.resolve("Music"))
        library.resolve("song1.mp3").writeText("synthetic 1")
        library.resolve("song2.mp3").writeText("synthetic 2")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val metadataRepo = DesktopMetadataRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            val media = repository.media(MediaKind.AUDIO).associateBy { it.fileName }

            metadataRepo.save(
                media.getValue("song1.mp3"),
                ExtractedMediaMetadata(title = "Hero", album = "Movie Soundtrack", artist = "Guest Singer 1", albumArtist = "Various Artists", trackNumber = 1),
            )
            metadataRepo.save(
                media.getValue("song2.mp3"),
                ExtractedMediaMetadata(title = "Villain", album = "Movie Soundtrack", artist = "Guest Singer 2", albumArtist = "Various Artists", trackNumber = 2),
            )

            val albums = repository.albums()
            assertEquals(1, albums.size)
            val album = albums.single()
            assertEquals("Movie Soundtrack", album.title)
            assertEquals("Various Artists", album.albumArtist)
            assertEquals(2, album.trackCount)
        }
    }

    @Test
    fun doesNotMergeDuplicateAlbumTitlesFromDifferentArtists() = runTest {
        val directory = Files.createTempDirectory("vimivo-dupe-test")
        val library = Files.createDirectories(directory.resolve("Music"))
        library.resolve("alpha.mp3").writeText("synthetic 1")
        library.resolve("beta.mp3").writeText("synthetic 2")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val metadataRepo = DesktopMetadataRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            val media = repository.media(MediaKind.AUDIO).associateBy { it.fileName }

            metadataRepo.save(
                media.getValue("alpha.mp3"),
                ExtractedMediaMetadata(title = "Hit 1", album = "Greatest Hits", artist = "Band Alpha"),
            )
            metadataRepo.save(
                media.getValue("beta.mp3"),
                ExtractedMediaMetadata(title = "Hit 2", album = "Greatest Hits", artist = "Band Beta"),
            )

            val albums = repository.albums()
            assertEquals(2, albums.size)
            assertTrue(albums.any { it.artist == "Band Alpha" })
            assertTrue(albums.any { it.artist == "Band Beta" })
            val ids = albums.map { it.id }.toSet()
            assertEquals(2, ids.size, "Album IDs must be stable and distinct")
        }
    }

    @Test
    fun handlesMissingTagsWithUnknownAlbumAndArtist() = runTest {
        val directory = Files.createTempDirectory("vimivo-missing-tags-test")
        val library = Files.createDirectories(directory.resolve("Music"))
        library.resolve("unknown.mp3").writeText("synthetic 1")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()

            val albums = repository.albums()
            assertEquals(1, albums.size)
            val album = albums.single()
            assertEquals("Unknown Album", album.title)
            assertNull(album.artist)

            val artists = repository.artists()
            assertEquals(1, artists.size)
            assertEquals("Unknown Artist", artists.single().name)
        }
    }

    @Test
    fun groupsArtistsWithAlbumsAndSongsAndPreservesUnicode() = runTest {
        val directory = Files.createTempDirectory("vimivo-artist-test")
        val library = Files.createDirectories(directory.resolve("Music"))
        library.resolve("bengali1.mp3").writeText("synthetic 1")
        library.resolve("bengali2.mp3").writeText("synthetic 2")
        library.resolve("other.mp3").writeText("synthetic 3")

        DesktopDatabase.open(directory.resolve("vimivo.sqlite")).use { database ->
            val repository = DesktopLibraryRepository(database)
            val metadataRepo = DesktopMetadataRepository(database)
            val root = repository.addRoot(library).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, library).getOrThrow()
            val media = repository.media(MediaKind.AUDIO).associateBy { it.fileName }

            metadataRepo.save(
                media.getValue("bengali1.mp3"),
                ExtractedMediaMetadata(title = "গান ১", album = "অ্যালবাম ১", artist = "শিল্পী অমুক"),
            )
            metadataRepo.save(
                media.getValue("bengali2.mp3"),
                ExtractedMediaMetadata(title = "গান ২", album = "অ্যালবাম ২", artist = "শিল্পী অমুক"),
            )
            metadataRepo.save(
                media.getValue("other.mp3"),
                ExtractedMediaMetadata(title = "Rock Song", album = "Rock Album", artist = "Rock Band"),
            )

            val artists = repository.artists()
            assertEquals(2, artists.size)
            val bengaliArtist = artists.first { it.name == "শিল্পী অমুক" }
            assertEquals(2, bengaliArtist.albumCount)
            assertEquals(2, bengaliArtist.trackCount)

            val details = repository.artistDetails(bengaliArtist.id)
            assertNotNull(details)
            assertEquals(2, details.albums.size)
            assertEquals(2, details.tracks.size)
        }
    }
}
