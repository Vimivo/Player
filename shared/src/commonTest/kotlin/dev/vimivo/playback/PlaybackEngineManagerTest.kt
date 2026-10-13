package dev.vimivo.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineManagerTest {
    @Test
    fun prefersMedia3AndPreservesQueue() = runTest {
        val media3 = FakeFactory(EngineId.MEDIA3)
        val vlc = FakeFactory(EngineId.LIBVLC)
        val manager = PlaybackEngineManager(mapOf(media3.id to media3, vlc.id to vlc), backgroundScope)
        val queue = QueueContext(listOf(media("one.mp4"), media("two.mkv")), 1)

        assertTrue(manager.open(queue, 1_234).isSuccess)
        runCurrent()

        assertEquals(EngineId.MEDIA3, manager.state.value.engine)
        assertEquals(queue, manager.state.value.queue)
        assertEquals(1_234, media3.latest.state.value.positionMillis)
        assertEquals(0, vlc.created)
    }

    @Test
    fun fallsBackOnceForVerifiedRecoverableErrorAndKeepsPosition() = runTest {
        val media3 = FakeFactory(EngineId.MEDIA3)
        val vlc = FakeFactory(EngineId.LIBVLC)
        val manager = PlaybackEngineManager(mapOf(media3.id to media3, vlc.id to vlc), backgroundScope)

        manager.open(media("sample.avi"), 500)
        runCurrent()
        media3.latest.failRecoverably(position = 9_000)
        runCurrent()

        assertEquals(EngineId.LIBVLC, manager.state.value.engine)
        assertEquals(9_000, vlc.latest.state.value.positionMillis)
        assertEquals(1, media3.created)
        assertEquals(1, vlc.created)
    }

    @Test
    fun doesNotFallbackForMissingOrDeniedSource() = runTest {
        val media3 = FakeFactory(EngineId.MEDIA3)
        val vlc = FakeFactory(EngineId.LIBVLC)
        val manager = PlaybackEngineManager(mapOf(media3.id to media3, vlc.id to vlc), backgroundScope)

        manager.open(media("missing.mp4"))
        runCurrent()
        media3.latest.failSourceUnavailable()
        runCurrent()

        assertEquals(EngineId.MEDIA3, manager.state.value.engine)
        assertEquals(0, vlc.created)
    }

    @Test
    fun reappliesExternalSubtitleAfterEngineFallback() = runTest {
        val media3 = FakeFactory(EngineId.MEDIA3)
        val vlc = FakeFactory(EngineId.LIBVLC)
        val manager = PlaybackEngineManager(mapOf(media3.id to media3, vlc.id to vlc), backgroundScope)
        val subtitle = ExternalSubtitle("content://subtitle", "movie.en.srt", "en", "application/x-subrip")

        manager.open(media("sample.mkv"))
        runCurrent()
        assertTrue(manager.addExternalSubtitle(subtitle).isSuccess)
        media3.latest.failRecoverably(position = 1_000)
        runCurrent()

        assertEquals(listOf(subtitle), vlc.latest.externalSubtitles)
    }

    private fun media(name: String) = MediaItem(MediaId(name), "file:///tmp/$name", name, MediaKind.VIDEO)
}

private class FakeFactory(
    override val id: EngineId,
) : PlayerEngineFactory {
    var created = 0
    lateinit var latest: FakeEngine
    override fun supportFor(media: MediaItem) = if (id == EngineId.MEDIA3) {
        SupportLevel.PREFERRED
    } else {
        SupportLevel.FALLBACK
    }

    override fun create(): PlayerEngine = FakeEngine(id).also {
        latest = it
        created++
    }
}

private class FakeEngine(override val id: EngineId) : PlayerEngine {
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    override val state = mutableState.asStateFlow()
    override val capabilities = EngineCapabilities(
        localAudio = true,
        localVideo = true,
        httpStreaming = true,
        hardwareDecoding = true,
        softwareDecoding = id == EngineId.LIBVLC,
        playbackSpeed = true,
        audioTrackSelection = true,
        videoTrackSelection = true,
        embeddedSubtitles = true,
        externalSubtitles = false,
        hdr = true,
    )
    var closed = false
    val externalSubtitles = mutableListOf<ExternalSubtitle>()

    override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED
    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Ready,
            positionMillis = startPositionMillis,
        )
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
    override fun addExternalSubtitle(subtitle: ExternalSubtitle) = Result.success(Unit).also {
        externalSubtitles += subtitle
    }
    override fun close() { closed = true }

    fun failRecoverably(position: Long) {
        mutableState.value = mutableState.value.copy(
            positionMillis = position,
            status = PlaybackStatus.Error(
                PlaybackError(
                    code = PlaybackErrorCode.DECODER_INITIALIZATION,
                    message = "decoder",
                    engine = id,
                    recoverableByFallback = true,
                ),
            ),
        )
    }

    fun failSourceUnavailable() {
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.Error(
                PlaybackError(
                    code = PlaybackErrorCode.SOURCE_NOT_FOUND,
                    message = "missing",
                    engine = id,
                ),
            ),
        )
    }
}
