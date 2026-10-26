package dev.vimivo.platform.android.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1 #11 on Media3: with the next item prepared, ExoPlayer starts it by itself and the engine moves on to it without
 * the first item ever completing or the second one going through preparing. Without preparation the first item
 * completes and the second has to be opened (the negative control). ExoPlayer lives on the main thread, so every
 * player call runs there while the test thread waits on the engine's state.
 */
@RunWith(AndroidJUnit4::class)
class Media3GaplessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val collectors = mutableListOf<CoroutineScope>()

    @Test
    fun theNextItemStartsOnItsOwnWithoutCompletingTheFirst() = withEngine { engine ->
        val first = item("track1_solaris.flac", "first")
        val second = item("track3_aurora.flac", "second")
        val seen = collect(engine)
        onMain { runBlocking { engine.open(first).getOrThrow() } }
        onMain { engine.setVolume(0f).getOrThrow() }
        onMain { engine.play().getOrThrow() }
        awaitState(engine) { it.status == PlaybackStatus.Playing }
        onMain { engine.prepareNext(second).getOrThrow() }
        val duration = awaitState(engine) { (it.durationMillis ?: 0) > 0 }.durationMillis ?: 0
        onMain { engine.seekTo(duration - NEAR_END_MILLIS).getOrThrow() }
        val moved = awaitState(engine) { it.media?.id == second.id && it.status == PlaybackStatus.Playing }
        assertEquals("second", moved.media?.id?.value)
        val history = synchronized(seen) { seen.toList() }
        assertFalse("the first item completed: $history", history.any { it.second == PlaybackStatus.Completed })
        val onSecond = history.filter { it.first == "second" }.map { it.second }
        assertFalse("the second item was opened afresh: $onSecond", onSecond.any { it == PlaybackStatus.Preparing })
    }

    @Test
    fun withoutPreparationTheFirstItemCompletes() = withEngine { engine ->
        val first = item("track1_solaris.flac", "first")
        val seen = collect(engine)
        onMain { runBlocking { engine.open(first).getOrThrow() } }
        onMain { engine.setVolume(0f).getOrThrow() }
        onMain { engine.play().getOrThrow() }
        val duration = awaitState(engine) { (it.durationMillis ?: 0) > 0 }.durationMillis ?: 0
        onMain { engine.seekTo(duration - NEAR_END_MILLIS).getOrThrow() }
        awaitState(engine) { it.status == PlaybackStatus.Completed }
        assertTrue(synchronized(seen) { seen.any { it.second == PlaybackStatus.Completed } })
    }

    @Test
    fun videoIsNeverPrepared() = withEngine { engine ->
        val clip = MediaItem(MediaId("clip"), "file:///clip.mp4", "clip", MediaKind.VIDEO)
        onMain { runBlocking { engine.open(item("track1_solaris.flac", "first")).getOrThrow() } }
        assertTrue(onMain { engine.prepareNext(clip) }.isFailure)
        assertTrue(onMain { engine.prepareNext(null) }.isSuccess)
    }

    private fun collect(engine: Media3PlayerEngine): MutableList<Pair<String?, PlaybackStatus>> {
        val seen = mutableListOf<Pair<String?, PlaybackStatus>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { engine.state.collect { synchronized(seen) { seen += it.media?.id?.value to it.status } } }
        collectors += scope
        return seen
    }

    private fun awaitState(engine: Media3PlayerEngine, predicate: (PlaybackState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun item(asset: String, id: String): MediaItem {
        val file = File(context.cacheDir, asset)
        instrumentation.context.assets.open("media/$asset").use { input -> file.outputStream().use(input::copyTo) }
        return MediaItem(MediaId(id), file.toURI().toString(), id, MediaKind.AUDIO, fileName = file.name)
    }

    private fun withEngine(block: (Media3PlayerEngine) -> Unit) {
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            block(engine)
        } finally {
            collectors.forEach { it.cancel() }
            onMain { engine.close() }
        }
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val NEAR_END_MILLIS = 1_500L
    }
}
