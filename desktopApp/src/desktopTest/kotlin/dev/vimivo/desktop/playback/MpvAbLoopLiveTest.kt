package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 #6 with the real libmpv: playback stays inside an A–B region and wraps back to A, moving an end while playing
 * takes effect, and clearing the region lets playback run on. Skipped where libmpv or the media is unavailable.
 */
class MpvAbLoopLiveTest {
    private val track = File("../testmedia/track1_solaris.flac").absoluteFile

    @Test
    fun playbackStaysInsideTheRegionAndWrapsToA() = withEngine { engine ->
        engine.setABRepeat(ABRepeatRange(A, B, enabled = true)).getOrThrow()
        engine.seekTo(A).getOrThrow()
        awaitPosition(engine)
        val positions = sample(engine, SAMPLE_MILLIS)
        assertTrue(positions.all { it in (A - SLACK)..(B + SLACK) }, "left the region: ${positions.max()}")
        val wraps = positions.zipWithNext().count { (before, after) -> after < before - WRAP_DROP }
        assertTrue(wraps >= 2, "expected at least two wraps in ${SAMPLE_MILLIS / 1000}s, saw $wraps")
    }

    @Test
    fun movingTheEndWhilePlayingMovesTheLoop() = withEngine { engine ->
        engine.setABRepeat(ABRepeatRange(A, B, enabled = true)).getOrThrow()
        engine.seekTo(A).getOrThrow()
        sample(engine, SETTLE_MILLIS)
        engine.setABRepeat(ABRepeatRange(A, MOVED_B, enabled = true)).getOrThrow()
        val positions = sample(engine, SAMPLE_MILLIS)
        assertTrue(positions.max() > B + SLACK, "the loop did not extend: max ${positions.max()}")
        assertTrue(positions.all { it <= MOVED_B + SLACK }, "left the moved region: ${positions.max()}")
    }

    @Test
    fun withoutARegionPlaybackRunsPastB() = withEngine { engine ->
        engine.seekTo(A).getOrThrow()
        val positions = sample(engine, SAMPLE_MILLIS)
        assertTrue(positions.max() > B + SLACK, "control: playback should pass B, max ${positions.max()}")
    }

    @Test
    fun aNewFileDoesNotInheritTheRegion() = withEngine { engine ->
        engine.setABRepeat(ABRepeatRange(A, B, enabled = true)).getOrThrow()
        runBlocking { engine.open(item(), 0).getOrThrow() }
        assertEquals(ABRepeatRange(), engine.state.value.abRepeat)
    }

    /** The reported position follows a seek within one poll; wait for it so the first sample is not stale. */
    private fun awaitPosition(engine: MpvPlayerEngine) = runBlocking {
        withTimeout(TIMEOUT) { engine.state.first { it.positionMillis >= A - SLACK } }
    }

    private fun sample(engine: MpvPlayerEngine, millis: Long): List<Long> = runBlocking {
        val samples = mutableListOf<Long>()
        val end = System.nanoTime() + millis * NANOS_PER_MILLI
        while (System.nanoTime() < end) {
            samples += engine.state.value.positionMillis
            delay(POLL_MILLIS)
        }
        samples
    }

    private fun item(): MediaItem {
        val uri = track.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, track.nameWithoutExtension, MediaKind.AUDIO, fileName = track.name)
    }

    private fun withEngine(block: (MpvPlayerEngine) -> Unit) {
        if (!track.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            engine.setVolume(0f)
            runBlocking {
                engine.open(item(), 0).getOrThrow()
                engine.play().getOrThrow()
                withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing } }
            }
            block(engine)
        }
    }

    private companion object {
        const val A = 1_000L
        const val B = 2_500L
        const val MOVED_B = 4_000L
        const val SLACK = 400L
        const val WRAP_DROP = 500L
        const val SAMPLE_MILLIS = 6_000L
        const val SETTLE_MILLIS = 1_000L
        const val POLL_MILLIS = 50L
        const val TIMEOUT = 15_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
