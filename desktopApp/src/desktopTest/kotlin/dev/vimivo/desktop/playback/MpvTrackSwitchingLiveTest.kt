package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.SnapshotOptions
import dev.vimivo.playback.TrackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 #18 and #22 with the real libmpv: a file with two video and two audio tracks lists both with their titles and
 * languages, and selecting the second track changes what is actually decoded (the picture turns from red to blue; the
 * current audio track becomes the other one). Skipped where libmpv or the media is unavailable.
 */
class MpvTrackSwitchingLiveTest {
    private val clip = File("../testmedia/multitrack_tiny.mkv").absoluteFile
    private val folder = Files.createTempDirectory("vimivo-tracks").toFile().apply { deleteOnExit() }

    @Test
    fun bothAudioTracksAreListedWithTheirTitlesAndLanguages() = withEngine { engine ->
        val audio = awaitState(engine) { it.audioTracks.size == 2 }.audioTracks
        assertEquals(listOf("Low tone", "High tone"), audio.map { it.label })
        assertEquals(listOf("eng", "spa"), audio.map { it.language })
        assertEquals(listOf(true, false), audio.map { it.isSelected })
        assertTrue(audio.all { it.type == TrackType.AUDIO })
    }

    @Test
    fun selectingTheSecondAudioTrackChangesTheDecodedTrack() = withEngine { engine ->
        val audio = awaitState(engine) { it.audioTracks.size == 2 }.audioTracks
        assertEquals("Low tone", engine.readProperty("current-tracks/audio/title"))
        engine.selectAudioTrack(audio[1].id).getOrThrow()
        val after = awaitState(engine) { state -> state.audioTracks.firstOrNull { it.isSelected }?.id == audio[1].id }
        assertEquals("High tone", engine.readProperty("current-tracks/audio/title"))
        assertEquals(listOf(false, true), after.audioTracks.map { it.isSelected })
        engine.selectAudioTrack(audio[0].id).getOrThrow()
        awaitState(engine) { state -> state.audioTracks.firstOrNull { it.isSelected }?.id == audio[0].id }
        assertEquals("Low tone", engine.readProperty("current-tracks/audio/title"))
    }

    @Test
    fun bothVideoTracksAreListedAndSelectingTheSecondChangesThePicture() = withEngine { engine ->
        val video = awaitState(engine) { it.videoTracks.size == 2 }.videoTracks
        assertEquals(listOf("Red picture", "Blue picture"), video.map { it.label })
        assertEquals(RED, centreColour(engine, "first"), "the first track is red")
        engine.selectVideoTrack(video[1].id).getOrThrow()
        awaitState(engine) { state -> state.videoTracks.firstOrNull { it.isSelected }?.id == video[1].id }
        assertEquals(BLUE, centreColour(engine, "second"), "the second track is blue")
    }

    /** The dominant colour (red or blue) of the middle of a snapshot taken after the picture settled. */
    private fun centreColour(engine: MpvPlayerEngine, name: String): String = runBlocking {
        delay(SETTLE_MILLIS)
        val file = File(folder, "$name.png")
        engine.takeSnapshot(file.path, SnapshotOptions()).getOrThrow()
        val image = ImageIO.read(file)
        val rgb = image.getRGB(image.width / 2, image.height / 2)
        val red = rgb shr RED_SHIFT and CHANNEL
        val blue = rgb and CHANNEL
        if (red > blue) RED else BLUE
    }

    private fun item(): MediaItem {
        val uri = clip.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, "multitrack", MediaKind.VIDEO, fileName = clip.name)
    }

    private fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean): PlaybackState =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun withEngine(block: (MpvPlayerEngine) -> Unit) {
        if (!clip.isFile || runCatching { MpvNative.load() }.isFailure) {
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
        const val RED = "red"
        const val BLUE = "blue"
        const val RED_SHIFT = 16
        const val CHANNEL = 0xFF
        const val TIMEOUT = 15_000L
        const val SETTLE_MILLIS = 500L
    }
}
