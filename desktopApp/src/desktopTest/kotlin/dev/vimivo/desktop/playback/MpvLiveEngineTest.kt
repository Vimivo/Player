package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the real libmpv with the repository's test media. Skipped where libmpv is not installed
 * (for example Windows unit-test runs, which only get libmpv inside the packaged app).
 */
class MpvLiveEngineTest {
    private val media = File("../testmedia/track2_lunar.mp3").absoluteFile

    @Test
    fun amplifiesToTwoHundredPercentAndRejectsMore() = withLiveEngine { engine ->
        engine.setVolume(2f).getOrThrow()
        val amplified = awaitState(engine) { kotlin.math.abs(it.volume - 2f) < 0.01f }
        assertEquals(2f, amplified.volume, 0.01f)

        assertTrue(engine.setVolume(2.01f).isFailure)
    }

    private fun withLiveEngine(block: suspend (MpvPlayerEngine) -> Unit) {
        if (!media.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val engine = MpvPlayerEngine()
        try {
            runBlocking {
                val item = MediaItem(MediaId("live"), media.toPath().toUri().toString(), "live", MediaKind.AUDIO)
                engine.open(item).getOrThrow()
                engine.setVolume(0f).getOrThrow()
                engine.play().getOrThrow()
                awaitState(engine) { it.durationMillis != null }
                block(engine)
            }
        } finally {
            engine.close()
        }
    }

    private suspend fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean): PlaybackState =
        withTimeout(TIMEOUT_MILLIS) { engine.state.first(predicate) }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
