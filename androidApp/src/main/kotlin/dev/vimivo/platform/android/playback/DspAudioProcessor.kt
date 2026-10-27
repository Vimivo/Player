@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("UnstableApiUsage")

package dev.vimivo.platform.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import dev.vimivo.audio.AudioDspChain
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * ExoPlayer's audio pipeline stage that runs Vimivo's shared [AudioDspChain]: the 10-band equalizer, the gain for a
 * volume boost or ReplayGain, and the limiter. It works on 16-bit and float PCM and passes anything else through
 * untouched (the player then plays it as before). Settings are written from the main thread and picked up by the
 * playback thread at the start of its next buffer.
 */
internal class DspAudioProcessor : BaseAudioProcessor() {
    @Volatile private var requestedGain = 1f

    @Volatile private var requestedBandsDb: List<Float> = emptyList()

    @Volatile private var requestedMonoDownmix = false

    @Volatile private var changed = true

    private var chain: AudioDspChain? = null
    private var scratch = FloatArray(0)
    private var float = false

    /** Sets the linear gain (boost × ReplayGain × preamp), the ten equalizer band gains in dB, and mono downmix. */
    fun configure(gain: Float, bandsDb: List<Float>, monoDownmix: Boolean = false) {
        requestedGain = gain
        requestedBandsDb = bandsDb
        requestedMonoDownmix = monoDownmix
        changed = true
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        chain = AudioDspChain(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        changed = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val dsp = chain
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        if (dsp == null) {
            output.put(inputBuffer).flip()
            return
        }
        if (changed) apply(dsp)
        if (dsp.isNeutral) {
            output.put(inputBuffer).flip()
            return
        }
        val channels = inputAudioFormat.channelCount
        val bytesPerSample = if (float) FLOAT_BYTES else SHORT_BYTES
        val samples = size / bytesPerSample
        val frames = samples / channels
        if (scratch.size < samples) scratch = FloatArray(samples)
        read(inputBuffer, samples)
        dsp.process(scratch, frames)
        write(output, samples)
        output.flip()
    }

    override fun onFlush() {
        chain?.reset()
    }

    override fun onReset() {
        chain = null
    }

    private fun apply(dsp: AudioDspChain) {
        changed = false
        dsp.gain = requestedGain
        dsp.monoDownmix = requestedMonoDownmix
        dsp.setEqualizerGainsDb(requestedBandsDb)
    }

    private fun read(input: ByteBuffer, samples: Int) {
        if (float) {
            for (i in 0 until samples) scratch[i] = input.getFloat()
        } else {
            for (i in 0 until samples) scratch[i] = input.getShort() / SHORT_SCALE
        }
    }

    private fun write(output: ByteBuffer, samples: Int) {
        if (float) {
            for (i in 0 until samples) output.putFloat(scratch[i])
        } else {
            for (i in 0 until samples) {
                val scaled = (scratch[i] * SHORT_SCALE).roundToInt()
                output.putShort(scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
    }

    private companion object {
        const val SHORT_BYTES = 2
        const val FLOAT_BYTES = 4
        const val SHORT_SCALE = 32_768f
    }
}
