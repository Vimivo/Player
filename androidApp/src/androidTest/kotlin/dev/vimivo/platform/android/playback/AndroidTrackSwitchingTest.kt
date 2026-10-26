@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * V1 #18 and #22 on Android with real media: a file with two audio tracks (a 440 Hz and a 1000 Hz tone) and two video
 * tracks lists both with their titles, and selecting the second audio track changes the PCM that is actually played
 * (measured behind the effects stage); selecting the second video track changes the decoded track.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTrackSwitchingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun bothAudioTracksAreListedAndSelectingTheSecondChangesTheTone() {
        val tap = AudioTap()
        val engine = onMain { Media3PlayerEngine(context, tap) }
        try {
            open(engine)
            val tracks = awaitState(engine) { it.audioTracks.size == 2 }.audioTracks
            assertEquals(listOf("Low tone", "High tone"), tracks.map { it.label })
            assertEquals(listOf("en", "es"), tracks.map { it.language })
            awaitTone(tap)
            assertEquals("the first track is the 440 Hz tone", 440.0, tap.toneHz, 15.0)
            onMain { engine.selectAudioTrack(tracks[1].id).getOrThrow() }
            awaitState(engine) { state -> state.audioTracks.firstOrNull { it.isSelected }?.id == tracks[1].id }
            awaitTone(tap)
            assertEquals("the second track is the 1000 Hz tone", 1_000.0, tap.toneHz, 30.0)
        } finally {
            onMain { engine.close() }
        }
    }

    @Test
    fun bothVideoTracksAreListedAndSelectingTheSecondChangesTheDecodedTrack() {
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            open(engine)
            val tracks = awaitState(engine) { it.videoTracks.size == 2 }.videoTracks
            assertEquals(listOf("Red picture", "Blue picture"), tracks.map { it.label })
            assertEquals("Red picture", onMain { engine.player.videoFormat?.label })
            onMain { engine.selectVideoTrack(tracks[1].id).getOrThrow() }
            awaitState(engine) { state -> state.videoTracks.firstOrNull { it.isSelected }?.id == tracks[1].id }
            val deadline = System.currentTimeMillis() + TIMEOUT
            while (onMain { engine.player.videoFormat?.label } != "Blue picture" && System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_MILLIS)
            }
            assertEquals("Blue picture", onMain { engine.player.videoFormat?.label })
        } finally {
            onMain { engine.close() }
        }
    }

    private fun open(engine: Media3PlayerEngine) {
        onMain { runBlocking { engine.open(item()).getOrThrow() } }
        onMain { engine.setVolume(0f).getOrThrow() }
        onMain { engine.play().getOrThrow() }
        awaitState(engine) { it.status == PlaybackStatus.Playing }
    }

    private fun awaitTone(tap: AudioTap) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (!tap.done && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertTrue("no audio reached the end of the effects stage", tap.done)
    }

    private fun awaitState(engine: Media3PlayerEngine, predicate: (PlaybackState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun item(): MediaItem {
        val file = File(context.cacheDir, "multitrack_tiny.mkv")
        instrumentation.context.assets.open("media/multitrack_tiny.mkv").use { input ->
            file.outputStream().use(input::copyTo)
        }
        return MediaItem(MediaId("multitrack"), file.toURI().toString(), "multitrack", MediaKind.VIDEO, fileName = file.name)
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val POLL_MILLIS = 50L
    }
}
