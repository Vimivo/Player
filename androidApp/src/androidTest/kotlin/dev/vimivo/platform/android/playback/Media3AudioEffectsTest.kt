@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.vimivo.platform.android.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.ReplayGainMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * V1 #19, #21 and #24 on Android with real audio: a 440 Hz tone is decoded by ExoPlayer, run through Vimivo's effects
 * stage, and the PCM that leaves it is measured (an optional tap behind the stage hands it to the test). A boost, an
 * equalizer band and a ReplayGain tag each change the level by the expected amount, and the plain tone is the control.
 */
@RunWith(AndroidJUnit4::class)
class Media3AudioEffectsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun aBoostToTwiceTheVolumeDoublesTheLevel() {
        val plain = measure("track1_solaris.flac") { }
        val boosted = measure("track1_solaris.flac") { engine -> onMain { engine.setVolume(2f).getOrThrow() } }
        assertEquals(2.0, boosted / plain, 0.1)
    }

    @Test
    fun anEqualizerBandRaisesTheToneInItsBand() {
        val plain = measure("track1_solaris.flac") { }
        val raised = measure("track1_solaris.flac") { engine ->
            // The 440 Hz tone sits in the 500 Hz band: +12 dB is about 4 times the level.
            val settings = AudioEnhancementSettings(
                enabled = true,
                preset = EqualizerPreset.CUSTOM,
                bands = listOf(EqualizerBand(500, 12f)),
            )
            onMain { engine.applyAudioEnhancements(settings).getOrThrow() }
        }
        assertTrue("ratio ${raised / plain}", raised / plain in 3.3..4.2)
    }

    @Test
    fun aFlatEqualizerChangesNothing() {
        val plain = measure("track1_solaris.flac") { }
        val flat = measure("track1_solaris.flac") { engine ->
            onMain { engine.applyAudioEnhancements(AudioEnhancementSettings(enabled = true)).getOrThrow() }
        }
        assertEquals(1.0, flat / plain, 0.02)
    }

    @Test
    fun replayGainLowersATaggedFileAndLeavesAnUntaggedOneAlone() {
        val tagged = measure("track1_replaygain.flac") { }
        val withGain = measure("track1_replaygain.flac") { engine ->
            onMain {
                engine.applyAudioEnhancements(AudioEnhancementSettings(replayGainMode = ReplayGainMode.TRACK))
                    .getOrThrow()
            }
        }
        // The file's tag says -6 dB, about half the level.
        assertEquals(0.5, withGain / tagged, 0.04)
        val untaggedOff = measure("track1_solaris.flac") { }
        val untaggedOn = measure("track1_solaris.flac") { engine ->
            onMain {
                engine.applyAudioEnhancements(AudioEnhancementSettings(replayGainMode = ReplayGainMode.TRACK))
                    .getOrThrow()
            }
        }
        assertEquals(1.0, untaggedOn / untaggedOff, 0.02)
    }

    /** Plays [asset] with [configure] applied and returns the RMS of one second of the PCM after the effects. */
    private fun measure(asset: String, configure: (Media3PlayerEngine) -> Unit): Double {
        val tap = AudioTap()
        val engine = onMain { Media3PlayerEngine(context, tap) }
        try {
            configure(engine)
            onMain { runBlocking { engine.open(item(asset)).getOrThrow() } }
            onMain { engine.play().getOrThrow() }
            runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing } } }
            val deadline = System.currentTimeMillis() + TIMEOUT
            while (!tap.done && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
            assertTrue("no audio reached the end of the effects stage", tap.done)
            return tap.rms
        } finally {
            onMain { engine.close() }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun item(asset: String): MediaItem {
        val file = File(context.cacheDir, asset)
        instrumentation.context.assets.open("media/$asset").use { input -> file.outputStream().use(input::copyTo) }
        return MediaItem(MediaId(asset), file.toURI().toString(), asset, MediaKind.AUDIO, fileName = file.name)
    }

    private companion object {
        const val TIMEOUT = 30_000L
        const val POLL_MILLIS = 50L
    }
}
