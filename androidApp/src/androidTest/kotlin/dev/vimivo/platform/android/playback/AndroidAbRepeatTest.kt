package dev.vimivo.platform.android.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * V1 #6 on Android with real players: playback stays inside an A–B region and wraps back to A again and again, moving B
 * while playing moves the loop, and with no region it plays on past B (the control). Both engines are covered: Media3
 * (which has no A–B loop of its own) and the libmpv fallback.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAbRepeatTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val settings: SettingsRepository = KoinJavaComponent.get(SettingsRepository::class.java)

    @Test
    fun media3StaysInsideTheRegionAndWrapsToA() = onMedia3 { engine, position ->
        loopStaysInside(engine, position)
    }

    @Test
    fun media3MovingBMovesTheLoopAndNoRegionPlaysPastB() = onMedia3 { engine, position ->
        movingBMovesTheLoop(engine, position)
    }

    @Test
    fun libmpvStaysInsideTheRegionAndWrapsToA() = onLibmpv { engine, position ->
        loopStaysInside(engine, position)
    }

    @Test
    fun libmpvMovingBMovesTheLoopAndNoRegionPlaysPastB() = onLibmpv { engine, position ->
        movingBMovesTheLoop(engine, position)
    }

    private fun loopStaysInside(engine: PlayerEngine, position: () -> Long) {
        assertTrue(engine.setABRepeat(ABRepeatRange(A, B, enabled = true)).isSuccess)
        assertTrue(engine.seekTo(A).isSuccess)
        awaitPosition(position) { it in (A - SLACK)..(B + SLACK) }
        val positions = sample(position, SAMPLE_MILLIS)
        assertTrue("left the region: ${positions.max()}", positions.all { it in (A - SLACK)..(B + SLACK) })
        val wraps = positions.zipWithNext().count { (before, after) -> after < before - WRAP_DROP }
        assertTrue("expected at least two wraps in ${SAMPLE_MILLIS / 1000}s, saw $wraps", wraps >= 2)
    }

    private fun movingBMovesTheLoop(engine: PlayerEngine, position: () -> Long) {
        engine.setABRepeat(ABRepeatRange(A, B, enabled = true))
        engine.seekTo(A)
        awaitPosition(position) { it in (A - SLACK)..(B + SLACK) }
        sample(position, SETTLE_MILLIS)
        engine.setABRepeat(ABRepeatRange(A, MOVED_B, enabled = true))
        val moved = sample(position, SAMPLE_MILLIS)
        assertTrue("the loop did not extend: max ${moved.max()}", moved.max() > B + SLACK)
        assertTrue("left the moved region: ${moved.max()}", moved.all { it <= MOVED_B + SLACK })
        engine.setABRepeat(ABRepeatRange())
        val free = sample(position, SAMPLE_MILLIS)
        assertTrue("control: playback should pass B, max ${free.max()}", free.max() > MOVED_B + SLACK)
    }

    private fun sample(position: () -> Long, millis: Long): List<Long> {
        val samples = mutableListOf<Long>()
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            samples += position()
            Thread.sleep(POLL_MILLIS)
        }
        return samples
    }

    private fun awaitPosition(position: () -> Long, accept: (Long) -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (!accept(position()) && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    }

    private fun onMedia3(block: (PlayerEngine, () -> Long) -> Unit) {
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            start(engine)
            block(MainThreadEngine(engine, instrumentation)) { onMain { engine.player.currentPosition } }
        } finally {
            onMain { engine.close() }
        }
    }

    private fun onLibmpv(block: (PlayerEngine, () -> Long) -> Unit) {
        assumeTrue("libmpv is not in this APK", LibmpvNative.available)
        LibmpvPlayerEngine(context, settings, File(context.cacheDir, "mpv-ab.log")).use { engine ->
            start(engine)
            block(engine) { engine.state.value.positionMillis.takeIf { it > 0 } ?: 0L }
        }
    }

    private fun start(engine: PlayerEngine) {
        val file = File(context.cacheDir, "mpris_check.mp3")
        instrumentation.context.assets.open("media/mpris_check.mp3").use { input -> file.outputStream().use(input::copyTo) }
        val item = MediaItem(MediaId("ab"), file.toURI().toString(), "ab", MediaKind.AUDIO, fileName = file.name)
        runBlocking {
            if (engine is Media3PlayerEngine) {
                onMain { runBlocking { engine.open(item).getOrThrow() } }
                onMain { engine.setVolume(0f).getOrThrow() }
                onMain { engine.play().getOrThrow() }
            } else {
                engine.open(item).getOrThrow()
                engine.setVolume(0f).getOrThrow()
                engine.play().getOrThrow()
            }
            withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing && it.positionMillis > 300 } }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    /** Runs the engine's calls on the main thread, where ExoPlayer lives; everything else delegates unchanged. */
    private class MainThreadEngine(
        private val engine: Media3PlayerEngine,
        private val instrumentation: android.app.Instrumentation,
    ) : PlayerEngine by engine {
        private fun <T> main(block: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            return checkNotNull(result).getOrThrow()
        }

        override fun setABRepeat(range: ABRepeatRange) = main { engine.setABRepeat(range) }
        override fun seekTo(positionMillis: Long) = main { engine.seekTo(positionMillis) }
    }

    private companion object {
        const val A = 2_000L
        const val B = 4_000L
        const val MOVED_B = 6_000L
        const val SLACK = 400L
        const val WRAP_DROP = 500L
        const val SAMPLE_MILLIS = 7_000L
        const val SETTLE_MILLIS = 1_000L
        const val POLL_MILLIS = 30L
        const val TIMEOUT = 20_000L
    }
}
