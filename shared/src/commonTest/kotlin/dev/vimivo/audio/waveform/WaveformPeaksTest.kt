package dev.vimivo.audio.waveform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaveformPeaksTest {
    private fun block(frames: Int, level: Short, channels: Int = 1) = ShortArray(frames * channels) { level }

    @Test
    fun aLoudFirstHalfAndQuietSecondHalfShowAsTallThenShortBars() {
        val accumulator = PeakAccumulator(chunkFrames = 100)
        accumulator.add(block(10_000, 20_000), 10_000, 1)
        accumulator.add(block(10_000, 2_000), 10_000, 1)
        val peaks = assertNotNull(accumulator.finish(buckets = 10))
        assertEquals(1f, peaks[0], 0.0001f)
        assertTrue(peaks[4] > 0.99f, "first half stays at the top: ${peaks[4]}")
        assertEquals(0.1f, peaks[5], 0.01f)
        assertEquals(0.1f, peaks[9], 0.01f)
    }

    @Test
    fun theLoudestChannelOfEachFrameCounts() {
        val accumulator = PeakAccumulator(chunkFrames = 4)
        accumulator.add(shortArrayOf(100, -30_000, 100, 200), 4, channels = 2)
        assertEquals(1f, assertNotNull(accumulator.finish(buckets = 1))[0])
    }

    @Test
    fun theResultDoesNotDependOnHowTheAudioWasSplitIntoBlocks() {
        val samples = ShortArray(5_000) { ((it * 37) % 20_000 - 10_000).toShort() }
        val whole = PeakAccumulator(64).also { it.add(samples, samples.size, 1) }.finish(20)
        val split = PeakAccumulator(64).also { acc ->
            var at = 0
            while (at < samples.size) {
                val n = minOf(333, samples.size - at)
                acc.add(samples.copyOfRange(at, at + n), n, 1)
                at += n
            }
        }.finish(20)
        assertContentEquals(whole, split)
    }

    @Test
    fun silenceOrNoAudioGivesNoPeaksSoNothingIsDrawnForIt() {
        assertNull(PeakAccumulator().finish())
        val silent = PeakAccumulator(10).also { it.add(block(1_000, 0), 1_000, 1) }
        assertNull(silent.finish())
        assertTrue(PeakAccumulator().isEmpty)
    }

    @Test
    fun aShortTrackStillFillsEveryBucket() {
        val accumulator = PeakAccumulator(chunkFrames = 1_024)
        accumulator.add(block(500, 8_000), 500, 1)
        val peaks = assertNotNull(accumulator.finish(buckets = 160))
        assertEquals(160, peaks.size)
        assertTrue(peaks.all { it == 1f })
    }

    @Test
    fun peaksSurviveTheCacheFormatWithinOneStep() {
        val peaks = floatArrayOf(0f, 0.25f, 0.5f, 1f)
        val back = assertNotNull(PeakCodec.decode(PeakCodec.encode(peaks)))
        assertEquals(peaks.size, back.size)
        peaks.indices.forEach { assertEquals(peaks[it], back[it], 1f / 255) }
    }

    @Test
    fun aDamagedOrForeignCacheFileIsIgnored() {
        val good = PeakCodec.encode(floatArrayOf(0.5f, 1f))
        assertNull(PeakCodec.decode(byteArrayOf()))
        assertNull(PeakCodec.decode(good.copyOf(good.size - 1)))
        assertNull(PeakCodec.decode(good.copyOf().also { it[0] = 0 }))
        assertNull(PeakCodec.decode(good.copyOf().also { it[2] = 99 }))
    }

    @Test
    fun theKeyChangesWithPathSizeOrModifiedTimeAndIsStable() {
        val key = WaveformKey.of("/music/a.flac", 1_000, 5)
        assertEquals(key, WaveformKey.of("/music/a.flac", 1_000, 5))
        assertEquals(16, key.length)
        assertNotEquals(key, WaveformKey.of("/music/b.flac", 1_000, 5))
        assertNotEquals(key, WaveformKey.of("/music/a.flac", 1_001, 5))
        assertNotEquals(key, WaveformKey.of("/music/a.flac", 1_000, 6))
    }
}
