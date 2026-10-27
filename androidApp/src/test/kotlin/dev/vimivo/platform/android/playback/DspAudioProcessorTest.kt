@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("UnstableApiUsage")

package dev.vimivo.platform.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The audio stage that runs the shared DSP chain inside ExoPlayer, on the PCM it will really receive. */
class DspAudioProcessorTest {
    private val rate = 44_100

    private fun shortSine(amplitude: Float, frames: Int = rate): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val value = amplitude * sin(2.0 * PI * 440 * frame / rate)
            buffer.putShort((value * 32_767).toInt().toShort())
        }
        buffer.flip()
        return buffer
    }

    private fun floatSine(amplitude: Float, frames: Int = rate): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) buffer.putFloat((amplitude * sin(2.0 * PI * 440 * frame / rate)).toFloat())
        buffer.flip()
        return buffer
    }

    private fun run(processor: DspAudioProcessor, input: ByteBuffer): ByteBuffer {
        processor.queueInput(input)
        val output = ByteBuffer.allocateDirect(input.capacity()).order(ByteOrder.nativeOrder())
        output.put(processor.output).flip()
        return output
    }

    private fun peakShort(buffer: ByteBuffer): Int {
        var peak = 0
        while (buffer.remaining() >= 2) peak = maxOf(peak, abs(buffer.getShort().toInt()))
        return peak
    }

    @Test
    fun aBoostDoublesQuietAudio() {
        val processor = DspAudioProcessor().also { it.configure(2f, emptyList()) }
        processor.configure(AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        val output = run(processor, shortSine(0.25f))
        assertEquals(2 * 0.25 * 32_767, peakShort(output).toDouble(), 40.0)
    }

    @Test
    fun aBoostOfLoudAudioIsLimitedUnderTheCeiling() {
        val processor = DspAudioProcessor().also { it.configure(2f, emptyList()) }
        processor.configure(AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        val peak = peakShort(run(processor, shortSine(1f)))
        assertTrue("peak $peak", peak <= (0.97 * 32_768).toInt() + 2)
        assertTrue("the limiter swallowed the signal: $peak", peak > 0.9 * 32_768)
    }

    @Test
    fun aNeutralProcessorPassesTheBytesThrough() {
        val processor = DspAudioProcessor()
        processor.configure(AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = shortSine(0.8f)
        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        val output = run(processor, input)
        val actual = ByteArray(output.remaining()).also { output.get(it) }
        assertArrayEquals(expected, actual)
    }

    @Test
    fun anEqualizerBandRaisesItsFrequency() {
        val processor = DspAudioProcessor().also { it.configure(1f, List(10) { band -> if (band == 4) 12f else 0f }) }
        processor.configure(AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        // 440 Hz sits in the 500 Hz band; a quiet tone comes out about 4 times as loud (12 dB), under the limiter.
        val peak = peakShort(run(processor, shortSine(0.1f)))
        assertTrue("peak $peak", peak in (0.3 * 32_767).toInt()..(0.45 * 32_767).toInt())
    }

    @Test
    fun floatAudioIsProcessedToo() {
        val processor = DspAudioProcessor().also { it.configure(2f, emptyList()) }
        processor.configure(AudioFormat(rate, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val output = run(processor, floatSine(0.25f))
        var peak = 0f
        while (output.remaining() >= 4) peak = maxOf(peak, abs(output.getFloat()))
        assertEquals(0.5f, peak, 0.01f)
    }

    @Test
    fun unsupportedEncodingsAreRefusedSoThePlayerKeepsTheirOriginalPath() {
        try {
            DspAudioProcessor().configure(AudioFormat(rate, 1, C.ENCODING_PCM_8BIT))
            fail("8-bit PCM should be refused")
        } catch (expected: UnhandledAudioFormatException) {
            // The audio sink then skips this stage for that stream.
        }
    }
}
