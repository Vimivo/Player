@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.vimivo.platform.android.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Measures the PCM that leaves the effects stage (see `Media3PlayerEngine`'s tap): it skips the first half second
 * (filters settling), then measures one second of samples: their RMS level and the number of zero crossings, from
 * which a single tone's frequency follows.
 */
internal class AudioTap : TeeAudioProcessor.AudioBufferSink {
    private var encoding = C.ENCODING_INVALID
    private var sampleRate = 0
    private var channels = 1
    private var skip = 0L
    private var remaining = 0L
    private var sumSquares = 0.0
    private var counted = 0L
    private var crossings = 0
    private var previous = 0.0

    @Volatile var done = false

    val rms: Double get() = sqrt(sumSquares / counted.coerceAtLeast(1))

    /** The frequency of a single tone in Hz, from its zero crossings over the measured second. */
    val toneHz: Double get() = crossings / 2.0 / (counted.toDouble() / (sampleRate * channels).coerceAtLeast(1))

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        sampleRate = sampleRateHz
        channels = channelCount
        skip = sampleRateHz.toLong() * channelCount / 2
        remaining = sampleRateHz.toLong() * channelCount
        sumSquares = 0.0
        counted = 0
        crossings = 0
        previous = 0.0
        done = false
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val samples = buffer.duplicate().order(ByteOrder.nativeOrder())
        while (!done && samples.remaining() >= bytes()) {
            val value = if (encoding == C.ENCODING_PCM_FLOAT) {
                samples.getFloat().toDouble()
            } else {
                samples.getShort() / SHORT_SCALE
            }
            if (skip > 0) {
                skip--
                previous = value
                continue
            }
            sumSquares += value * value
            if (counted > 0 && (previous < 0) != (value < 0)) crossings++
            previous = value
            counted++
            if (--remaining == 0L) done = true
        }
    }

    private fun bytes() = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2

    private companion object {
        const val SHORT_SCALE = 32_768.0
    }
}
