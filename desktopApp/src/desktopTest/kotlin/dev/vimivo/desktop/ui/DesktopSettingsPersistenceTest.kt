@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength", "MaxLineLength", "Wrapping",
)

package dev.vimivo.desktop.ui

import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopLibraryRepository
import dev.vimivo.desktop.library.DesktopMediaScanner
import dev.vimivo.desktop.library.DesktopPersonalMediaRepository
import dev.vimivo.desktop.library.FavoriteTarget
import dev.vimivo.settings.LibraryViewMode
import dev.vimivo.settings.RepeatBehavior
import dev.vimivo.settings.ShuffleBehavior
import dev.vimivo.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSettingsPersistenceTest {

    @Test
    fun testDefaultSettingsValues() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-settings-test-default")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val controller = DesktopLibraryController(database)
            val state = controller.state.first { !it.loading }

            assertEquals(ThemeMode.SYSTEM, state.themeMode)
            assertEquals(LibraryViewMode.LIST, state.libraryViewMode)
            assertEquals(LibrarySort.TITLE, state.defaultSort)
            assertFalse(state.reduceAnimations)
            assertFalse(state.highContrast)
            assertFalse(state.reduceTransparency)
            assertTrue(state.resumePlayback)
            assertEquals(1.0f, state.defaultSpeed)
            assertEquals(RepeatBehavior.REMEMBER, state.repeatBehavior)
            assertEquals(ShuffleBehavior.REMEMBER, state.shuffleBehavior)

            controller.close()
        }
    }

    @Test
    fun testSettingsMutationAndImmediatePersistence() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-settings-test-mutate")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val controller = DesktopLibraryController(database)
            controller.state.first { !it.loading }

            // Update settings through controller and join background persistence
            controller.setThemeMode(ThemeMode.AMOLED).join()
            controller.setLibraryViewMode(LibraryViewMode.GRID).join()
            controller.setDefaultSort(LibrarySort.ARTIST).join()
            controller.setReduceAnimations(true).join()
            controller.setHighContrast(true).join()
            controller.setReduceTransparency(true).join()
            controller.setResumePlayback(false).join()
            controller.setDefaultSpeed(1.5f).join()
            controller.setRepeatBehavior(RepeatBehavior.ONE).join()
            controller.setShuffleBehavior(ShuffleBehavior.ON).join()

            // Verify memory state updated immediately
            val state = controller.state.value
            assertEquals(ThemeMode.AMOLED, state.themeMode)
            assertEquals(LibraryViewMode.GRID, state.libraryViewMode)
            assertEquals(LibrarySort.ARTIST, state.defaultSort)
            assertTrue(state.reduceAnimations)
            assertTrue(state.highContrast)
            assertTrue(state.reduceTransparency)
            assertFalse(state.resumePlayback)
            assertEquals(1.5f, state.defaultSpeed)
            assertEquals(RepeatBehavior.ONE, state.repeatBehavior)
            assertEquals(ShuffleBehavior.ON, state.shuffleBehavior)

            // Verify raw SQLite settings table was populated
            val personalRepo = DesktopPersonalMediaRepository(database)
            assertEquals("AMOLED", personalRepo.setting("theme_mode"))
            assertEquals("GRID", personalRepo.setting("library_view_mode"))
            assertEquals("ARTIST", personalRepo.setting("default_sort"))
            assertEquals("true", personalRepo.setting("reduce_animations"))
            assertEquals("true", personalRepo.setting("high_contrast"))
            assertEquals("true", personalRepo.setting("reduce_transparency"))
            assertEquals("false", personalRepo.setting("resume_playback"))
            assertEquals("1.5", personalRepo.setting("default_speed"))
            assertEquals("ONE", personalRepo.setting("repeat_behavior"))
            assertEquals("ON", personalRepo.setting("shuffle_behavior"))

            controller.close()
        }
    }

    @Test
    fun testSettingsRestorationAcrossAppRestart() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-settings-test-restart")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        // Phase 1: Launch app, configure settings, and shutdown
        DesktopDatabase.open(dbPath).use { database ->
            val controller = DesktopLibraryController(database)
            controller.state.first { !it.loading }

            controller.setThemeMode(ThemeMode.DARK).join()
            controller.setLibraryViewMode(LibraryViewMode.GRID).join()
            controller.setDefaultSpeed(1.25f).join()
            controller.setHighContrast(true).join()

            controller.close()
        }

        // Phase 2: Relaunch app from persisted database file and verify restoration
        DesktopDatabase.open(dbPath).use { database ->
            val controller = DesktopLibraryController(database)
            val state = controller.state.first { !it.loading }

            assertEquals(ThemeMode.DARK, state.themeMode)
            assertEquals(LibraryViewMode.GRID, state.libraryViewMode)
            assertEquals(1.25f, state.defaultSpeed)
            assertTrue(state.highContrast)

            controller.close()
        }
    }

    @Test
    fun testSafeSettingsResetPreservesUserMediaAndPlaylists() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-settings-test-reset")
        val libraryDir = Files.createDirectories(tempDir.resolve("Media"))
        libraryDir.resolve("track1.mp3").writeText("audio track 1")
        libraryDir.resolve("track2.mp3").writeText("audio track 2")

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val personalRepo = DesktopPersonalMediaRepository(database)
            val scanner = DesktopMediaScanner(database)
            val controller = DesktopLibraryController(database)
            controller.state.first { !it.loading }

            // 1. Scan media into library
            val root = libraryRepo.addRoot(libraryDir).getOrThrow()
            scanner.scan(root.id, libraryDir).getOrThrow()
            val allAudio = libraryRepo.allMedia(MediaKind.AUDIO)
            assertEquals(2, allAudio.size)

            // 2. Create playlist and favorite
            val playlist = personalRepo.createPlaylist("Important Playlist")
            personalRepo.addToPlaylist(playlist.id, allAudio[0].id)
            personalRepo.setFavorite(FavoriteTarget.MEDIA, allAudio[0].id.value, favorite = true)
            personalRepo.savePosition(allAudio[0].id, 30_000L, 120_000L, completed = false)

            // 3. Customize settings
            controller.setThemeMode(ThemeMode.AMOLED).join()
            controller.setLibraryViewMode(LibraryViewMode.GRID).join()
            controller.setDefaultSpeed(2.0f).join()
            controller.setHighContrast(true).join()
            assertEquals(ThemeMode.AMOLED, controller.state.value.themeMode)

            // 4. Reset settings
            controller.resetSettings().join()

            // 5. Verify settings returned to defaults
            val resetState = controller.state.value
            assertEquals(ThemeMode.SYSTEM, resetState.themeMode)
            assertEquals(LibraryViewMode.LIST, resetState.libraryViewMode)
            assertEquals(1.0f, resetState.defaultSpeed)
            assertFalse(resetState.highContrast)

            // 6. VERIFY USER DATA IS 100% PRESERVED
            val playlistsAfter = personalRepo.playlists()
            assertEquals(1, playlistsAfter.size)
            assertEquals("Important Playlist", playlistsAfter[0].name)
            assertEquals(1, playlistsAfter[0].items.size)

            val favoritesAfter = personalRepo.favoriteIds(FavoriteTarget.MEDIA)
            assertTrue(allAudio[0].id.value in favoritesAfter)

            val positionAfter = personalRepo.position(allAudio[0].id)
            assertEquals(30_000L, positionAfter?.positionMillis)

            val mediaAfter = libraryRepo.allMedia(MediaKind.AUDIO)
            assertEquals(2, mediaAfter.size)

            controller.close()
        }
    }

    @Test
    fun testCorruptAndInvalidSettingsValuesFallbackSafely() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-settings-test-corrupt")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val personalRepo = DesktopPersonalMediaRepository(database)

            // Inject malformed/unsupported settings strings into SQLite
            personalRepo.putSetting("theme_mode", "NEON_VAPORWAVE_CORRUPTED")
            personalRepo.putSetting("library_view_mode", "3D_HOLOGRAM")
            personalRepo.putSetting("default_sort", "BITRATE_DESCENDING")
            personalRepo.putSetting("default_speed", "not_a_valid_float")
            personalRepo.putSetting("repeat_behavior", "INVALID_ENUM")
            personalRepo.putSetting("shuffle_behavior", "INVALID_ENUM")

            // Initialize controller and verify safe fallback without throwing unhandled exceptions
            val controller = DesktopLibraryController(database)
            val state = controller.state.first { !it.loading }

            assertEquals(ThemeMode.SYSTEM, state.themeMode)
            assertEquals(LibraryViewMode.LIST, state.libraryViewMode)
            assertEquals(LibrarySort.TITLE, state.defaultSort)
            assertEquals(1.0f, state.defaultSpeed)
            assertEquals(RepeatBehavior.REMEMBER, state.repeatBehavior)
            assertEquals(ShuffleBehavior.REMEMBER, state.shuffleBehavior)

            controller.close()
        }
    }
}
