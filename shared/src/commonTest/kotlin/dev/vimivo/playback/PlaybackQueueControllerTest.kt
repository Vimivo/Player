@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueControllerTest {
    @Test
    fun continuesOnCompletionAndHonorsRepeatModes() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("one"), media("two"))

        controller.play(items).getOrThrow()
        runCurrent()
        engine.complete()
        runCurrent()
        assertEquals("two", controller.state.value.currentMedia?.id?.value)

        controller.cycleRepeat()
        engine.complete()
        runCurrent()
        assertEquals("two", controller.state.value.currentMedia?.id?.value)
        assertEquals(2, engine.opened.count { it == "two" })

        controller.cycleRepeat()
        engine.complete()
        runCurrent()
        assertEquals("one", controller.state.value.currentMedia?.id?.value)
    }

    @Test
    fun mutatesQueueWithoutLosingCurrentItem() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope, random = Random(7))
        val items = listOf(media("one"), media("two"), media("three"))
        controller.play(items, startIndex = 1).getOrThrow()

        controller.playNext(listOf(media("next")))
        controller.addToQueue(listOf(media("last")))
        controller.move(4, 0)
        controller.toggleShuffle()

        assertEquals("two", controller.state.value.currentMedia?.id?.value)
        assertEquals(5, controller.state.value.items.size)
        assertTrue(controller.state.value.shuffleEnabled)
        controller.remove(controller.state.value.items.indexOfFirst { it.media.id.value == "three" })
        assertEquals(4, controller.state.value.items.size)
    }

    @Test
    fun skipsUnavailableMedia() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope, isAvailable = { it.id.value != "missing" })
        controller.play(listOf(media("missing"), media("available"))).getOrThrow()

        assertEquals("available", controller.state.value.currentMedia?.id?.value)
        assertEquals(listOf("available"), engine.opened)
    }

    @Test
    fun keepsDuplicateMediaOccurrencesDistinctWhenReordering() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val duplicate = media("same")
        controller.play(listOf(duplicate, media("middle"), duplicate), startIndex = 2).getOrThrow()
        val activeQueueId = controller.state.value.current?.queueId

        controller.move(0, 1)

        assertEquals(activeQueueId, controller.state.value.current?.queueId)
        assertEquals(3, controller.state.value.items.map { it.queueId }.distinct().size)
    }

    private fun media(id: String) = MediaItem(MediaId(id), "file:///$id.mp3", id, MediaKind.AUDIO)
}

private class FakeQueueEngine : PlayerEngine {
    override val id = EngineId.MPV
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    override val state = mutableState.asStateFlow()
    override val capabilities = EngineCapabilities(
        localAudio = true, localVideo = true, httpStreaming = true, hardwareDecoding = true,
        softwareDecoding = true, playbackSpeed = true, audioTrackSelection = true,
        videoTrackSelection = true, embeddedSubtitles = true, externalSubtitles = true, hdr = true,
    )
    override val audioEnhancementState = MutableStateFlow(AudioEnhancementState(capabilities.audioEnhancements)).asStateFlow()
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

    fun complete() {
        mutableState.value = state.value.copy(status = PlaybackStatus.Completed)
    }
}
