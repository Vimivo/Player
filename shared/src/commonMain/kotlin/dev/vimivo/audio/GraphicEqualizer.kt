package dev.vimivo.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The ten octave bands of the graphic equalizer, the same on every platform. */
object GraphicEqualizerBands {
    val CENTRES_HZ: List<Int> = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
}

/**
 * A 10-band graphic equalizer: one peaking filter per band (RBJ audio-EQ-cookbook biquads) per channel, applied in
 * place to interleaved float samples. Bands above half the sample rate are skipped. All-zero gains are a
 * pass-through ([isFlat]).
 */
class GraphicEqualizer(
    private val sampleRate: Int,
    private val channels: Int,
    private val centresHz: List<Int> = GraphicEqualizerBands.CENTRES_HZ,
) {
    private var gainsDb: List<Float> = List(centresHz.size) { 0f }
    private var sections: List<Section> = emptyList()

    init {
        require(sampleRate > 0 && channels > 0) { "A stream needs a sample rate and at least one channel" }
    }

    /** True when every band is at 0 dB, so [process] leaves the audio untouched. */
    val isFlat: Boolean get() = gainsDb.all { it == 0f }

    /** Sets the gain of each band in dB; missing bands stay at 0 and gains are limited to ±[MAX_GAIN_DB]. */
    fun setGainsDb(gains: List<Float>) {
        gainsDb = centresHz.indices.map { (gains.getOrNull(it) ?: 0f).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB) }
        sections = centresHz.indices.filter { gainsDb[it] != 0f && centresHz[it] < sampleRate / 2 }
            .map { Section(coefficients(centresHz[it], gainsDb[it]), channels) }
    }

    /** Filters [frames] frames of interleaved [samples] in place. */
    fun process(samples: FloatArray, frames: Int) {
        if (isFlat) return
        for (section in sections) section.process(samples, frames)
    }

    fun reset() = sections.forEach(Section::reset)

    private fun coefficients(centreHz: Int, gainDb: Float): Coefficients {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * centreHz / sampleRate
        val alpha = sin(w0) / (2.0 * Q)
        val a0 = 1.0 + alpha / a
        return Coefficients(
            b0 = ((1.0 + alpha * a) / a0).toFloat(),
            b1 = ((-2.0 * cos(w0)) / a0).toFloat(),
            b2 = ((1.0 - alpha * a) / a0).toFloat(),
            a1 = ((-2.0 * cos(w0)) / a0).toFloat(),
            a2 = ((1.0 - alpha / a) / a0).toFloat(),
        )
    }

    private class Coefficients(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

    /** One biquad with its own delay line for every channel. */
    private class Section(private val c: Coefficients, channels: Int) {
        private val z1 = FloatArray(channels)
        private val z2 = FloatArray(channels)
        private val channelCount = channels

        fun process(samples: FloatArray, frames: Int) {
            for (frame in 0 until frames) {
                for (channel in 0 until channelCount) {
                    val index = frame * channelCount + channel
                    val x = samples[index]
                    val y = c.b0 * x + z1[channel]
                    z1[channel] = c.b1 * x - c.a1 * y + z2[channel]
                    z2[channel] = c.b2 * x - c.a2 * y
                    samples[index] = y
                }
            }
        }

        fun reset() {
            z1.fill(0f)
            z2.fill(0f)
        }
    }

    companion object {
        const val MAX_GAIN_DB = 12f

        /** One octave per band, as for a classic graphic equalizer. */
        private const val Q = 1.0
    }
}
