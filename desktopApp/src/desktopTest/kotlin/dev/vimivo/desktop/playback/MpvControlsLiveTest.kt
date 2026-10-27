package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.VideoAdjustments
import dev.vimivo.playback.VideoAspectRatio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 controls with the real libmpv: each control is applied through the engine and read back from libmpv, so a
 * control that silently does nothing fails. Skipped where libmpv or the media is unavailable.
 */
class MpvControlsLiveTest {
    private val video = File("../testmedia/video_landscape.mp4").absoluteFile

    @Test
    fun speedAcceptsTheWholeRangeInFineStepsWithPitchCorrection() = withVideo { engine ->
        listOf(0.25f, 0.5f, 1.05f, 1.5f, 2f, 4f).forEach { speed ->
            engine.setPlaybackSpeed(speed).getOrThrow()
            assertEquals(speed.toDouble(), engine.readProperty("speed")!!.toDouble(), 0.001, "speed $speed")
        }
        assertTrue(engine.setPlaybackSpeed(0.2f).isFailure && engine.setPlaybackSpeed(4.1f).isFailure)
        assertEquals("yes", engine.readProperty("audio-pitch-correction"))
    }

    @Test
    fun frameStepMovesByExactlyOneFrameInEitherDirection() = withVideo { engine ->
        engine.pause().getOrThrow()
        awaitState(engine) { it.status == PlaybackStatus.Paused || it.status == PlaybackStatus.Ready }
        val frame = 1.0 / (engine.readProperty("container-fps")!!.toDouble())
        val start = position(engine)
        engine.frameStep(forward = true).getOrThrow()
        waitUntil { position(engine) > start + frame / 2 }
        val forward = position(engine)
        assertEquals(frame, forward - start, frame / 4, "one frame forward")
        engine.frameStep(forward = false).getOrThrow()
        waitUntil { position(engine) < forward - frame / 2 }
        assertEquals(frame, forward - position(engine), frame / 4, "one frame back")
    }

    @Test
    fun zoomPanRotationAspectAndColourReachLibmpv() = withVideo { engine ->
        engine.applyVideoAdjustments(
            VideoAdjustments(
                brightness = 12,
                contrast = -8,
                saturation = 20,
                gamma = 15,
                rotationDegrees = 90,
                zoom = 0.5f,
                panX = 0.1f,
                panY = -0.2f,
                aspectRatio = VideoAspectRatio.RATIO_16_9,
            ),
        ).getOrThrow()
        fun number(name: String) = engine.readProperty(name)!!.toDouble()
        assertEquals(12.0, number("brightness"), 0.001)
        assertEquals(-8.0, number("contrast"), 0.001)
        assertEquals(20.0, number("saturation"), 0.001)
        assertEquals(15.0, number("gamma"), 0.001)
        assertEquals(90.0, number("video-rotate"), 0.001)
        assertEquals(0.5, number("video-zoom"), 0.001)
        assertEquals(0.1, number("video-pan-x"), 0.001)
        assertEquals(-0.2, number("video-pan-y"), 0.001)
        assertEquals(16.0 / 9.0, number("video-aspect-override"), 0.01)
        engine.applyVideoAdjustments(VideoAdjustments(aspectRatio = VideoAspectRatio.FILL)).getOrThrow()
        assertEquals(1.0, number("panscan"), 0.001)
        assertEquals(0.0, number("video-zoom"), 0.001)
    }

    @Test
    fun audioAndSubtitleSyncOffsetsReachLibmpvInMilliseconds() = withVideo { engine ->
        engine.setAudioDelay(-250).getOrThrow()
        engine.setSubtitleDelay(1_500).getOrThrow()
        assertEquals(-0.25, engine.readProperty("audio-delay")!!.toDouble(), 0.001)
        assertEquals(1.5, engine.readProperty("sub-delay")!!.toDouble(), 0.001)
    }

    private fun position(engine: MpvPlayerEngine) = engine.readProperty("time-pos")!!.toDouble()

    private fun waitUntil(condition: () -> Boolean) = runBlocking {
        withTimeout(TIMEOUT) {
            while (!condition()) delay(POLL)
        }
    }

    private fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun withVideo(block: (MpvPlayerEngine) -> Unit) {
        if (!video.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            val uri = video.toPath().toUri().toString()
            val media = MediaItem(MediaId(uri), uri, "landscape", MediaKind.VIDEO, fileName = video.name)
            runBlocking {
                engine.setVolume(0f)
                engine.open(media).getOrThrow()
                engine.play().getOrThrow()
                awaitState(engine) { it.positionMillis > 300 }
            }
            block(engine)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val POLL = 50L
    }
}
