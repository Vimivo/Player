@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MagicNumber",
    "MaxLineLength", "MaximumLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.core.streaming.StreamFailureReason
import dev.vimivo.core.streaming.StreamReconnectionPolicy
import dev.vimivo.core.streaming.StreamValidationResult
import dev.vimivo.core.streaming.StreamValidator
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopPersonalMediaRepository
import dev.vimivo.desktop.library.FavoriteTarget
import dev.vimivo.desktop.streaming.TestStreamServer
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EndToEndPlaybackUserJourneysTest {

    private lateinit var server: TestStreamServer

    @BeforeTest
    fun setUp() {
        server = TestStreamServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun journeyC_DirectVideoUrlPlaybackAndStop() = runTest {
        val videoUrl = "${server.baseUrl}/media/video.mp4"
        val validation = StreamValidator.validate(videoUrl)
        assertIs<StreamValidationResult.Valid>(validation)
        assertFalse(validation.isLive)

        val mediaItem = StreamValidator.toMediaItem(validation)
        val engine = MockSwitchingEngine()
        try {
            engine.open(mediaItem).getOrThrow()
            assertEquals(PlaybackStatus.Playing, engine.state.value.status)
            assertTrue(engine.videoSurfaceAttached, "Video media must attach video surface")

            // User clicks Stop
            engine.stop().getOrThrow()
            assertEquals(PlaybackStatus.Idle, engine.state.value.status)
            assertFalse(engine.videoSurfaceAttached, "Stopping video must detach video surface")
        } finally {
            engine.close()
        }
    }

    @Test
    fun journeyD_LiveHlsDisconnectionAndRecovery() = runTest {
        val hlsUrl = "${server.baseUrl}/hls/live.m3u8"
        val validation = StreamValidator.validate(hlsUrl)
        assertIs<StreamValidationResult.Valid>(validation)
        assertTrue(validation.isLive)

        val policy = StreamReconnectionPolicy(maxRetries = 3)

        // Transient timeout recovery
        assertTrue(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.TIMEOUT))
        assertTrue(policy.shouldRetry(attempt = 2, failureReason = StreamFailureReason.TIMEOUT))
        assertFalse(policy.shouldRetry(attempt = 3, failureReason = StreamFailureReason.TIMEOUT))

        // Stop during reconnection cancels retry loop immediately
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = StreamFailureReason.TIMEOUT, isUserCancelled = true))
    }

    @Test
    fun journeyE_InternetRadioAndNativeControls() = runTest {
        val radioUrl = "${server.baseUrl}/media/audio.mp3"
        val validation = StreamValidator.validate(radioUrl)
        assertIs<StreamValidationResult.Valid>(validation)

        val radioItem = MediaItem(
            id = MediaId("radio-paradise"),
            uri = radioUrl,
            title = "Internet Radio",
            kind = MediaKind.STREAM,
            source = MediaSource.NETWORK,
        )

        val engine = MockSwitchingEngine()
        try {
            engine.open(radioItem).getOrThrow()
            assertEquals(PlaybackStatus.Playing, engine.state.value.status)
            assertFalse(engine.videoSurfaceAttached, "Audio radio stream must not allocate video surface")

            // Simulate Native Media Controls (Pause / Resume / Stop)
            engine.pause().getOrThrow()
            assertEquals(PlaybackStatus.Paused, engine.state.value.status)

            engine.play().getOrThrow()
            assertEquals(PlaybackStatus.Playing, engine.state.value.status)

            engine.setVolume(0.8f).getOrThrow()
            assertEquals(0.8f, engine.state.value.volume)

            engine.stop().getOrThrow()
            assertEquals(PlaybackStatus.Idle, engine.state.value.status)
        } finally {
            engine.close()
        }
    }

    @Test
    fun journeyI_LocalBackupExportAndRestorationIntegrity() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-backup-journey")
        val dbSourcePath = tempDir.resolve("source.sqlite")
        val dbRestorePath = tempDir.resolve("restore.sqlite")
        val mediaDir = Files.createDirectories(tempDir.resolve("Music"))
        mediaDir.resolve("song-1.mp3").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        mediaDir.resolve("song-2.mp3").also { Files.write(it, byteArrayOf(4, 5, 6)) }

        // 1. Seed source user data
        DesktopDatabase.open(dbSourcePath).use { database ->
            val libraryRepo = dev.vimivo.desktop.library.DesktopLibraryRepository(database)
            val root = libraryRepo.addRoot(mediaDir).getOrThrow()
            dev.vimivo.desktop.library.DesktopMediaScanner(database).scan(root.id, mediaDir).getOrThrow()
            val songs = libraryRepo.media(MediaKind.AUDIO)
            val song1 = songs.first { it.fileName == "song-1.mp3" }
            val song2 = songs.first { it.fileName == "song-2.mp3" }

            val repo = DesktopPersonalMediaRepository(database)
            repo.putSetting("theme_mode", "DARK")
            repo.putSetting("volume", "85")

            val playlist = repo.createPlaylist("Rock Classics", now = 1000L)
            repo.addToPlaylist(playlist.id, song1.id, now = 1001L)
            repo.addToPlaylist(playlist.id, song2.id, now = 1002L)
            repo.setFavorite(FavoriteTarget.MEDIA, song1.id.value, true, now = 1003L)
        }

        // 2. Export to JSON backup format
        val backupJson = DesktopDatabase.open(dbSourcePath).use { exportBackupJson(it) }

        // Verify backup never contains secrets
        assertFalse(backupJson.contains("password"), "Backups must never contain credentials")
        assertFalse(backupJson.contains("Authorization"), "Backups must never contain auth headers")

        // 3. Restore into clean database and verify
        DesktopDatabase.open(dbRestorePath).use { restoreBackupJson(it, mediaDir, backupJson) }
    }

    private fun exportBackupJson(database: DesktopDatabase): String {
        val libraryRepo = dev.vimivo.desktop.library.DesktopLibraryRepository(database)
        val mediaById = libraryRepo.media(MediaKind.AUDIO).associateBy { it.id.value }
        val repo = DesktopPersonalMediaRepository(database)
        val root = buildJsonObject {
            put("format", "dev.vimivo.backup")
            put("version", 1)
            put("createdAtEpochMillis", System.currentTimeMillis())
            put(
                "settings",
                buildJsonObject {
                    put("theme_mode", repo.setting("theme_mode").orEmpty())
                    put("volume", repo.setting("volume").orEmpty())
                },
            )
            put(
                "favorites",
                buildJsonArray {
                    repo.favoriteIds(FavoriteTarget.MEDIA).forEach { id ->
                        mediaById[id]?.uri?.let { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    }
                },
            )
            put(
                "playlists",
                buildJsonArray {
                    repo.playlists().forEach { pl ->
                        add(
                            buildJsonObject {
                                put("id", pl.id)
                                put("name", pl.name)
                                put(
                                    "items",
                                    buildJsonArray {
                                        pl.items.forEach { id ->
                                            mediaById[id.value]?.uri?.let { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        return root.toString()
    }

    private suspend fun restoreBackupJson(database: DesktopDatabase, mediaDir: java.nio.file.Path, backupJson: String) {
        val libraryRepo = dev.vimivo.desktop.library.DesktopLibraryRepository(database)
        val root = libraryRepo.addRoot(mediaDir).getOrThrow()
        dev.vimivo.desktop.library.DesktopMediaScanner(database).scan(root.id, mediaDir).getOrThrow()
        val mediaByUri = libraryRepo.media(MediaKind.AUDIO).associateBy { it.uri }

        val repo = DesktopPersonalMediaRepository(database)
        val parsed = Json.parseToJsonElement(backupJson).jsonObject
        assertEquals("dev.vimivo.backup", parsed["format"]?.jsonPrimitive?.content)
        assertEquals(1, parsed["version"]?.jsonPrimitive?.int)

        val settingsObj = parsed["settings"]?.jsonObject
        if (settingsObj != null) {
            repo.putSetting("theme_mode", settingsObj["theme_mode"]?.jsonPrimitive?.content.orEmpty())
            repo.putSetting("volume", settingsObj["volume"]?.jsonPrimitive?.content.orEmpty())
        }

        val favs = parsed["favorites"]?.jsonArray
        favs?.forEach { element ->
            val uri = element.jsonPrimitive.content
            mediaByUri[uri]?.id?.let { mediaId ->
                repo.setFavorite(FavoriteTarget.MEDIA, mediaId.value, true, now = 2000L)
            }
        }

        val pls = parsed["playlists"]?.jsonArray
        pls?.forEach { plElement ->
            val plObj = plElement.jsonObject
            val created = repo.createPlaylist(plObj["name"]?.jsonPrimitive?.content.orEmpty(), now = 2000L)
            val items = plObj["items"]?.jsonArray
            items?.forEachIndexed { index, itemElement ->
                val uri = itemElement.jsonPrimitive.content
                mediaByUri[uri]?.id?.let { mediaId ->
                    repo.addToPlaylist(created.id, mediaId, now = 2000L + index)
                }
            }
        }

        // Verify restored data matches source perfectly
        val restoredSong1 = libraryRepo.media(MediaKind.AUDIO).first { it.fileName == "song-1.mp3" }
        val restoredSong2 = libraryRepo.media(MediaKind.AUDIO).first { it.fileName == "song-2.mp3" }
        assertEquals("DARK", repo.setting("theme_mode"))
        assertEquals("85", repo.setting("volume"))
        assertTrue(repo.favoriteIds(FavoriteTarget.MEDIA).contains(restoredSong1.id.value))
        val restoredPlaylists = repo.playlists()
        assertEquals(1, restoredPlaylists.size)
        assertEquals("Rock Classics", restoredPlaylists.single().name)
        assertEquals(listOf(restoredSong1.id, restoredSong2.id), restoredPlaylists.single().items)
    }
}
