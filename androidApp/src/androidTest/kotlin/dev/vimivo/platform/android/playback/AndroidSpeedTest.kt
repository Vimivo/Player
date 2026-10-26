package dev.vimivo.platform.android.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * V1 #4 on Android with a real player: the speeds across the supported range are applied, so media time advances at
 * that multiple of wall-clock time, the pitch is left alone (speed changes only the tempo), and a speed outside
 * 0.25×–4× is refused. A 30 s track is long enough to measure each speed for a couple of seconds.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSpeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun mediaTimeAdvancesAtTheChosenSpeedWithoutChangingThePitch() {
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            onMain { runBlocking { engine.open(item()).getOrThrow() } }
            onMain { engine.setVolume(0f).getOrThrow() }
            onMain { engine.play().getOrThrow() }
            runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing } } }
            listOf(0.5f, 1f, 2f, 3f).forEach { speed ->
                onMain { engine.setPlaybackSpeed(speed).getOrThrow() }
                assertEquals(speed, onMain { engine.player.playbackParameters.speed }, 0.001f)
                assertEquals("pitch must stay 1", 1f, onMain { engine.player.playbackParameters.pitch }, 0.001f)
                assertEquals(speed.toDouble(), measuredSpeed(engine), speed * TOLERANCE)
            }
        } finally {
            onMain { engine.close() }
        }
    }

    @Test
    fun speedsOutsideTheRangeAreRefusedAndTheLimitsAreAccepted() {
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            onMain { runBlocking { engine.open(item()).getOrThrow() } }
            assertTrue(onMain { engine.setPlaybackSpeed(PlaybackSpeeds.MIN) }.isSuccess)
            assertTrue(onMain { engine.setPlaybackSpeed(PlaybackSpeeds.MAX) }.isSuccess)
            assertTrue(onMain { engine.setPlaybackSpeed(0.2f) }.isFailure)
            assertTrue(onMain { engine.setPlaybackSpeed(4.5f) }.isFailure)
            assertEquals(PlaybackSpeeds.MAX, onMain { engine.player.playbackParameters.speed }, 0.001f)
        } finally {
            onMain { engine.close() }
        }
    }

    /** Media seconds played per wall-clock second over a two-second window, read from the player's own clock. */
    private fun measuredSpeed(engine: Media3PlayerEngine): Double {
        Thread.sleep(SETTLE_MILLIS)
        val startPosition = onMain { engine.player.currentPosition }
        val startClock = System.nanoTime()
        Thread.sleep(WINDOW_MILLIS)
        val endPosition = onMain { engine.player.currentPosition }
        val wallSeconds = (System.nanoTime() - startClock) / NANOS_PER_SECOND
        return (endPosition - startPosition) / MILLIS_PER_SECOND / wallSeconds
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun item(): MediaItem {
        val file = File(context.cacheDir, "mpris_check.mp3")
        instrumentation.context.assets.open("media/mpris_check.mp3").use { input ->
            file.outputStream().use(input::copyTo)
        }
        return MediaItem(MediaId("speed"), file.toURI().toString(), "speed", MediaKind.AUDIO, fileName = file.name)
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SETTLE_MILLIS = 600L
        const val WINDOW_MILLIS = 2_000L
        const val TOLERANCE = 0.15
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
