@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.vimivo.platform.android.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaItem as VimivoMediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.PlayerEngineFactory
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.PlaybackSleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1 #11, the queue side: the session adapter asks the engine to prepare the file that follows the current one, follows
 * the engine when it starts that file by itself (without opening it again), and prepares nothing where the engine would
 * otherwise run past the end of what the user asked for. The engine is a fake that records every call.
 */
@RunWith(AndroidJUnit4::class)
class SessionPlayerAdapterGaplessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val engine = HandoffEngine()
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private lateinit var sleepTimer: PlaybackSleepTimer
    private lateinit var adapter: SessionPlayerAdapter

    @Before
    fun setUp() = onMain {
        val factory = object : PlayerEngineFactory {
            override val id = EngineId.MEDIA3
            override fun supportFor(media: VimivoMediaItem) = SupportLevel.PREFERRED
            override fun create(): PlayerEngine = engine
        }
        sleepTimer = PlaybackSleepTimer(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            System::currentTimeMillis,
            DiagnosticsSleepTimerPlayer(PlaybackDiagnostics()),
        )
        adapter = SessionPlayerAdapter(
            PlaybackEngineManager(mapOf(EngineId.MEDIA3 to factory), scope),
            scope,
            PlaybackStateStore(context),
            sleepTimer,
        )
    }

    @After
    fun tearDown() = onMain {
        sleepTimer.close()
        scope.cancel()
    }

    @Test
    fun theFollowingItemIsPreparedAndTheQueueFollowsTheEngine() {
        play("one", "two", "three")
        await("the first item is opened") { engine.opened == listOf("one") }
        engine.startPlaying()
        await("the second item is prepared") { engine.prepared.lastOrNull() == "two" }
        engine.handOver("two")
        await("the queue follows to the second item") { onMain { adapter.currentMediaItemIndex } == 1 }
        assertEquals("the engine started it, the queue must not open it again", listOf("one"), engine.opened)
        await("the third item is prepared next") { engine.prepared.lastOrNull() == "three" }
    }

    @Test
    fun nothingIsPreparedForStopAfterThisTrack() {
        onMain { sleepTimer.afterCurrent() }
        play("one", "two")
        await("the first item is opened") { engine.opened == listOf("one") }
        engine.startPlaying()
        Thread.sleep(SETTLE_MILLIS)
        assertTrue("prepared ${engine.prepared}", engine.prepared.none { it != null })
    }

    @Test
    fun nothingIsPreparedForRepeatOne() {
        onMain { adapter.repeatMode = Player.REPEAT_MODE_ONE }
        play("one", "two")
        await("the first item is opened") { engine.opened == listOf("one") }
        engine.startPlaying()
        Thread.sleep(SETTLE_MILLIS)
        assertTrue("prepared ${engine.prepared}", engine.prepared.none { it != null })
    }

    private fun play(vararg ids: String) = onMain {
        adapter.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp3").build() })
        adapter.prepare()
        adapter.play()
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        assertTrue("timed out waiting for: $what (opened ${engine.opened}, prepared ${engine.prepared})", false)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    /** An engine that records `open` and `prepareNext` and can start the prepared item by itself. */
    private class HandoffEngine : PlayerEngine {
        override val id = EngineId.MEDIA3
        private val mutableState = MutableStateFlow(PlaybackState(engine = id))
        override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
        override val capabilities = Media3PlayerEngine.CAPABILITIES
        override val audioEnhancementState = MutableStateFlow(AudioEnhancementState(capabilities.audioEnhancements))
        val opened = java.util.concurrent.CopyOnWriteArrayList<String>()
        val prepared = java.util.concurrent.CopyOnWriteArrayList<String?>()
        private val items = mutableMapOf<String, VimivoMediaItem>()

        override fun supportFor(media: VimivoMediaItem) = SupportLevel.PREFERRED

        override fun prepareNext(media: VimivoMediaItem?): Result<Unit> {
            prepared += media?.id?.value
            if (media != null) items[media.id.value] = media
            return Result.success(Unit)
        }

        override suspend fun open(media: VimivoMediaItem, startPositionMillis: Long): Result<Unit> {
            check(media.kind == MediaKind.AUDIO)
            opened += media.id.value
            items[media.id.value] = media
            mutableState.value = PlaybackState(engine = id, media = media, status = PlaybackStatus.Ready)
            return Result.success(Unit)
        }

        fun startPlaying() {
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Playing)
        }

        /** The engine moved on to the prepared item by itself. */
        fun handOver(itemId: String) {
            mutableState.value = PlaybackState(engine = id, media = items.getValue(itemId), status = PlaybackStatus.Playing)
        }

        override fun play(): Result<Unit> = Result.success(Unit).also { startPlaying() }
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

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
        const val SETTLE_MILLIS = 1_000L
    }
}
