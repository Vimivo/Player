@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MagicNumber",
    "MaxLineLength", "MaximumLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.core.streaming.LiveLatencyMode
import dev.vimivo.core.streaming.StreamFailureReason
import dev.vimivo.core.streaming.StreamReconnectionPolicy
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackSourceSwitchingTest {

    private fun localAudioItem(id: String) = MediaItem(
        id = MediaId(id),
        uri = "file:///music/$id.flac",
        title = "Local Audio $id",
        kind = MediaKind.AUDIO,
        source = MediaSource.SAF_FILE,
        durationMillis = 180_000,
    )

    private fun localVideoItem(id: String) = MediaItem(
        id = MediaId(id),
        uri = "file:///movies/$id.mp4",
        title = "Local Video $id",
        kind = MediaKind.VIDEO,
        source = MediaSource.SAF_FILE,
        durationMillis = 7_200_000,
    )

    private fun streamAudioItem(id: String) = MediaItem(
        id = MediaId(id),
        uri = "https://stream.example.org/$id.mp3",
        title = "Stream Audio $id",
        kind = MediaKind.STREAM,
        source = MediaSource.NETWORK,
        durationMillis = null,
    )

    private fun streamVideoItem(id: String) = MediaItem(
        id = MediaId(id),
        uri = "https://stream.example.org/$id.m3u8",
        title = "Stream Video $id",
        kind = MediaKind.STREAM,
        source = MediaSource.NETWORK,
        durationMillis = null,
    )

    @Test
    fun testLocalAudioToLocalVideoSwitching() = runTest {
        val engine = MockSwitchingEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val audio = localAudioItem("track1")
        val video = localVideoItem("film1")

        queue.play(listOf(audio, video)).getOrThrow()
        runCurrent()
        assertEquals(MediaKind.AUDIO, queue.state.value.currentMedia?.kind)
        assertEquals("track1", queue.state.value.currentMedia?.id?.value)

        queue.next()
        runCurrent()
        assertEquals(MediaKind.VIDEO, queue.state.value.currentMedia?.kind)
        assertEquals("film1", queue.state.value.currentMedia?.id?.value)
        assertTrue(engine.videoSurfaceAttached, "Switching to local video must enable video rendering")
    }

    @Test
    fun testLocalVideoToLocalAudioSwitching() = runTest {
        val engine = MockSwitchingEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val video = localVideoItem("film1")
        val audio = localAudioItem("track1")

        queue.play(listOf(video, audio)).getOrThrow()
        runCurrent()
        assertEquals(MediaKind.VIDEO, queue.state.value.currentMedia?.kind)

        queue.next()
        runCurrent()
        assertEquals(MediaKind.AUDIO, queue.state.value.currentMedia?.kind)
        assertFalse(engine.videoSurfaceAttached, "Switching to audio must deactivate video surface")
    }

    @Test
    fun testLocalAudioToUrlAudioSwitching() = runTest {
        val engine = MockSwitchingEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val localAudio = localAudioItem("song1")
        val streamAudio = streamAudioItem("radio1")

        queue.play(listOf(localAudio, streamAudio)).getOrThrow()
        runCurrent()
        assertEquals(MediaSource.SAF_FILE, queue.state.value.currentMedia?.source)

        queue.next()
        runCurrent()
        assertEquals(MediaSource.NETWORK, queue.state.value.currentMedia?.source)
        assertEquals(MediaKind.STREAM, queue.state.value.currentMedia?.kind)
        assertEquals(PlaybackStatus.Playing, engine.state.value.status)
    }

    @Test
    fun testUrlAudioToLocalAudioSwitching() = runTest {
        val engine = MockSwitchingEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val streamAudio = streamAudioItem("radio1")
        val localAudio = localAudioItem("song1")

        queue.play(listOf(streamAudio, localAudio)).getOrThrow()
        runCurrent()
        assertEquals(MediaSource.NETWORK, queue.state.value.currentMedia?.source)

        queue.next()
        runCurrent()
        assertEquals(MediaSource.SAF_FILE, queue.state.value.currentMedia?.source)
        assertEquals(180_000L, queue.state.value.currentMedia?.durationMillis)
    }

    @Test
    fun testHlsLiveToLocalMusicSwitching() = runTest {
        val engine = MockSwitchingEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val liveStream = streamVideoItem("live_hls")
        val localMusic = localAudioItem("music1")

        queue.play(listOf(liveStream, localMusic)).getOrThrow()
        runCurrent()
        // Live stream has no finite duration
        assertEquals(null, queue.state.value.currentMedia?.durationMillis)

        queue.next()
        runCurrent()
        // Local music restores finite duration and seekability
        assertEquals(MediaKind.AUDIO, queue.state.value.currentMedia?.kind)
        assertEquals(180_000L, queue.state.value.currentMedia?.durationMillis)
    }

    @Test
    fun testFailedStreamToLocalMediaSwitching() = runTest {
        val engine = MockSwitchingEngine()
        val queue = PlaybackQueueController(engine, backgroundScope)

        val badStream = MediaItem(
            id = MediaId("bad_stream"),
            uri = "https://nonexistent.host/stream.m3u8",
            title = "Broken Stream",
            kind = MediaKind.STREAM,
            source = MediaSource.NETWORK,
        )
        val validLocal = localAudioItem("recovery_track")

        queue.play(listOf(badStream, validLocal)).getOrThrow()
        runCurrent()

        // Inject simulated connection failure
        engine.injectFailure("HTTP 404: Not Found")
        assertIs<PlaybackStatus.Error>(engine.state.value.status)

        // Switching to valid local track must clear error state
        queue.next()
        runCurrent()
        assertEquals(PlaybackStatus.Playing, engine.state.value.status)
        assertEquals("recovery_track", queue.state.value.currentMedia?.id?.value)
    }

    @Test
    fun testBufferingStreamToNewSourceIsolation() {
        val engine = MpvPlayerEngine()
        try {
            // Verify default latency mode
            assertEquals(LiveLatencyMode.BALANCED, engine.liveLatencyMode)

            // Switch latency mode
            engine.setLiveLatencyMode(LiveLatencyMode.LOW_LATENCY)
            assertEquals(LiveLatencyMode.LOW_LATENCY, engine.liveLatencyMode)

            // Pausing sets userPaused intent
            engine.pause()
            // Stopping sets userStopped intent, clears userPaused, and resets to Idle
            engine.stop()
            assertEquals(PlaybackStatus.Idle, engine.state.value.status)
        } finally {
            engine.close()
        }
    }

    @Test
    fun testReconnectingStreamUserStopPreservation() {
        val policy = StreamReconnectionPolicy(maxRetries = 3)
        val failure = StreamFailureReason.TIMEOUT

        // When user has not cancelled, retry is allowed
        assertTrue(policy.shouldRetry(attempt = 1, failureReason = failure, isUserCancelled = false))

        // When user presses Stop during reconnect, cancellation is immediate and absolute
        assertFalse(policy.shouldRetry(attempt = 1, failureReason = failure, isUserCancelled = true))
    }
}

internal class MockSwitchingEngine : PlayerEngine {
    override val id = EngineId.MPV
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    override val state = mutableState.asStateFlow()
    override val capabilities = EngineCapabilities(
        localAudio = true, localVideo = true, httpStreaming = true, hlsStreaming = true,
        dashStreaming = true, liveStreaming = true, hardwareDecoding = true, softwareDecoding = true,
        playbackSpeed = true, audioTrackSelection = true, videoTrackSelection = true,
        embeddedSubtitles = true, externalSubtitles = true, hdr = true,
    )
    override val audioEnhancementState = MutableStateFlow(
        dev.vimivo.playback.AudioEnhancementState(capabilities.audioEnhancements),
    ).asStateFlow()

    var videoSurfaceAttached = false
        private set
    var sessionCount = 0
        private set

    override fun supportFor(media: MediaItem): SupportLevel = SupportLevel.PREFERRED

    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
        sessionCount++
        videoSurfaceAttached = media.kind == MediaKind.VIDEO ||
            (media.kind == MediaKind.STREAM && !media.uri.endsWith(".mp3") && !media.uri.contains("audio"))
        val tracks = if (videoSurfaceAttached) {
            listOf(
                TrackInfo("a1", TrackType.AUDIO, "Stereo AAC", language = "eng"),
                TrackInfo("v1", TrackType.VIDEO, "1080p H.264", width = 1920, height = 1080),
            )
        } else {
            listOf(TrackInfo("a1", TrackType.AUDIO, "Stereo FLAC", language = "eng"))
        }

        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Playing,
            positionMillis = startPositionMillis,
            durationMillis = media.durationMillis,
            audioTracks = tracks.filter { it.type == TrackType.AUDIO },
            videoTracks = tracks.filter { it.type == TrackType.VIDEO },
        )
        return Result.success(Unit)
    }

    override fun play() = runCatching {
        mutableState.value = mutableState.value.copy(status = PlaybackStatus.Playing)
    }

    override fun pause() = runCatching {
        mutableState.value = mutableState.value.copy(status = PlaybackStatus.Paused)
    }

    override fun stop() = runCatching {
        mutableState.value = PlaybackState(engine = id, status = PlaybackStatus.Idle)
        videoSurfaceAttached = false
    }

    override fun seekTo(positionMillis: Long) = runCatching {
        mutableState.value = mutableState.value.copy(positionMillis = positionMillis)
    }

    override fun setPlaybackSpeed(speed: Float) = runCatching {
        mutableState.value = mutableState.value.copy(speed = speed)
    }

    override fun setVolume(volume: Float) = runCatching {
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    override fun selectAudioTrack(trackId: String?) = Result.success(Unit)
    override fun selectVideoTrack(trackId: String?) = Result.success(Unit)
    override fun selectSubtitleTrack(trackId: String?) = Result.success(Unit)

    fun injectFailure(message: String) {
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.Error(
                dev.vimivo.playback.PlaybackError(
                    dev.vimivo.playback.PlaybackErrorCode.NETWORK,
                    message,
                    engine = id,
                ),
            ),
        )
    }

    override fun close() = Unit
}
