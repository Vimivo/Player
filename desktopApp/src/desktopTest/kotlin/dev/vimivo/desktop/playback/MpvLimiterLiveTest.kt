package dev.vimivo.desktop.playback

import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1 #21 with the real libmpv: audio boosted past 100 % stays under the limiter ceiling, and the same chain without
 * the limiter clips. The audio is decoded to a WAV file (`ao=pcm`) and its peak measured. Skipped without libmpv.
 */
class MpvLimiterLiveTest {
    private val source = File("../testmedia/track2_lunar.mp3").absoluteFile
    private val folder = Files.createTempDirectory("vimivo-limiter").toFile().apply { deleteOnExit() }

    @Test
    fun boostedLoudAudioStaysUnderTheCeiling() = withNative { native ->
        val peak = peakOf(native, "boosted", hotInput + "," + DesktopAudioEffects.boostStage(2f))
        assertTrue(peak <= DesktopAudioEffects.LIMITER_CEILING + TOLERANCE, "peak $peak")
        assertTrue(peak > MIN_AUDIBLE_PEAK, "the limiter removed the signal: peak $peak")
    }

    @Test
    fun theSameBoostWithoutTheLimiterClips() = withNative { native ->
        val peak = peakOf(native, "clipped", "$hotInput,volume=2.0:precision=float")
        assertTrue(peak >= CLIPPED_PEAK, "expected clipping, peak $peak")
    }

    @Test
    fun noBoostAddsNoStage() {
        assertEquals(null, DesktopAudioEffects.boostStage(1f))
    }

    private val hotInput = "volume=12.0:precision=float"

    private fun peakOf(native: MpvNative, name: String, chain: String): Double {
        val out = File(folder, "$name.wav")
        val handle = checkNotNull(native.mpv_create())
        try {
            listOf(
                "config" to "no",
                "terminal" to "no",
                "vo" to "null",
                "ao" to "pcm",
                "ao-pcm-file" to out.absolutePath,
                "ao-pcm-waveheader" to "yes",
                "audio-format" to "s16",
                "af" to "lavfi=[$chain]",
            ).forEach { (key, value) -> assertEquals(0, native.mpv_set_option_string(handle, key, value), key) }
            assertEquals(0, native.mpv_initialize(handle))
            val load = StringArray(arrayOf("loadfile", source.absolutePath, "replace"))
            assertEquals(0, native.mpv_command(handle, load))
            awaitEnd(native, handle)
        } finally {
            native.mpv_terminate_destroy(handle)
        }
        return peak(out)
    }

    private fun awaitEnd(native: MpvNative, handle: Pointer) {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            val event = native.mpv_wait_event(handle, 0.5) ?: continue
            if (event.getInt(0) == END_FILE_EVENT) return
        }
        error("playback did not finish")
    }

    private fun peak(wav: File): Double {
        val bytes = wav.readBytes()
        val samples = ByteBuffer.wrap(bytes, WAV_HEADER, bytes.size - WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN)
        var max = 0
        while (samples.remaining() >= 2) max = maxOf(max, abs(samples.short.toInt()))
        return max / SHORT_FULL_SCALE
    }

    private fun withNative(block: (MpvNative) -> Unit) {
        if (!source.isFile) return println("Skipping: test media unavailable")
        val native = runCatching { MpvNative.load() }.getOrNull() ?: return println("Skipping: libmpv unavailable")
        block(native)
    }

    private companion object {
        const val END_FILE_EVENT = 7
        const val WAV_HEADER = 44
        const val SHORT_FULL_SCALE = 32768.0
        const val TOLERANCE = 0.01
        const val MIN_AUDIBLE_PEAK = 0.5
        const val CLIPPED_PEAK = 0.999
        const val TIMEOUT_NANOS = 60_000_000_000L
    }
}
