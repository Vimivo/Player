package dev.vimivo.desktop.library

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
}
