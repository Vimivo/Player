package dev.vimivo.audio

import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.ReplayGainMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioDspTest {
    private val rate = 44_100

    private fun sine(hz: Int, amplitude: Float, seconds: Float = 1f, channels: Int = 1): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { index ->
            amplitude * sin(2.0 * PI * hz * (index / channels) / rate).toFloat()
        }
    }

    /** RMS of the second half, after the filters have settled. */
    private fun settledRms(samples: FloatArray): Double {
        val tail = samples.drop(samples.size / 2)
        return sqrt(tail.sumOf { it.toDouble() * it } / tail.size)
    }

    private fun db(ratio: Double) = 20 * log10(ratio)

    private fun gainAt(hz: Int, band: Int, gainDb: Float): Double {
        val equalizer = GraphicEqualizer(rate, 1)
        equalizer.setGainsDb(List(10) { if (it == band) gainDb else 0f })
        val input = sine(hz, 0.1f)
        val output = input.copyOf().also { equalizer.process(it, it.size) }
        return db(settledRms(output) / settledRms(input))
    }

    @Test
    fun aBandBoostsItsOwnFrequencyByItsGain() {
        assertEquals(12.0, gainAt(1_000, band = 5, gainDb = 12f), 0.3)
        assertEquals(-9.0, gainAt(1_000, band = 5, gainDb = -9f), 0.3)
        assertEquals(6.0, gainAt(125, band = 2, gainDb = 6f), 0.3)
    }

    @Test
    fun aBandLeavesFarAwayFrequenciesAlone() {
        assertTrue(abs(gainAt(100, band = 5, gainDb = 12f)) < 0.7)
        assertTrue(abs(gainAt(12_000, band = 5, gainDb = 12f)) < 0.7)
    }

    @Test
    fun aFlatEqualizerChangesNothing() {
        val input = sine(440, 0.5f)
        val output = input.copyOf()
        GraphicEqualizer(rate, 1).also { it.setGainsDb(List(10) { 0f }) }.process(output, output.size)
        assertTrue(input.contentEquals(output))
    }

    @Test
    fun channelsAreFilteredIndependently() {
        val equalizer = GraphicEqualizer(rate, 2)
        equalizer.setGainsDb(List(10) { if (it == 5) 12f else 0f })
        val frames = rate
        val stereo = FloatArray(frames * 2) { index ->
            // Left carries 1 kHz, right is silent.
            if (index % 2 == 0) 0.1f * sin(2.0 * PI * 1_000 * (index / 2) / rate).toFloat() else 0f
        }
        equalizer.process(stereo, frames)
        assertTrue(stereo.filterIndexed { index, _ -> index % 2 == 1 }.all { it == 0f })
        assertTrue(stereo.filterIndexed { index, _ -> index % 2 == 0 }.maxOf { abs(it) } > 0.3f)
    }

    @Test
    fun bandsAboveHalfTheSampleRateAreSkipped() {
        val equalizer = GraphicEqualizer(sampleRate = 22_050, channels = 1)
        equalizer.setGainsDb(List(10) { 12f })
        val input = FloatArray(2_000) { 0.1f * sin(it * 0.3f) }
        val output = input.copyOf().also { equalizer.process(it, it.size) }
        assertTrue(output.all { it.isFinite() })
    }

    @Test
    fun theLimiterKeepsABoostedLoudSignalUnderItsCeiling() {
        val chain = AudioDspChain(rate, 1)
        chain.gain = 2f
        val samples = sine(440, 1.0f)
        chain.process(samples, samples.size)
        val peak = samples.maxOf { abs(it) }
        assertTrue(peak <= PeakLimiter.DEFAULT_CEILING + 1e-6f, "peak $peak")
        assertTrue(peak > 0.9f, "the limiter swallowed the signal: peak $peak")
    }

    @Test
    fun withoutALimiterTheSameBoostWouldClip() {
        val samples = sine(440, 1.0f).also { for (i in it.indices) it[i] *= 2f }
        assertTrue(samples.maxOf { abs(it) } > 1.9f)
    }

    @Test
    fun aBoostOfQuietAudioIsExactAndNeverLimited() {
        val chain = AudioDspChain(rate, 1)
        chain.gain = 2f
        val input = sine(440, 0.25f)
        val output = input.copyOf().also { chain.process(it, it.size) }
        for (i in input.indices) assertEquals(input[i] * 2f, output[i], 1e-6f)
    }

    @Test
    fun aNeutralChainIsAPassThrough() {
        val chain = AudioDspChain(rate, 2)
        assertTrue(chain.isNeutral)
        val input = sine(440, 0.9f, channels = 2)
        val output = input.copyOf().also { chain.process(it, it.size / 2) }
        assertTrue(input.contentEquals(output))
    }

    @Test
    fun anEqualizerBoostOnLoudAudioIsLimitedToo() {
        val chain = AudioDspChain(rate, 1)
        chain.setEqualizerGainsDb(List(10) { if (it == 5) 12f else 0f })
        val samples = sine(1_000, 0.9f)
        chain.process(samples, samples.size)
        assertTrue(samples.maxOf { abs(it) } <= PeakLimiter.DEFAULT_CEILING + 1e-6f)
    }

    @Test
    fun presetsAreTenBandsAndFlatIsFlat() {
        EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.forEach {
            assertEquals(10, EqualizerPresets.gains(it).size, "$it")
        }
        assertTrue(EqualizerPresets.gains(EqualizerPreset.FLAT).all { it == 0f })
        assertTrue(EqualizerPresets.gains(EqualizerPreset.ROCK).any { it > 0f })
    }

    @Test
    fun customBandsBassAndTrebleResolveToTenGains() {
        val custom = AudioEnhancementSettings(
            enabled = true,
            preset = EqualizerPreset.CUSTOM,
            bands = listOf(EqualizerBand(1_000, 5f), EqualizerBand(16_000, 20f)),
            bassStrength = 0.5f,
            trebleGainDb = 3f,
        )
        val gains = EqualizerPresets.resolved(custom)
        assertEquals(10, gains.size)
        assertEquals(3f, gains[0], 1e-6f)
        assertEquals(5f, gains[5], 1e-6f)
        assertEquals(GraphicEqualizer.MAX_GAIN_DB, gains[9], 1e-6f)
        assertEquals(3f, gains[8], 1e-6f)
    }

    @Test
    fun replayGainTagsParse() {
        assertEquals(-6.5f, ReplayGain.parseGainDb("-6.50 dB"))
        assertEquals(3.2f, ReplayGain.parseGainDb("+3.2dB"))
        assertEquals(-1f, ReplayGain.parseGainDb(" -1 "))
        assertNull(ReplayGain.parseGainDb("loud"))
        assertEquals(0.98f, ReplayGain.parsePeak("0.98"))
        assertNull(ReplayGain.parsePeak("-1"))
    }

    @Test
    fun replayGainPicksTrackOrAlbumAndFallsBack() {
        val track = ReplayGain.linearGain(ReplayGainMode.TRACK, -6f, -12f)
        val album = ReplayGain.linearGain(ReplayGainMode.ALBUM, -6f, -12f)
        assertEquals(AudioDspChain.linear(-6f), track, 1e-6f)
        assertEquals(AudioDspChain.linear(-12f), album, 1e-6f)
        assertEquals(track, ReplayGain.linearGain(ReplayGainMode.ALBUM, -6f, null), 1e-6f)
        assertEquals(album, ReplayGain.linearGain(ReplayGainMode.TRACK, null, -12f), 1e-6f)
        assertEquals(1f, ReplayGain.linearGain(ReplayGainMode.OFF, -6f, -12f))
        assertEquals(1f, ReplayGain.linearGain(ReplayGainMode.TRACK, null, null))
    }

    @Test
    fun replayGainNeverPushesATaggedPeakPastFullScale() {
        val gain = ReplayGain.linearGain(ReplayGainMode.TRACK, trackGainDb = 6f, albumGainDb = null, trackPeak = 0.9f)
        assertEquals(1f / 0.9f, gain, 1e-6f)
        assertTrue(0.9f * gain <= 1.0001f)
    }
}
