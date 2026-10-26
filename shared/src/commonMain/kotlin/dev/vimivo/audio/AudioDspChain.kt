package dev.vimivo.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Keeps the peak of interleaved float samples at or under [ceiling]: the gain drops at once when a sample would go
 * over and recovers towards 1 over [releaseMillis]. Audio that stays under the ceiling is left exactly as it is.
 */
class PeakLimiter(
    sampleRate: Int,
    private val channels: Int,
    val ceiling: Float = DEFAULT_CEILING,
    releaseMillis: Float = DEFAULT_RELEASE_MILLIS,
) {
    private val releaseCoefficient = exp(-1.0 / (releaseMillis / 1_000.0 * sampleRate)).toFloat()
    private var gain = 1f

    init {
        require(channels > 0 && ceiling in 0.1f..1f) { "A limiter needs channels and a ceiling between 0.1 and 1" }
    }

    fun process(samples: FloatArray, frames: Int) {
        for (frame in 0 until frames) {
            val start = frame * channels
            var peak = 0f
            for (channel in 0 until channels) peak = maxOf(peak, abs(samples[start + channel]))
            val limit = if (peak > ceiling) ceiling / peak else 1f
            gain = minOf(1f - (1f - gain) * releaseCoefficient, limit)
            if (gain < 1f) for (channel in 0 until channels) samples[start + channel] *= gain
        }
    }

    fun reset() {
        gain = 1f
    }

    companion object {
        const val DEFAULT_CEILING = 0.97f
        const val DEFAULT_RELEASE_MILLIS = 80f
    }
}

/**
 * The audio effects chain shared by the apps that process PCM themselves: a linear [gain] (volume boost above 100 %,
 * ReplayGain and the preamp, multiplied together by the caller), then the 10-band [equalizer], then a limiter that
 * only runs when something can raise the level. A neutral chain ([isNeutral]) changes nothing.
 */
class AudioDspChain(sampleRate: Int, private val channels: Int) {
    val equalizer = GraphicEqualizer(sampleRate, channels)
    private val limiter = PeakLimiter(sampleRate, channels)
    private var equalizerRaisesLevel = false

    /** Linear gain applied before the equalizer; 1 leaves the level alone. */
    var gain: Float = 1f
        set(value) {
            require(value >= 0f) { "Gain cannot be negative" }
            field = value
        }

    val isNeutral: Boolean get() = gain == 1f && equalizer.isFlat

    fun setEqualizerGainsDb(gains: List<Float>) {
        equalizer.setGainsDb(gains)
        equalizerRaisesLevel = gains.any { it > 0f }
    }

    fun process(samples: FloatArray, frames: Int) {
        if (isNeutral) return
        val count = frames * channels
        if (gain != 1f) for (index in 0 until count) samples[index] *= gain
        equalizer.process(samples, frames)
        if (gain > 1f || equalizerRaisesLevel) limiter.process(samples, frames)
    }

    fun reset() {
        equalizer.reset()
        limiter.reset()
    }

    companion object {
        /** Converts a gain in dB to a linear factor. */
        fun linear(gainDb: Float): Float = 10.0.pow(gainDb / 20.0).toFloat()
    }
}
