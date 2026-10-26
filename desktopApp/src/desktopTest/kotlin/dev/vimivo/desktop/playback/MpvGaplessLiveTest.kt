package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 #11 with the real libmpv: the queue prepares the next audio file, libmpv starts it on its own, and the audio
 * never goes idle across the boundary. The negative control runs the same queue without the preparation (the engine
 * refuses `prepareNext`), which is the old open-after-completion path. Skipped where libmpv or the media is missing.
 */
class MpvGaplessLiveTest {
    private val first = File("../testmedia/track1_solaris.flac").absoluteFile
    private val second = File("../testmedia/track3_aurora.flac").absoluteFile

    @Test
    fun theNextFileStartsWithoutTheAudioGoingIdle() = withQueue(prepare = true) { engine, queue ->
        val seen = mutableListOf<Pair<String?, PlaybackStatus>>()
        val collector = collect(engine, seen)
        val idleMillis = crossTheBoundary(engine)
        collector.cancel()
        val statuses = seen.filter { it.first == "second" }.map { it.second }
        assertEquals("second", queue.state.value.currentMedia?.id?.value)
        assertTrue(idleMillis < GAPLESS_MAX_IDLE_MILLIS, "audio was idle for $idleMillis ms across the boundary")
        val interruptions = setOf(PlaybackStatus.Preparing, PlaybackStatus.Ready, PlaybackStatus.Paused)
        assertTrue(statuses.none { it in interruptions }, "the second file went through $statuses")
        assertTrue(seen.none { it.second == PlaybackStatus.Completed }, "the first file reported Completed")
    }

    @Test
    fun withoutPreparationThereIsAGapAtTheBoundary() = withQueue(prepare = false) { engine, queue ->
        val idleMillis = crossTheBoundary(engine)
        assertEquals("second", queue.state.value.currentMedia?.id?.value)
        assertTrue(idleMillis >= CONTROL_MIN_IDLE_MILLIS, "control: expected a gap, idle only $idleMillis ms")
    }

    /** Seeks near the end of the first file and returns the longest run of `core-idle` until the second plays. */
    private fun crossTheBoundary(engine: MpvPlayerEngine): Long = runBlocking {
        val duration = withTimeout(TIMEOUT) { engine.state.first { (it.durationMillis ?: 0) > 0 } }.durationMillis ?: 0
        engine.seekTo(duration - NEAR_END_MILLIS).getOrThrow()
        val target = (duration - NEAR_END_MILLIS) / 1_000.0
        withTimeout(TIMEOUT) {
            while ((engine.readProperty("time-pos")?.toDoubleOrNull() ?: 0.0) < target) delay(SAMPLE_MILLIS)
        }
        var longest = 0L
        var idleSince: Long? = null
        val deadline = System.nanoTime() + TIMEOUT * NANOS_PER_MILLI
        var playedSecond = 0L
        while (System.nanoTime() < deadline && playedSecond < SECOND_FILE_PLAYED_MILLIS) {
            val now = System.nanoTime()
            val idle = engine.readProperty("core-idle") == "yes"
            val onSecond = engine.readProperty("path")?.endsWith(second.name) == true
            if (idle) {
                idleSince = idleSince ?: now
                longest = maxOf(longest, (now - idleSince) / NANOS_PER_MILLI)
            } else {
                idleSince = null
                if (onSecond) playedSecond += SAMPLE_MILLIS
            }
            delay(SAMPLE_MILLIS)
        }
        assertTrue(playedSecond >= SECOND_FILE_PLAYED_MILLIS, "the second file never played")
        longest
    }

    private fun collect(engine: MpvPlayerEngine, into: MutableList<Pair<String?, PlaybackStatus>>) =
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            engine.state.collect { synchronized(into) { into += it.media?.id?.value to it.status } }
        }

    private fun item(file: File, id: String): MediaItem {
        val uri = file.toPath().toUri().toString()
        return MediaItem(MediaId(id), uri, id, MediaKind.AUDIO, fileName = file.name)
    }

    private fun withQueue(prepare: Boolean, block: (MpvPlayerEngine, PlaybackQueueController) -> Unit) {
        if (!first.isFile || !second.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        MpvPlayerEngine().use { engine ->
            engine.setVolume(0f)
            val driven: PlayerEngine = if (prepare) engine else RefusesPrepare(engine)
            val queue = PlaybackQueueController(driven, scope)
            try {
                runBlocking {
                    queue.play(listOf(item(first, "first"), item(second, "second"))).getOrThrow()
                    withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing } }
                    if (prepare) {
                        withTimeout(TIMEOUT) {
                            while (engine.readProperty("playlist-count") != "2") delay(SAMPLE_MILLIS)
                        }
                    }
                }
                block(engine, queue)
            } finally {
                queue.close()
                scope.cancel()
            }
        }
    }

    /** The same engine, but it never accepts a prepared item: the queue falls back to opening after completion. */
    private class RefusesPrepare(private val engine: MpvPlayerEngine) : PlayerEngine by engine {
        override fun prepareNext(media: MediaItem?): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private companion object {
        const val TIMEOUT = 30_000L
        const val NEAR_END_MILLIS = 1_500L
        const val SAMPLE_MILLIS = 5L
        const val SECOND_FILE_PLAYED_MILLIS = 800L
        const val GAPLESS_MAX_IDLE_MILLIS = 120L
        const val CONTROL_MIN_IDLE_MILLIS = 150L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
