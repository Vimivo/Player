package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.CropRect
import dev.vimivo.playback.DeinterlaceMode
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.VideoAdjustments
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** V1 #12 #13 #14 with the real libmpv: hue, crop and every deinterlacing mode reach libmpv and keep playback going. */
class MpvVideoFiltersLiveTest {
    @Test
    fun hueReachesLibmpv() = withClip("video_landscape.mp4") { engine ->
        engine.applyVideoAdjustments(VideoAdjustments(hue = -30)).getOrThrow()
        assertEquals(-30.0, engine.readProperty("hue")!!.toDouble(), 0.001)
    }

    @Test
    fun aCropIsAppliedAndCleared() = withClip("letterbox_tiny.mp4") { engine ->
        val crop = CropRect(x = 0, y = 30, width = 320, height = 180)
        engine.applyVideoAdjustments(VideoAdjustments(crop = crop)).getOrThrow()
        assertEquals("320x180+0+30", engine.readProperty("video-crop"))
        engine.applyVideoAdjustments(VideoAdjustments()).getOrThrow()
        val cleared = engine.readProperty("video-crop").orEmpty()
        assertTrue(cleared.isEmpty() || cleared.startsWith("0x0") || cleared == "no", "video-crop is '$cleared'")
    }

    @Test
    fun everyDeinterlacingModeIsAcceptedAndPlaybackContinues() = withClip("interlaced_tiny.ts") { engine ->
        DeinterlaceMode.entries.forEach { mode ->
            engine.setDeinterlaceMode(mode).getOrThrow()
            assertEquals(mode, engine.state.value.deinterlaceMode)
            val expectedFilters = if (mode == DeinterlaceMode.OFF) 0 else 1
            assertEquals(expectedFilters, filtersLabelled(engine, "vimivo-deint"), mode.name)
            // Start from the beginning each time: on a slow machine the short clip would otherwise run out before
            // every mode has been tried, and "playback continues" could never be seen.
            engine.seekTo(0).getOrThrow()
            val before = engine.readProperty("time-pos")?.toDoubleOrNull() ?: 0.0
            val advanced = runCatching {
                runBlocking {
                    withTimeout(TIMEOUT) {
                        while (timePosition(engine) < before + 0.1) delay(POLL_MILLIS)
                    }
                }
            }
            check(advanced.isSuccess) {
                val report = listOf("time-pos", "vf", "core-idle", "pause", "hwdec-current")
                    .joinToString { "$it=${engine.readProperty(it)}" }
                "playback did not advance with ${mode.name} (time-pos was $before): $report"
            }
        }
    }

    @Test
    fun theOldOnOffSwitchStillWorksAndMapsToAuto() = withClip("interlaced_tiny.ts") { engine ->
        engine.setDeinterlace(true).getOrThrow()
        assertEquals(DeinterlaceMode.AUTO, engine.state.value.deinterlaceMode)
        assertTrue(engine.state.value.deinterlace)
        engine.setDeinterlace(false).getOrThrow()
        assertEquals(DeinterlaceMode.OFF, engine.state.value.deinterlaceMode)
    }

    private fun timePosition(engine: MpvPlayerEngine) = engine.readProperty("time-pos")?.toDoubleOrNull() ?: 0.0

    /** How many filters in libmpv's `vf` chain carry [label]; the chain reads as `@label:name=params,@label2:...`. */
    private fun filtersLabelled(engine: MpvPlayerEngine, label: String): Int =
        engine.readProperty("vf").orEmpty().split(',').count { it.startsWith("@$label:") }

    private fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun withClip(name: String, block: (MpvPlayerEngine) -> Unit) {
        val file = File("../testmedia/$name").absoluteFile
        if (!file.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            val uri = file.toPath().toUri().toString()
            runBlocking {
                engine.setVolume(0f)
                engine.open(MediaItem(MediaId(uri), uri, name, MediaKind.VIDEO, fileName = name)).getOrThrow()
                engine.play().getOrThrow()
                awaitState(engine) { it.positionMillis > 100 }
            }
            block(engine)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val POLL_MILLIS = 50L
    }
}

/**
 * V1 #13: the deinterlacers actually remove combing. A frame of the interlaced clip is captured without and with each
 * filter; combing is the mean difference between neighbouring rows relative to rows two apart (interlaced motion makes
 * neighbours differ far more than same-field rows).
 */
class MpvDeinterlaceEffectTest {
    private val clip = File("../testmedia/interlaced_tiny.ts").absoluteFile

    @Test
    fun everyDeinterlacerReducesCombing() {
        if (!clip.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val inspector = MpvMediaInspector()
        val plain = combing(checkNotNull(inspector.captureFrame(clip, 1.0, 320)))
        assertTrue(plain > MIN_COMBING, "the fixture is not combed enough to test (combing $plain)")
        DeinterlaceMode.entries.filter { it != DeinterlaceMode.OFF }.forEach { mode ->
            val frame = inspector.captureFrame(clip, 1.0, 320, filtersBeforeScale = "lavfi=[${mode.lavfiFilter}]")
            val after = combing(checkNotNull(frame) { "no frame for ${mode.name}" })
            println("VIMIVO_COMBING ${mode.name}: $plain -> $after")
            assertTrue(after < plain * MAX_REMAINING, "${mode.name}: combing $plain -> $after")
        }
    }

    private fun combing(jpeg: ByteArray): Double {
        val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(jpeg))
        fun luma(x: Int, y: Int): Double {
            val rgb = image.getRGB(x, y)
            return (rgb shr RED_SHIFT and BYTE) * RED + (rgb shr GREEN_SHIFT and BYTE) * GREEN + (rgb and BYTE) * BLUE
        }
        var near = 0.0
        var far = 0.0
        for (y in 0 until image.height - 2) {
            for (x in 0 until image.width) {
                near += kotlin.math.abs(luma(x, y) - luma(x, y + 1))
                far += kotlin.math.abs(luma(x, y) - luma(x, y + 2))
            }
        }
        return near / far.coerceAtLeast(1.0)
    }

    private companion object {
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE = 0xFF
        const val RED = 0.3
        const val GREEN = 0.59
        const val BLUE = 0.11
        const val MIN_COMBING = 1.15
        const val MAX_REMAINING = 0.9
    }
}
