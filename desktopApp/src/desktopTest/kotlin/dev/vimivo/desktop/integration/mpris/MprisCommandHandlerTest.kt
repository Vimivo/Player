package dev.vimivo.desktop.integration.mpris

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.playback.MpvCapabilities
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.QueueRepeatMode
import dev.vimivo.playback.SupportLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MprisCommandHandlerTest {
    private val song = MediaItem(MediaId("song"), "file:///song.flac", "Song", MediaKind.AUDIO, artist = "Artist")
    private val playing = PlaybackState(
        media = song,
        status = PlaybackStatus.Playing,
        positionMillis = 10_000,
        durationMillis = 60_000,
        isSeekable = true,
    )

    @Test
    fun playbackMethodsReachTheEngineAndQueue() {
        val fixture = Fixture(playing)
        listOf("Play", "Pause", "Stop", "PlayPause", "Next", "Previous").forEach { member ->
            fixture.handler.handle(checkNotNull(MprisRequests.playerCommand(member, emptyList())))
        }
        assertEquals(listOf("play", "pause", "stop"), fixture.engine.calls)
        assertEquals(listOf("toggle", "next", "previous"), fixture.launched)
    }

    @Test
    fun seekIsRelativeInMicrosecondsAndPastTheEndGoesToNext() {
        val fixture = Fixture(playing)

        assertEquals(15_000L, fixture.handler.handle(MprisCommand.Seek(5_000_000)))
        assertNull(fixture.handler.handle(MprisCommand.Seek(55_000_000)))

        assertEquals(listOf("seek:15000"), fixture.engine.calls)
        assertEquals(listOf("next"), fixture.launched)
    }

    @Test
    fun setPositionOnlyAppliesToTheCurrentTrackAndValidRange() {
        val fixture = Fixture(playing)
        val track = MprisModel.toTrackId(song)

        assertEquals(30_000L, fixture.handler.handle(MprisCommand.SetPosition(track, 30_000_000)))
        assertNull(fixture.handler.handle(MprisCommand.SetPosition("/org/mpris/MediaPlayer2/track/other", 1_000_000)))
        assertNull(fixture.handler.handle(MprisCommand.SetPosition(track, 99_000_000)))

        assertEquals(listOf("seek:30000"), fixture.engine.calls)
    }

    @Test
    fun writablePropertiesClampAndOnlyToggleWhenNeeded() {
        val fixture = Fixture(playing, PlaybackQueueState(shuffleEnabled = true, repeatMode = QueueRepeatMode.OFF))

        fixture.handler.handle(checkNotNull(MprisRequests.propertyCommand("Volume", 3.5)))
        fixture.handler.handle(checkNotNull(MprisRequests.propertyCommand("Rate", 9.0)))
        fixture.handler.handle(checkNotNull(MprisRequests.propertyCommand("Shuffle", true)))
        fixture.handler.handle(checkNotNull(MprisRequests.propertyCommand("LoopStatus", "Track")))

        assertEquals(listOf("volume:2.0", "speed:4.0"), fixture.engine.calls)
        assertEquals(0, fixture.shuffleToggles)
        assertEquals(QueueRepeatMode.ONE, fixture.queue.repeatMode)
        assertNull(MprisRequests.propertyCommand("PlaybackStatus", "Playing"))
    }

    @Test
    fun publishedPropertiesDescribeTheTrackAndSkipPosition() {
        val queue = PlaybackQueueState(repeatMode = QueueRepeatMode.ALL)
        val props = MprisModel.playerProperties(playing, queue, maxVolume = 2f)
        val metadata = (props["Metadata"] as MprisValue.Dict).raw

        assertEquals(MprisValue.Str("Playing"), props["PlaybackStatus"])
        assertEquals(MprisValue.Str("Playlist"), props["LoopStatus"])
        assertEquals(MprisValue.Int64(10_000_000), props["Position"])
        assertEquals(MprisValue.Int64(60_000_000), metadata["mpris:length"])
        assertEquals(MprisValue.StrList(listOf("Artist")), metadata["xesam:artist"])

        val pausedState = playing.copy(status = PlaybackStatus.Paused, positionMillis = 20_000)
        val paused = MprisModel.playerProperties(pausedState, queue, maxVolume = 2f)
        assertEquals(setOf("PlaybackStatus"), MprisModel.changedProperties(props, paused).keys)
    }

    private class Fixture(state: PlaybackState, initialQueue: PlaybackQueueState = PlaybackQueueState()) {
        val engine = RecordingEngine(state)
        val launched = mutableListOf<String>()
        var queue = initialQueue
        var shuffleToggles = 0
        val handler = MprisCommandHandler(
            engine = engine,
            queue = object : MprisQueueActions {
                override val queueState get() = queue
                override suspend fun togglePlayback() {
                    launched += "toggle"
                }
                override suspend fun next() {
                    launched += "next"
                }
                override suspend fun previous() {
                    launched += "previous"
                }
                override fun toggleShuffle() {
                    shuffleToggles++
                }
                override fun cycleRepeat() {
                    val modes = QueueRepeatMode.entries
                    queue = queue.copy(repeatMode = modes[(modes.indexOf(queue.repeatMode) + 1) % modes.size])
                }
            },
            launch = { block -> runBlocking { block() } },
        )
    }

    private class RecordingEngine(initial: PlaybackState) : PlayerEngine {
        val calls = mutableListOf<String>()
        override val id = EngineId.MPV
        override val capabilities = MpvCapabilities.forPlatform(DesktopPlatform.LINUX)
        override val state = MutableStateFlow(initial).asStateFlow()
        override val audioEnhancementState =
            MutableStateFlow(AudioEnhancementState(capabilities.audioEnhancements)).asStateFlow()
        override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED
        override suspend fun open(media: MediaItem, startPositionMillis: Long) = Result.success(Unit)
        override fun play() = record("play")
        override fun pause() = record("pause")
        override fun stop() = record("stop")
        override fun seekTo(positionMillis: Long) = record("seek:$positionMillis")
        override fun setPlaybackSpeed(speed: Float) = record("speed:$speed")
        override fun setVolume(volume: Float) = record("volume:$volume")
        override fun selectAudioTrack(trackId: String?) = Result.success(Unit)
        override fun selectVideoTrack(trackId: String?) = Result.success(Unit)
        override fun selectSubtitleTrack(trackId: String?) = Result.success(Unit)
        override fun close() = Unit
        private fun record(call: String): Result<Unit> = Result.success(Unit).also { calls += call }
    }
}
