package dev.vimivo.desktop.integration.mpris

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MprisModelTest {
    @Test
    fun timeConversionToMicroseconds() {
        assertEquals(0L, MprisModel.millisToMicroseconds(0L))
        assertEquals(0L, MprisModel.millisToMicroseconds(-100L))
        assertEquals(10_000_000L, MprisModel.millisToMicroseconds(10_000L))
        assertEquals(180_000_000L, MprisModel.millisToMicroseconds(180_000L))
    }

    @Test
    fun playbackStatusStrings() {
        assertEquals("Playing", MprisModel.toPlaybackStatusString(PlaybackStatus.Playing))
        assertEquals("Paused", MprisModel.toPlaybackStatusString(PlaybackStatus.Paused))
        assertEquals("Stopped", MprisModel.toPlaybackStatusString(PlaybackStatus.Idle))
        assertEquals("Stopped", MprisModel.toPlaybackStatusString(PlaybackStatus.Completed))
        assertEquals("Stopped", MprisModel.toPlaybackStatusString(PlaybackStatus.Preparing))
    }

    @Test
    fun loopStatusStrings() {
        assertEquals("None", MprisModel.toLoopStatusString(QueueRepeatMode.OFF))
        assertEquals("Playlist", MprisModel.toLoopStatusString(QueueRepeatMode.ALL))
        assertEquals("Track", MprisModel.toLoopStatusString(QueueRepeatMode.ONE))
    }

    @Test
    fun trackIdFormatting() {
        assertEquals("/org/mpris/MediaPlayer2/track/none", MprisModel.toTrackId(null))
        val item = MediaItem(MediaId("file:///music/song.mp3"), "file:///music/song.mp3", "Song", MediaKind.AUDIO)
        val trackId = MprisModel.toTrackId(item)
        assertTrue(trackId.startsWith("/org/mpris/MediaPlayer2/track/"))
    }

    @Test
    fun metadataMapMapping() {
        val item = MediaItem(
            id = MediaId("song_1"),
            uri = "file:///music/track.flac",
            title = "Test Song",
            kind = MediaKind.AUDIO,
            artist = "Test Artist",
            album = "Test Album",
            artworkUri = "file:///cache/artwork/art.jpg",
        )
        val map = MprisModel.toMetadataMap(item, 200_000L)
        assertEquals("Test Song", map["xesam:title"])
        assertEquals(listOf("Test Artist"), map["xesam:artist"])
        assertEquals("Test Album", map["xesam:album"])
        assertEquals("file:///cache/artwork/art.jpg", map["mpris:artUrl"])
        assertEquals(200_000_000L, map["mpris:length"])
    }

    @Test
    fun factoryCreatesFunctionalServiceForAnyPlatform() {
        val engine = MpvPlayerEngine()
        val queue = PlaybackQueueController(engine, CoroutineScope(Dispatchers.Default))

        val macService = MprisServiceFactory.create(engine, queue, {}, DesktopPlatform.MACOS)
        assertNotNull(macService)
        macService.close()

        val winService = MprisServiceFactory.create(engine, queue, {}, DesktopPlatform.WINDOWS)
        assertNotNull(winService)
        winService.close()

        val linuxService = MprisServiceFactory.create(engine, queue, {}, DesktopPlatform.LINUX)
        assertNotNull(linuxService)
        linuxService.close()
    }
}
