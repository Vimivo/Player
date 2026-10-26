package dev.vimivo.audio.waveform

import kotlin.math.abs
import kotlin.math.max

/**
 * Real peaks of a track for the seek bar: the loudest sample in each of [bucketCount] equal slices of the audio, from
 * 0 (silence) to 1 (the loudest slice of the track). Each platform decodes the audio and feeds it to a
 * [PeakAccumulator]; this is the shared part, so a track looks the same on every platform.
 */
object WaveformPeaks {
    /** How many bars a seek bar draws. */
    const val BUCKETS: Int = 160
}

/**
 * Collects peaks while audio is decoded, without needing to know the track's length in advance: it keeps one peak per
 * [chunkFrames] frames, then [finish] merges those into the requested number of slices.
 */
class PeakAccumulator(private val chunkFrames: Int = DEFAULT_CHUNK_FRAMES) {
    private val chunks = ArrayList<Float>()
    private var chunkPeak = 0
    private var framesInChunk = 0

    init {
        require(chunkFrames > 0) { "A chunk needs at least one frame" }
    }

    /**
     * Adds [count] interleaved 16-bit samples ([channels] per frame) from [samples]. A frame's level is the loudest of
     * its channels.
     */
    fun add(samples: ShortArray, count: Int, channels: Int) {
        require(channels > 0 && count >= 0 && count <= samples.size) { "Invalid sample block" }
        var index = 0
        while (index + channels <= count) {
            var level = 0
            for (channel in 0 until channels) level = max(level, abs(samples[index + channel].toInt()))
            chunkPeak = max(chunkPeak, level)
            index += channels
            if (++framesInChunk == chunkFrames) closeChunk()
        }
    }

    private fun closeChunk() {
        chunks += chunkPeak / FULL_SCALE
        chunkPeak = 0
        framesInChunk = 0
    }

    /** True when no audio was added. */
    val isEmpty: Boolean get() = chunks.isEmpty() && framesInChunk == 0

    /**
     * The peaks in [buckets] slices, scaled so the loudest slice is 1; null when there was no audio or it was all
     * silence (a flat line would say nothing).
     */
    fun finish(buckets: Int = WaveformPeaks.BUCKETS): FloatArray? {
        require(buckets > 0) { "At least one bucket is needed" }
        if (framesInChunk > 0) closeChunk()
        if (chunks.isEmpty()) return null
        val result = FloatArray(buckets)
        for (bucket in 0 until buckets) {
            val from = (bucket.toLong() * chunks.size / buckets).toInt()
            val to = max(from + 1, ((bucket + 1).toLong() * chunks.size / buckets).toInt()).coerceAtMost(chunks.size)
            var peak = 0f
            for (i in from until to) peak = max(peak, chunks[i])
            result[bucket] = peak
        }
        val loudest = result.max()
        if (loudest <= 0f) return null
        for (i in result.indices) result[i] /= loudest
        return result
    }

    private companion object {
        const val DEFAULT_CHUNK_FRAMES = 1_024
        const val FULL_SCALE = 32_768f
    }
}

/** Stores peaks as one byte each (0-255) behind a small header, so a cached file is tiny and versioned. */
object PeakCodec {
    private const val MAGIC_0: Byte = 0x56 // 'V'
    private const val MAGIC_1: Byte = 0x50 // 'P'
    private const val VERSION: Byte = 1
    private const val HEADER = 5
    private const val LEVELS = 255

    fun encode(peaks: FloatArray): ByteArray {
        require(peaks.size in 1..MAX_COUNT) { "A waveform has between 1 and $MAX_COUNT peaks" }
        val bytes = ByteArray(HEADER + peaks.size)
        bytes[0] = MAGIC_0
        bytes[1] = MAGIC_1
        bytes[2] = VERSION
        bytes[3] = (peaks.size shr BYTE_BITS).toByte()
        bytes[4] = peaks.size.toByte()
        peaks.forEachIndexed { index, peak ->
            bytes[HEADER + index] = (peak.coerceIn(0f, 1f) * LEVELS + ROUNDING).toInt().toByte()
        }
        return bytes
    }

    /** The peaks in [bytes], or null if it is not a file this version wrote (it is then simply recomputed). */
    fun decode(bytes: ByteArray): FloatArray? {
        val ours = bytes.size >= HEADER && bytes[0] == MAGIC_0 && bytes[1] == MAGIC_1 && bytes[2] == VERSION
        val high = if (ours) bytes[3].toInt() and BYTE_MASK else 0
        val count = (high shl BYTE_BITS) or if (ours) bytes[4].toInt() and BYTE_MASK else 0
        val complete = count > 0 && bytes.size == HEADER + count
        return if (complete) {
            FloatArray(count) { (bytes[HEADER + it].toInt() and BYTE_MASK) / LEVELS.toFloat() }
        } else {
            null
        }
    }

    private const val MAX_COUNT = 65_535
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xFF
    private const val ROUNDING = 0.5f
}

/** Names a cached waveform after the file it describes, so an edited or replaced file is never shown a stale one. */
object WaveformKey {
    /** A stable key for a file at [path] of [sizeBytes] bytes last modified at [modifiedMillis]. */
    fun of(path: String, sizeBytes: Long, modifiedMillis: Long): String {
        var hash = FNV_OFFSET
        for (byte in "$path|$sizeBytes|$modifiedMillis".encodeToByteArray()) {
            hash = (hash xor (byte.toLong() and BYTE_MASK)) * FNV_PRIME
        }
        return hash.toULong().toString(HEX_RADIX).padStart(HEX_DIGITS, '0')
    }

    private const val FNV_OFFSET = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
    private const val BYTE_MASK = 0xFFL
    private const val HEX_RADIX = 16
    private const val HEX_DIGITS = 16
}
