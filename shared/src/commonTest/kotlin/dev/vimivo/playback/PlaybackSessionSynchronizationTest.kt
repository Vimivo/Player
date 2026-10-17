@file:Suppress("ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionSynchronizationTest {

    private class SynchronizedTestEngine : PlayerEngine {
        override val id = EngineId.MPV
        val mutableState = MutableStateFlow(PlaybackState(engine = id))
        override val state = mutableState.asStateFlow()
        override val capabilities = EngineCapabilities(
            localAudio = true, localVideo = true, httpStreaming = true, hardwareDecoding = true,
            softwareDecoding = true, playbackSpeed = true, audioTrackSelection = true,
            videoTrackSelection = true, embeddedSubtitles = true, externalSubtitles = true, hdr = true,
        )
        override val audioEnhancementState = MutableStateFlow(
            AudioEnhancementState(capabilities.audioEnhancements),
        ).asStateFlow()

        val openedItems = mutableListOf<String>()
        var playCount = 0
        var stopCount = 0
        var openDeferred: CompletableDeferred<Unit>? = null

        override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED

        override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
            openDeferred?.await()
            openedItems += media.id.value
            mutableState.value = PlaybackState(
                engine = id,
                media = media,
                status = PlaybackStatus.Ready,
                positionMillis = startPositionMillis,
            )
            return Result.success(Unit)
        }

        override fun play(): Result<Unit> {
            playCount++
            mutableState.value = state.value.copy(status = PlaybackStatus.Playing)
            return Result.success(Unit)
        }

        override fun pause(): Result<Unit> {
            mutableState.value = state.value.copy(status = PlaybackStatus.Paused)
            return Result.success(Unit)
        }

        override fun stop(): Result<Unit> {
            stopCount++
            mutableState.value = state.value.copy(status = PlaybackStatus.Idle, media = null)
            return Result.success(Unit)
        }

        override fun seekTo(positionMillis: Long) = Result.success(Unit)
        override fun setPlaybackSpeed(speed: Float) = Result.success(Unit)
        override fun setVolume(volume: Float) = Result.success(Unit)
        override fun selectAudioTrack(trackId: String?) = Result.success(Unit)
        override fun selectVideoTrack(trackId: String?) = Result.success(Unit)
        override fun selectSubtitleTrack(trackId: String?) = Result.success(Unit)
        override fun close() = Unit

        fun emitCompleted(media: MediaItem? = state.value.media) {
            mutableState.value = state.value.copy(status = PlaybackStatus.Completed, media = media)
        }
    }

    private fun media(id: String) = MediaItem(MediaId(id), "file:///$id.mp3", id, MediaKind.AUDIO)

    @Test
    fun scenarioA_rapidItemSwitchingRejectsDelayedCallback() = runTest {
        val engine = SynchronizedTestEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val songA = media("songA")
        val songB = media("songB")
        val items = listOf(songA, songB)

        // Suspend songA opening
        val deferredA = CompletableDeferred<Unit>()
        engine.openDeferred = deferredA

        backgroundScope.launch { controller.play(items, startIndex = 0) }
        runCurrent()

        // Before songA finishes, user selects songB
        engine.openDeferred = null // songB opens immediately
        backgroundScope.launch { controller.playAt(1) }
        runCurrent()

        // Now complete deferred A (delayed callback from songA)
        deferredA.complete(Unit)
        runCurrent()

        // Verify songB is the active item and session
        assertEquals("songB", controller.state.value.currentMedia?.id?.value)
        assertEquals(1, controller.state.value.currentIndex)
    }

    @Test
    fun scenarioB_duplicateCompletionEventsAdvanceQueueOnlyOnce() = runTest {
        val engine = SynchronizedTestEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("track1"), media("track2"), media("track3"))

        controller.play(items, startIndex = 0).getOrThrow()
        runCurrent()
        assertEquals("track1", controller.state.value.currentMedia?.id?.value)

        // First completion event
        engine.emitCompleted(items[0])
        runCurrent()
        assertEquals("track2", controller.state.value.currentMedia?.id?.value)

        // Duplicate completion event for track1 arrives
        engine.emitCompleted(items[0])
        runCurrent()

        // Must still be on track2, NOT skipped to track3
        assertEquals("track2", controller.state.value.currentMedia?.id?.value)
        assertEquals(1, controller.state.value.currentIndex)
    }

    @Test
    fun scenarioC_staleCompletionCallbackFromOldTrackIgnored() = runTest {
        val engine = SynchronizedTestEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("first"), media("second"), media("third"))

        controller.play(items, startIndex = 0).getOrThrow()
        runCurrent()

        // User manually skips to track 2 (second)
        controller.next().getOrThrow()
        runCurrent()
        assertEquals("second", controller.state.value.currentMedia?.id?.value)

        // A stale completion event for "first" arrives late
        engine.emitCompleted(items[0])
        runCurrent()

        // Must NOT advance to "third"
        assertEquals("second", controller.state.value.currentMedia?.id?.value)
        assertEquals(1, controller.state.value.currentIndex)
    }

    @Test
    fun scenarioD_stopWhileOpeningCancelsSession() = runTest {
        val engine = SynchronizedTestEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("slow1"))

        val deferred = CompletableDeferred<Unit>()
        engine.openDeferred = deferred

        backgroundScope.launch { controller.play(items) }
        runCurrent()

        // Stop/clear queue while open is pending
        controller.clear()
        runCurrent()

        deferred.complete(Unit)
        runCurrent()

        assertTrue(controller.state.value.items.isEmpty())
        assertTrue(engine.stopCount > 0)
    }

    @Test
    fun scenarioE_repeatModesSingleAndMultiItem() = runTest {
        val engine = SynchronizedTestEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val singleItem = listOf(media("solo"))

        // Repeat OFF: single track stops engine on completion
        controller.play(singleItem).getOrThrow()
        runCurrent()
        assertEquals("solo", controller.state.value.currentMedia?.id?.value)

        engine.emitCompleted(singleItem[0])
        runCurrent()
        assertTrue(engine.stopCount > 0)

        // Repeat ONE: replays the track on completion
        controller.cycleRepeat() // ONE
        assertEquals(QueueRepeatMode.ONE, controller.state.value.repeatMode)
        controller.play(singleItem).getOrThrow()
        runCurrent()
        val openCountBefore = engine.openedItems.size
        engine.emitCompleted(singleItem[0])
        runCurrent()
        assertEquals("solo", controller.state.value.currentMedia?.id?.value)
        assertEquals(openCountBefore + 1, engine.openedItems.size)

        // Repeat ALL: loops back to start on completion of last item
        val multiItems = listOf(media("first"), media("last"))
        controller.cycleRepeat() // ALL
        assertEquals(QueueRepeatMode.ALL, controller.state.value.repeatMode)
        controller.play(multiItems, startIndex = 1).getOrThrow()
        runCurrent()
        assertEquals("last", controller.state.value.currentMedia?.id?.value)

        val multiOpenCountBefore = engine.openedItems.size
        engine.emitCompleted(multiItems[1])
        runCurrent()
        assertEquals("first", controller.state.value.currentMedia?.id?.value)
        assertEquals(multiOpenCountBefore + 1, engine.openedItems.size)
    }

    @Test
    fun scenarioF_shuffleTogglePreservesActiveTrackAndRestoresSourceOrder() = runTest {
        val engine = SynchronizedTestEngine()
        val controller = PlaybackQueueController(engine, backgroundScope, random = Random(42))
        val items = listOf(media("a"), media("b"), media("c"), media("d"), media("e"))

        controller.play(items, startIndex = 2).getOrThrow()
        runCurrent()
        assertEquals("c", controller.state.value.currentMedia?.id?.value)

        // Enable shuffle
        controller.toggleShuffle()
        assertTrue(controller.state.value.shuffleEnabled)
        assertEquals("c", controller.state.value.currentMedia?.id?.value)
        assertEquals(5, controller.state.value.items.size)

        // Disable shuffle
        controller.toggleShuffle()
        assertFalse(controller.state.value.shuffleEnabled)
        assertEquals("c", controller.state.value.currentMedia?.id?.value)
        assertEquals(listOf("a", "b", "c", "d", "e"), controller.state.value.items.map { it.media.id.value })
        assertEquals(2, controller.state.value.currentIndex)
    }
}
