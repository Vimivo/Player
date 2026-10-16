@file:Suppress(
    "ArgumentListWrapping", "EmptyFunctionBlock", "FunctionNaming", "ImportOrdering",
    "LongMethod", "MaximumLineLength", "MaxLineLength", "NoUnusedImports", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.QueueItem
import dev.vimivo.playback.QueueRepeatMode
import dev.vimivo.playback.SupportLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueRaceConditionTest {

    private class RaceEngine : PlayerEngine {
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
        var playCalled = 0
        var pauseCalled = 0
        var stopCalled = 0

        override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED

        override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
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
            playCalled++
            mutableState.value = state.value.copy(status = PlaybackStatus.Playing)
            return Result.success(Unit)
        }

        override fun pause(): Result<Unit> {
            pauseCalled++
            mutableState.value = state.value.copy(status = PlaybackStatus.Paused)
            return Result.success(Unit)
        }

        override fun stop(): Result<Unit> {
            stopCalled++
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

        fun triggerCompletion() {
            mutableState.value = state.value.copy(status = PlaybackStatus.Completed)
        }
    }

    private fun media(id: String) = MediaItem(MediaId(id), "file:///$id.mp3", id, MediaKind.AUDIO)

    @Test
    fun handlesRemovalOfAdjacentTracksWithoutCorruptingState() = runTest {
        val engine = RaceEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("songA"), media("songB"), media("songC"))

        controller.play(items, startIndex = 1).getOrThrow()
        runCurrent()
        assertEquals("songB", controller.state.value.currentMedia?.id?.value)
        assertEquals(1, controller.state.value.currentIndex)

        // Attempting to remove active item is safely protected / ignored
        controller.remove(1)
        runCurrent()
        assertEquals("songB", controller.state.value.currentMedia?.id?.value)
        assertEquals(3, controller.state.value.items.size)

        // Removing upcoming item (index 2: songC)
        controller.remove(2)
        runCurrent()
        assertEquals(2, controller.state.value.items.size)
        assertEquals(1, controller.state.value.currentIndex)
        assertEquals("songB", controller.state.value.currentMedia?.id?.value)

        // Removing previous item (index 0: songA) shifts currentIndex to 0
        controller.remove(0)
        runCurrent()
        assertEquals(1, controller.state.value.items.size)
        assertEquals(0, controller.state.value.currentIndex)
        assertEquals("songB", controller.state.value.currentMedia?.id?.value)
    }

    @Test
    fun handlesRapidNextWhileTrackCompletes() = runTest {
        val engine = RaceEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("1"), media("2"), media("3"), media("4"))

        controller.play(items, startIndex = 0).getOrThrow()
        runCurrent()
        assertEquals("1", controller.state.value.currentMedia?.id?.value)

        // Simultaneously trigger next() and completion event
        controller.next()
        engine.triggerCompletion()
        runCurrent()

        // Should be at track 2 or 3 without crash or corrupt state
        val currentId = controller.state.value.currentMedia?.id?.value
        assertTrue(currentId == "2" || currentId == "3")
        assertEquals(4, controller.state.value.items.size)
    }

    @Test
    fun duplicateOccurrencesMaintainDistinctQueueIdentities() = runTest {
        val engine = RaceEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val songA = media("songA")
        val songB = media("songB")
        val items = listOf(songA, songB, songA, songB)

        controller.play(items, startIndex = 0).getOrThrow()
        runCurrent()

        val queueIds = controller.state.value.items.map { it.queueId }
        assertEquals(4, queueIds.toSet().size, "All queue instances must have unique occurrence IDs")

        // Removing the item at index 1 (songB) leaves the remaining 3 items intact
        controller.remove(1)
        runCurrent()

        assertEquals(3, controller.state.value.items.size)
        assertEquals(listOf("songA", "songA", "songB"), controller.state.value.items.map { it.media.id.value })
        assertEquals(queueIds[2], controller.state.value.items[1].queueId)
    }

    @Test
    fun reordersQueueItemsWhilePlayingWithoutAffectingCurrentTrack() = runTest {
        val engine = RaceEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("track1"), media("track2"), media("track3"), media("track4"))

        controller.play(items, startIndex = 1).getOrThrow()
        runCurrent()
        assertEquals("track2", controller.state.value.currentMedia?.id?.value)

        // Move index 3 (track4) to index 0
        controller.move(from = 3, to = 0)
        runCurrent()

        // Current media must remain track2
        assertEquals("track2", controller.state.value.currentMedia?.id?.value)
        assertEquals(
            listOf("track4", "track1", "track2", "track3"),
            controller.state.value.items.map { it.media.id.value },
        )
    }

    @Test
    fun clearingQueueStopsPlaybackCleanly() = runTest {
        val engine = RaceEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val items = listOf(media("x"), media("y"))

        controller.play(items, startIndex = 0).getOrThrow()
        runCurrent()
        assertEquals("x", controller.state.value.currentMedia?.id?.value)

        controller.clear()
        runCurrent()

        assertNull(controller.state.value.currentMedia)
        assertTrue(controller.state.value.items.isEmpty())
        assertEquals(-1, controller.state.value.currentIndex)
    }

    @Test
    fun restoresPausedQueueStateWithoutAutoplay() = runTest {
        val engine = RaceEngine()
        val controller = PlaybackQueueController(engine, backgroundScope)
        val song1 = media("restored1")
        val song2 = media("restored2")

        val snapshot = PlaybackQueueSnapshot(
            sourceItems = listOf(QueueItem(10, song1), QueueItem(20, song2)),
            playbackItems = listOf(QueueItem(10, song1), QueueItem(20, song2)),
            currentQueueId = 20,
            shuffleEnabled = false,
            repeatMode = QueueRepeatMode.OFF,
            sourceLabel = "Saved Session",
        )

        controller.restore(snapshot)
        runCurrent()

        assertEquals(2, controller.state.value.items.size)
        assertEquals("restored2", controller.state.value.currentMedia?.id?.value)
        assertEquals(1, controller.state.value.currentIndex)
        assertNotNull(controller.state.value.current)
        assertEquals(20L, controller.state.value.current?.queueId)
        // Autoplay should NOT have been triggered during restore
        assertEquals(0, engine.playCalled, "Restore must not automatically invoke engine.play()")
    }
}
