package dev.vimivo.platform.android.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import dev.vimivo.audio.waveform.PeakAccumulator
import dev.vimivo.audio.waveform.PeakCodec
import dev.vimivo.audio.waveform.WaveformKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * The real peaks of a track for the seek bar. The audio is decoded in the background with `MediaExtractor` and
 * `MediaCodec` (no playback involved) and the peaks are cached by path, size and modified time, so a changed file is
 * decoded again and an unchanged one never is. Until the peaks exist the seek bar shows a plain line.
 */
class AndroidWaveformRepository(
    private val context: Context,
    private val cacheDirectory: File = File(context.cacheDir, "waveforms"),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** The peaks of [uri], or null when it cannot be decoded or is silent. Cancelling stops the decoding. */
    suspend fun peaks(uri: Uri): FloatArray? = withContext(dispatcher) {
        val cacheFile = cacheKey(uri)?.let { File(cacheDirectory, "$it.peaks") }
        val cached = cacheFile?.takeIf(File::isFile)?.let { PeakCodec.decode(it.readBytes()) }
        if (cached != null) return@withContext cached
        val peaks = try {
            decode(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // A file that cannot be decoded just keeps the plain line.
            Log.w(TAG, "No waveform for $uri", failure)
            null
        }
        if (peaks != null && cacheFile != null) {
            runCatching {
                cacheDirectory.mkdirs()
                cacheFile.writeBytes(PeakCodec.encode(peaks))
            }
        }
        peaks
    }

    /** The cache name for [uri]: path plus the file's size and modified time; null when they cannot be read. */
    internal fun cacheKey(uri: Uri): String? {
        val (size, modified) = when (uri.scheme) {
            "file" -> File(uri.path ?: return null).let { it.length() to it.lastModified() }
            "content" -> contentSizeAndModified(uri)
            else -> return null
        }
        return if (size > 0) WaveformKey.of(uri.toString(), size, modified) else null
    }

    private fun contentSizeAndModified(uri: Uri): Pair<Long, Long> = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val size = if (cursor.isNull(0)) 0L else cursor.getLong(0)
            val modified = if (cursor.columnCount > 1 && !cursor.isNull(1)) cursor.getLong(1) else 0L
            size to modified
        }
    }.getOrNull() ?: (0L to 0L)

    private suspend fun decode(uri: Uri): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            return drain(extractor, decoder, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2))
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private suspend fun drain(extractor: MediaExtractor, decoder: MediaCodec, initialChannels: Int): FloatArray? {
        val accumulator = PeakAccumulator()
        val output = PcmOutput(initialChannels)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var finished = false
        while (!finished) {
            coroutineContext.ensureActive()
            if (!inputDone) inputDone = feed(extractor, decoder)
            val index = decoder.dequeueOutputBuffer(info, TIMEOUT_MICROS)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                output.update(decoder.outputFormat)
            } else if (index >= 0) {
                output.accumulate(decoder, index, info, accumulator)
                finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
        return accumulator.finish()
    }

    /** What the decoder is producing: channel count, 16-bit or float samples, and a reusable conversion buffer. */
    private class PcmOutput(private var channels: Int) {
        private var float = false
        private var scratch = ShortArray(0)

        fun update(format: MediaFormat) {
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channels)
            float = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_16) == ENCODING_FLOAT
        }

        fun accumulate(decoder: MediaCodec, index: Int, info: MediaCodec.BufferInfo, into: PeakAccumulator) {
            val buffer = checkNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.nativeOrder())
            buffer.position(info.offset).limit(info.offset + info.size)
            val count = if (float) info.size / Float.SIZE_BYTES else info.size / Short.SIZE_BYTES
            if (scratch.size < count) scratch = ShortArray(count)
            if (float) {
                val floats = buffer.asFloatBuffer()
                for (i in 0 until count) scratch[i] = floats.get(i).toPcm16()
            } else {
                buffer.asShortBuffer().get(scratch, 0, count)
            }
            into.add(scratch, count, channels.coerceAtLeast(1))
            decoder.releaseOutputBuffer(index, false)
        }

        private fun Float.toPcm16(): Short = (coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }

    /** Queues the next compressed sample; true once the end of the stream has been queued. */
    private fun feed(extractor: MediaExtractor, decoder: MediaCodec): Boolean {
        val slot = decoder.dequeueInputBuffer(TIMEOUT_MICROS)
        if (slot < 0) return false
        val buffer = checkNotNull(decoder.getInputBuffer(slot))
        val size = extractor.readSampleData(buffer, 0)
        return if (size < 0) {
            decoder.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            true
        } else {
            decoder.queueInputBuffer(slot, 0, size, extractor.sampleTime, 0)
            extractor.advance()
            false
        }
    }

    private companion object {
        const val TAG = "VimivoWaveform"
        const val TIMEOUT_MICROS = 10_000L
        const val ENCODING_16 = 2 // AudioFormat.ENCODING_PCM_16BIT
        const val ENCODING_FLOAT = 4 // AudioFormat.ENCODING_PCM_FLOAT
    }
}
