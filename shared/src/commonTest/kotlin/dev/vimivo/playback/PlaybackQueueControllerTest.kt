@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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

    @Test
    fun restoresSnapshotPausedWithIdentitiesShuffleAndRepeat() = runTest {
        val original = PlaybackQueueController(FakeQueueEngine(), backgroundScope, random = Random(3))
        val duplicate = media("same")
        original.play(listOf(duplicate, media("b"), duplicate, media("c")), startIndex = 2, shuffle = true, sourceLabel = "Album · Test").getOrThrow()
        original.cycleRepeat()
        original.cycleRepeat()
        val snapshot = original.snapshot()

        val engine = FakeQueueEngine()
        val restored = PlaybackQueueController(engine, backgroundScope)
        restored.restore(snapshot)
        runCurrent()

        assertTrue(engine.opened.isEmpty(), "Restoration must not open or start media")
        assertEquals(snapshot.playbackItems.map { it.queueId }, restored.state.value.items.map { it.queueId })
        assertEquals(snapshot.currentQueueId, restored.state.value.current?.queueId)
        assertTrue(restored.state.value.shuffleEnabled)
        assertEquals(QueueRepeatMode.ALL, restored.state.value.repeatMode)
        assertEquals("Album · Test", restored.state.value.sourceLabel)

        restored.toggleShuffle()
        assertEquals(snapshot.sourceItems.map { it.queueId }, restored.state.value.items.map { it.queueId })
        assertEquals(snapshot.currentQueueId, restored.state.value.current?.queueId)

        restored.playCurrent().getOrThrow()
        assertEquals(listOf("same"), engine.opened)
        restored.addToQueue(listOf(media("new")))
        assertEquals(5, restored.state.value.items.map { it.queueId }.distinct().size, "New entries must not reuse restored identities")
    }

    @Test
    fun rejectsInconsistentSnapshots() = runTest {
        val controller = PlaybackQueueController(FakeQueueEngine(), backgroundScope)
        val item = QueueItem(1, media("a"))
        assertFailsWith<IllegalArgumentException> {
            controller.restore(PlaybackQueueSnapshot(listOf(item), listOf(item, item), 1, false, QueueRepeatMode.OFF))
        }
        assertFailsWith<IllegalArgumentException> {
            controller.restore(PlaybackQueueSnapshot(listOf(item), listOf(QueueItem(2, media("b"))), 1, false, QueueRepeatMode.OFF))
        }
        assertTrue(controller.state.value.items.isEmpty())
    }

    @Test
    fun playNextKeepsSourceOrderWhenShuffled() = runTest {
        val controller = PlaybackQueueController(FakeQueueEngine(), backgroundScope, random = Random(11))
        controller.play(listOf(media("a"), media("b"), media("c"), media("d")), startIndex = 1, shuffle = true).getOrThrow()

        controller.playNext(listOf(media("next")))
        assertEquals("next", controller.state.value.items[controller.state.value.currentIndex + 1].media.id.value)

        controller.toggleShuffle()
        assertEquals(listOf("a", "b", "next", "c", "d"), controller.state.value.items.map { it.media.id.value })
        assertEquals("b", controller.state.value.currentMedia?.id?.value)
    }

    @Test
    fun queueActionsOnEmptyQueueSelectFirstEntryWithoutPlaying() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        controller.addToQueue(listOf(media("a"), media("b")))
        assertEquals(0, controller.state.value.currentIndex)
        assertTrue(engine.opened.isEmpty())

        controller.playAt(1).getOrThrow()
        assertEquals("b", controller.state.value.currentMedia?.id?.value)
        assertEquals(listOf("a"), controller.state.value.items.take(controller.state.value.currentIndex).map { it.media.id.value })
    }

    @Test
    fun retainOnlyAdvancesActiveEntryAndPreservesDuplicates() {
        val a = media("a")
        val snapshot = PlaybackQueueSnapshot(
            sourceItems = listOf(QueueItem(1, a), QueueItem(2, media("transient")), QueueItem(3, a)),
            playbackItems = listOf(QueueItem(2, media("transient")), QueueItem(3, a), QueueItem(1, a)),
            currentQueueId = 2,
            shuffleEnabled = true,
            repeatMode = QueueRepeatMode.ONE,
        )

        val retained = snapshot.retainOnly { it.id.value != "transient" }

        assertEquals(listOf(3L, 1L), retained.playbackItems.map { it.queueId })
        assertEquals(listOf(1L, 3L), retained.sourceItems.map { it.queueId })
        assertEquals(3L, retained.currentQueueId)
        assertNull(snapshot.retainOnly { false }.currentQueueId)
    }

    @Test
    fun previousRestartsTrackWhenBeyondThreshold() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        controller.play(listOf(media("a"), media("b")), startIndex = 1).getOrThrow()
        engine.setPosition(5_000L)

        controller.previous().getOrThrow()
        assertEquals("b", controller.state.value.currentMedia?.id?.value)
        assertEquals(listOf(0L), engine.seeks)
    }

    @Test
    fun previousStepsBackWhenAtOrBeforeThreshold() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        controller.play(listOf(media("a"), media("b")), startIndex = 1).getOrThrow()
        engine.setPosition(1_500L)

        controller.previous().getOrThrow()
        assertEquals("a", controller.state.value.currentMedia?.id?.value)
    }

    @Test
    fun previousWrapsToLastTrackWhenRepeatAll() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        controller.play(listOf(media("first"), media("second"), media("last")), startIndex = 0).getOrThrow()
        controller.cycleRepeat() // ONE
        controller.cycleRepeat() // ALL
        assertEquals(QueueRepeatMode.ALL, controller.state.value.repeatMode)

        controller.previous().getOrThrow()
        assertEquals("last", controller.state.value.currentMedia?.id?.value)
    }

    @Test
    fun previousSeeksToZeroAtFirstTrackWhenRepeatOff() = runTest {
        val engine = FakeQueueEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        controller.play(listOf(media("first"), media("last")), startIndex = 0).getOrThrow()
        assertEquals(QueueRepeatMode.OFF, controller.state.value.repeatMode)

        controller.previous().getOrThrow()
        assertEquals("first", controller.state.value.currentMedia?.id?.value)
        assertEquals(listOf(0L), engine.seeks)
    }

    @Test
    fun stopsEngineAndFailsWhenQueueClearedWhileOpenIsPending() = runTest {
        val engine = FakeQueueEngine()
        lateinit var controller: PlaybackQueueController
        controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("a"), media("b"))

        engine.openHook = {
            controller.clear()
        }

        val result = controller.play(items)
        runCurrent()

        assertTrue(result.isFailure, "Playback must fail when queue was cleared during open")
        assertTrue(engine.stopped, "Engine must be stopped when queue is cleared while open is pending")
        assertTrue(controller.state.value.items.isEmpty())
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
    override val audioEnhancementState = MutableStateFlow(
        AudioEnhancementState(capabilities.audioEnhancements),
    ).asStateFlow()
    val opened = mutableListOf<String>()
    val seeks = mutableListOf<Long>()
    var openHook: (() -> Unit)? = null
    var stopped = false

    override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED
    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
        openHook?.invoke()
        opened += media.id.value
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
    override fun stop(): Result<Unit> {
        stopped = true
        return Result.success(Unit)
    }
    override fun seekTo(positionMillis: Long): Result<Unit> {
        seeks += positionMillis
        mutableState.value = state.value.copy(positionMillis = positionMillis)
        return Result.success(Unit)
    }
    override fun setPlaybackSpeed(speed: Float) = Result.success(Unit)
    override fun setVolume(volume: Float) = Result.success(Unit)
    override fun selectAudioTrack(trackId: String?) = Result.success(Unit)
    override fun selectVideoTrack(trackId: String?) = Result.success(Unit)
    override fun selectSubtitleTrack(trackId: String?) = Result.success(Unit)
    override fun close() = Unit

    fun complete() {
        mutableState.value = state.value.copy(status = PlaybackStatus.Completed)
    }

    fun setPosition(pos: Long) {
        mutableState.value = state.value.copy(positionMillis = pos)
    }
}
