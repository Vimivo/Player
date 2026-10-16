@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.SmartRuleCondition
import dev.vimivo.core.model.SmartRuleField
import dev.vimivo.core.model.SmartRuleMatchMode
import dev.vimivo.core.model.SmartRuleOperator
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSmartPlaylistTest {

    @Test
    fun persistsAndEvaluatesSmartPlaylistsWithConditions() {
        val tempDir = Files.createTempDirectory("vimivo-smart-pl-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopSmartPlaylistRepository(database)

            val playlist = repository.createSmartPlaylist(
                name = "Rock Classics",
                conditions = listOf(
                    SmartRuleCondition(SmartRuleField.GENRE, SmartRuleOperator.EQUALS, "Rock"),
                    SmartRuleCondition(SmartRuleField.RELEASE_YEAR, SmartRuleOperator.LESS_THAN, "2000"),
                ),
                matchMode = SmartRuleMatchMode.ALL,
                sortField = SmartRuleField.RELEASE_YEAR,
                sortDescending = true,
                limit = 10,
            )

            val loaded = repository.smartPlaylists()
            assertEquals(1, loaded.size)
            assertEquals("Rock Classics", loaded[0].name)
            assertEquals(2, loaded[0].conditions.size)

            val items = listOf(
                MediaItem(
                    id = MediaId("item-1"),
                    uri = "file:///song1.mp3",
                    title = "Old Rock",
                    genre = "Rock",
                    releaseYear = 1975,
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("item-2"),
                    uri = "file:///song2.mp3",
                    title = "Modern Rock",
                    genre = "Rock",
                    releaseYear = 2020,
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("item-3"),
                    uri = "file:///song3.mp3",
                    title = "Old Pop",
                    genre = "Pop",
                    releaseYear = 1980,
                    kind = MediaKind.AUDIO,
                ),
            )

            // ALL conditions: only Old Rock matches (Rock AND < 2000)
            val matched = repository.evaluate(playlist, items)
            assertEquals(1, matched.size)
            assertEquals(MediaId("item-1"), matched[0].id)
        }
    }

    @Test
    fun evaluatesAnyMatchModeAndAppliesLimits() {
        val tempDir = Files.createTempDirectory("vimivo-smart-any-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopSmartPlaylistRepository(database)

            val playlist = repository.createSmartPlaylist(
                name = "Favorites or Rock",
                conditions = listOf(
                    SmartRuleCondition(SmartRuleField.GENRE, SmartRuleOperator.EQUALS, "Rock"),
                    SmartRuleCondition(SmartRuleField.TITLE, SmartRuleOperator.CONTAINS, "Special"),
                ),
                matchMode = SmartRuleMatchMode.ANY,
                limit = 2,
            )

            val items = listOf(
                MediaItem(
                    id = MediaId("i-1"),
                    uri = "file:///1.mp3",
                    title = "Rock Anthem",
                    genre = "Rock",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("i-2"),
                    uri = "file:///2.mp3",
                    title = "Special Pop",
                    genre = "Pop",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("i-3"),
                    uri = "file:///3.mp3",
                    title = "Special Jazz",
                    genre = "Jazz",
                    kind = MediaKind.AUDIO,
                ),
                MediaItem(
                    id = MediaId("i-4"),
                    uri = "file:///4.mp3",
                    title = "Ordinary Electronic",
                    genre = "Electronic",
                    kind = MediaKind.AUDIO,
                ),
            )

            val matched = repository.evaluate(playlist, items)
            // 3 items match (i-1 has genre Rock, i-2 and i-3 have title Special), but limit is 2
            assertEquals(2, matched.size)
        }
    }

    @Test
    fun deletesSmartPlaylistSuccessfully() {
        val tempDir = Files.createTempDirectory("vimivo-smart-del-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repository = DesktopSmartPlaylistRepository(database)
            val pl = repository.createSmartPlaylist("Disposable", emptyList())
            assertEquals(1, repository.smartPlaylists().size)

            repository.deleteSmartPlaylist(pl.id)
            assertTrue(repository.smartPlaylists().isEmpty())
        }
    }
}
