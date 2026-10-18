package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Q1 regression with real libmpv: starting from item 2 of a list must play 2 then 3 automatically.
 * Skipped where libmpv or the test media are unavailable.
 */
class MpvContinuousPlaybackTest {
    private val files = listOf("track1_solaris.flac", "track2_lunar.mp3", "track3_aurora.flac")
        .map { File("../testmedia/$it").absoluteFile }

    @Test
    fun startingAtTheSecondItemPlaysTheRestAutomatically() {
        if (files.any { !it.isFile } || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val engine = MpvPlayerEngine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PlaybackQueueController(engine, scope)
        try {
            runBlocking {
                engine.setVolume(0f)
                queue.play(files.map(::item), startIndex = 1).getOrThrow()
                withTimeout(START_TIMEOUT) { engine.state.first { it.media?.fileName == "track2_lunar.mp3" } }
                // Jump close to the end so the test does not wait for the whole 10-second track.
                withTimeout(START_TIMEOUT) { engine.state.first { it.durationMillis != null && it.isSeekable } }
                engine.seekTo(engine.state.value.durationMillis!! - NEAR_END_MILLIS)
                val next =
                    withTimeout(ADVANCE_TIMEOUT) { engine.state.first { it.media?.fileName == "track3_aurora.flac" } }
                assertEquals("track3_aurora.flac", next.media?.fileName)
                withTimeout(START_TIMEOUT) { queue.state.first { it.currentIndex == 2 } }
            }
        } finally {
            queue.close()
            engine.close()
            scope.cancel()
        }
    }

    private fun item(file: File): MediaItem {
        val uri = file.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, file.nameWithoutExtension, MediaKind.AUDIO, fileName = file.name)
    }

    private companion object {
        const val START_TIMEOUT = 5_000L
        const val ADVANCE_TIMEOUT = 8_000L
        const val NEAR_END_MILLIS = 1_000L
    }
}
