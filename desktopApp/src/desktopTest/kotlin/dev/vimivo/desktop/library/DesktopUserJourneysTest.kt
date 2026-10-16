@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.util.SafeLogger
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.QueueRepeatMode
import dev.vimivo.playback.SupportLevel
import dev.vimivo.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopUserJourneysTest {

    @Test
    fun journey1FreshInstallAndMediaIndexing() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-journey-1")
        val mediaDir = Files.createDirectories(tempDir.resolve("Media"))
        Files.createDirectories(mediaDir.resolve("Subfolder"))

        mediaDir.resolve("track1.mp3").writeText("synthetic mp3")
        mediaDir.resolve("Subfolder").resolve("film.mp4").writeText("synthetic mp4")
        mediaDir.resolve("readme.txt").writeText("ignore me")

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val repo = DesktopLibraryRepository(database)
            val root = repo.addRoot(mediaDir).getOrThrow()
            val scanner = DesktopMediaScanner(database)
            val scanResult = scanner.scan(root.id, mediaDir).getOrThrow()

            assertEquals(2, scanResult.indexed, "Scanner must discover audio and video, ignoring txt")
            assertEquals(1, repo.media(MediaKind.AUDIO).size)
            assertEquals(1, repo.media(MediaKind.VIDEO).size)
            assertEquals("film", repo.media(MediaKind.VIDEO).single().title)
        }
    }

    @Test
    fun journey2MusicLibraryOperations() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-journey-2")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val personalRepo = DesktopPersonalMediaRepository(database)
            val metadataRepo = DesktopMetadataRepository(database)

            val rootDir = Files.createDirectories(tempDir.resolve("Music"))
            rootDir.resolve("s1.flac").writeText("audio1")
            rootDir.resolve("s2.flac").writeText("audio2")

            val root = libraryRepo.addRoot(rootDir).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, rootDir).getOrThrow()

            val songs = libraryRepo.media(MediaKind.AUDIO)
            assertEquals(2, songs.size)
            val song1 = songs.first { it.fileName == "s1.flac" }
            val song2 = songs.first { it.fileName == "s2.flac" }

            metadataRepo.save(
                song1,
                ExtractedMediaMetadata(
                    title = "Song One",
                    artist = "Master Artist",
                    album = "Greatest Album",
                    durationMillis = 200_000,
                ),
            )
            metadataRepo.save(
                song2,
                ExtractedMediaMetadata(
                    title = "Song Two",
                    artist = "Master Artist",
                    album = "Greatest Album",
                    durationMillis = 180_000,
                ),
            )

            // Search by artist
            val searchResults = libraryRepo.media(MediaKind.AUDIO, "Master Artist")
            assertEquals(2, searchResults.size)

            // Favorites
            personalRepo.setFavorite(FavoriteTarget.MEDIA, song1.id.value, true, now = 100)
            assertTrue(personalRepo.favoriteIds(FavoriteTarget.MEDIA).contains(song1.id.value))
            assertFalse(personalRepo.favoriteIds(FavoriteTarget.MEDIA).contains(song2.id.value))

            // Playlists
            val playlist = personalRepo.createPlaylist("Favorites Mix", now = 200)
            personalRepo.addToPlaylist(playlist.id, song1.id, now = 201)
            personalRepo.addToPlaylist(playlist.id, song2.id, now = 202)

            val plItems = personalRepo.playlists().single().items
            assertEquals(listOf(song1.id, song2.id), plItems)

            // Reorder
            personalRepo.movePlaylistItem(playlist.id, song2.id, 0, now = 203)
            assertEquals(listOf(song2.id, song1.id), personalRepo.playlists().single().items)
        }
    }

    @Test
    fun journey3VideoPlaybackLifecycle() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-journey-3")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val libraryRepo = DesktopLibraryRepository(database)
            val personalRepo = DesktopPersonalMediaRepository(database)
            val metadataRepo = DesktopMetadataRepository(database)

            val videoDir = Files.createDirectories(tempDir.resolve("Videos"))
            videoDir.resolve("movie.mkv").writeText("video data")
            val root = libraryRepo.addRoot(videoDir).getOrThrow()
            DesktopMediaScanner(database).scan(root.id, videoDir).getOrThrow()

            val video = libraryRepo.media(MediaKind.VIDEO).single()
            metadataRepo.save(
                video,
                ExtractedMediaMetadata(
                    title = "Documentary",
                    videoCodec = "h264",
                    audioCodec = "aac",
                    width = 1920,
                    height = 1080,
                    durationMillis = 3_600_000,
                ),
            )

            // Record initial playback and resume position at 15 minutes (900,000 ms)
            personalRepo.recordPlayback(video.id, 900_000, completed = false, now = 1000)
            personalRepo.savePosition(video.id, 900_000, 3_600_000, completed = false, now = 1000)
            val savedPos = personalRepo.position(video.id)
            assertNotNull(savedPos)
            assertEquals(900_000, savedPos.positionMillis)

            // Add bookmarks
            val bm = personalRepo.addBookmark(video.id, 450_000, "Key Scene", now = 1001)
            assertEquals("Key Scene", personalRepo.bookmarks(video.id).single().name)
            assertEquals(450_000, bm.positionMillis)

            // Mark completion at end
            personalRepo.markPlaybackCompleted(video.id, 3_595_000, now = 2000)
            val completedHistory = personalRepo.history().single()
            assertTrue(completedHistory.completed)
            assertEquals(3_595_000, completedHistory.positionMillis)
        }
    }

    @Test
    fun journey4ContinuousPlaybackQueue() = runTest {
        val engine = FakeJourneyEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val itemA = MediaItem(MediaId("a"), "file:///a.mp3", "A", MediaKind.AUDIO)
        val itemB = MediaItem(MediaId("b"), "file:///b.mp3", "B", MediaKind.AUDIO)
        val itemC = MediaItem(MediaId("c"), "file:///c.mp3", "C", MediaKind.AUDIO)

        queue.play(listOf(itemA, itemB, itemC)).getOrThrow()
        runCurrent()
        assertEquals("a", queue.state.value.currentMedia?.id?.value)

        // Queue next track
        val itemNext = MediaItem(MediaId("next"), "file:///next.mp3", "Next", MediaKind.AUDIO)
        queue.playNext(listOf(itemNext))
        assertEquals("next", queue.state.value.items[1].media.id.value)

        // Toggle repeat mode
        queue.cycleRepeat()
        assertEquals(QueueRepeatMode.ONE, queue.state.value.repeatMode)

        // Snapshot and restore
        val snapshot = queue.snapshot()
        val newEngine = FakeJourneyEngine()
        val restoredQueue = PlaybackQueueController(newEngine, backgroundScope)
        restoredQueue.restore(snapshot)
        runCurrent()

        assertTrue(newEngine.opened.isEmpty(), "Restoration must not autoplay")
        assertEquals(QueueRepeatMode.ONE, restoredQueue.state.value.repeatMode)
        assertEquals("a", restoredQueue.state.value.currentMedia?.id?.value)
    }

    @Test
    fun journey5UserDataIntegrityAndSettings() {
        val tempDir = Files.createTempDirectory("vimivo-journey-5")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val repo = DesktopPersonalMediaRepository(database)
            repo.putSetting("theme_mode", ThemeMode.AMOLED.name)
            repo.putSetting("volume", "75")
        }

        DesktopDatabase.open(dbPath).use { database ->
            val repo = DesktopPersonalMediaRepository(database)
            assertEquals(ThemeMode.AMOLED.name, repo.setting("theme_mode"))
            assertEquals("75", repo.setting("volume"))
        }

        // SafeLogger redactions
        val safeLog = SafeLogger.sanitize("Loading /Users/alice/Music/secret.mp3 with token https://stream.com/live?auth=secret123")
        assertFalse(safeLog.contains("/Users/alice"), "User directories must be redacted")
        assertFalse(safeLog.contains("secret123"), "URL tokens must be redacted")
    }

    @Test
    fun journey6NetworkStreamingHandling() {
        val streamUrl = "https://example.com/live.m3u8?token=xyz987"
        val safe = SafeLogger.sanitize(streamUrl)
        assertTrue(safe.contains("example.com"))
        assertFalse(safe.contains("xyz987"))

        val item = MediaItem(MediaId("stream-1"), streamUrl, "Live Broadcast", MediaKind.STREAM)
        assertEquals(MediaKind.STREAM, item.kind)
        assertTrue(item.isAvailable)
    }

    @Test
    fun journey7ErrorRecoveryAndFaultInjection() = runTest {
        val tempDir = Files.createTempDirectory("vimivo-journey-7")
        val mediaDir = Files.createDirectories(tempDir.resolve("FaultyMedia"))
        val normal = mediaDir.resolve("normal.mp3").also { it.writeText("ok") }
        val toDelete = mediaDir.resolve("will_delete.mp3").also { it.writeText("temporary") }

        val dbPath = tempDir.resolve("vimivo.sqlite")
        DesktopDatabase.open(dbPath).use { database ->
            val repo = DesktopLibraryRepository(database)
            val root = repo.addRoot(mediaDir).getOrThrow()
            val scanner = DesktopMediaScanner(database)
            scanner.scan(root.id, mediaDir).getOrThrow()

            assertEquals(2, repo.media(MediaKind.AUDIO).size)
            assertTrue(Files.exists(normal), "Normal media file should exist")

            // Simulate file deletion
            Files.delete(toDelete)
            scanner.scan(root.id, mediaDir).getOrThrow()

            // Available should only be normal.mp3
            val available = repo.media(MediaKind.AUDIO)
            assertEquals(1, available.size)
            assertEquals("normal.mp3", available.single().fileName)

            // Inactive record must still exist in DB (marked available = 0)
            database.transaction { conn ->
                conn.createStatement().executeQuery("SELECT COUNT(*) FROM media_items WHERE available = 0").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1), "Deleted file must be retained with available=0")
                }
            }
        }
    }
}

private class FakeJourneyEngine : PlayerEngine {
    override val id = EngineId.MPV
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    override val state = mutableState.asStateFlow()
    override val capabilities = EngineCapabilities(
        localAudio = true, localVideo = true, httpStreaming = true, hardwareDecoding = true,
        softwareDecoding = true, playbackSpeed = true, audioTrackSelection = true,
        videoTrackSelection = true, embeddedSubtitles = true, externalSubtitles = true, hdr = true,
    )
    override val audioEnhancementState = MutableStateFlow(dev.vimivo.playback.AudioEnhancementState(capabilities.audioEnhancements)).asStateFlow()
    val opened = mutableListOf<String>()

    override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED
    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
        opened += media.id.value
        mutableState.value = PlaybackState(engine = id, media = media, status = PlaybackStatus.Ready, positionMillis = startPositionMillis)
        return Result.success(Unit)
    }
    override fun play() = Result.success(Unit)
    override fun pause() = Result.success(Unit)
    override fun stop() = Result.success(Unit)
    override fun seekTo(positionMillis: Long) = Result.success(Unit)
    override fun setPlaybackSpeed(speed: Float) = Result.success(Unit)
    override fun setVolume(volume: Float) = Result.success(Unit)
    override fun selectAudioTrack(trackId: String?) = Result.success(Unit)
    override fun selectVideoTrack(trackId: String?) = Result.success(Unit)
    override fun selectSubtitleTrack(trackId: String?) = Result.success(Unit)
    override fun close() = Unit
}
