@file:Suppress(
    "ArgumentListWrapping", "ComplexCondition", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "MagicNumber",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "NestedBlockDepth", "ReturnCount", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import java.io.File

/**
 * Pure Kotlin parser for Ogg Opus metadata (RFC 7845).
 * Extracts stream configuration from OpusHead and Vorbis comments from OpusTags without external binaries.
 */
internal object OpusMetadataParser {
    fun parse(file: File): ExtractedMediaMetadata? {
        val bytes = runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(minOf(file.length().toInt().coerceAtLeast(1), 65536))
                var read = 0
                while (read < buffer.size) {
                    val count = input.read(buffer, read, buffer.size - read)
                    if (count <= 0) break
                    read += count
                }
                buffer.copyOf(read)
            }
        }.getOrNull() ?: return null

        val headIndex = indexOf(bytes, OPUS_HEAD_MAGIC)
        if (headIndex < 0) return null

        val channels = if (headIndex + 9 < bytes.size) bytes[headIndex + 9].toInt() and 0xFF else 2
        val sampleRate = if (headIndex + 15 < bytes.size) {
            readInt32LE(bytes, headIndex + 12).takeIf { it > 0 } ?: 48000
        } else 48000

        val tagsIndex = indexOf(bytes, OPUS_TAGS_MAGIC)
        val tags = mutableMapOf<String, String>()
        if (tagsIndex >= 0 && tagsIndex + 16 <= bytes.size) {
            var offset = tagsIndex + 8
            val vendorLen = readInt32LE(bytes, offset)
            offset += 4 + vendorLen
            if (offset + 4 <= bytes.size) {
                val commentCount = readInt32LE(bytes, offset)
                offset += 4
                for (i in 0 until minOf(commentCount, 128)) {
                    if (offset + 4 > bytes.size) break
                    val len = readInt32LE(bytes, offset)
                    offset += 4
                    if (len in 1..4096 && offset + len <= bytes.size) {
                        val comment = String(bytes, offset, len, Charsets.UTF_8)
                        val separator = comment.indexOf('=')
                        if (separator > 0) {
                            val key = comment.substring(0, separator).uppercase()
                            val value = comment.substring(separator + 1).trim()
                            tags[key] = value
                        }
                        offset += len
                    } else break
                }
            }
        }

        return ExtractedMediaMetadata(
            title = tags["TITLE"],
            artist = tags["ARTIST"],
            album = tags["ALBUM"],
            albumArtist = tags["ALBUMARTIST"] ?: tags["ALBUM_ARTIST"],
            genre = tags["GENRE"],
            trackNumber = tags["TRACKNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull(),
            discNumber = tags["DISCNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull(),
            releaseYear = (tags["DATE"] ?: tags["YEAR"])?.take(4)?.toIntOrNull(),
            sampleRate = sampleRate,
            channels = channels,
            audioCodec = "Opus",
            container = "OGG",
            audioTrackCount = 1,
        )
    }

    private fun indexOf(source: ByteArray, target: ByteArray): Int {
        if (source.size < target.size) return -1
        for (i in 0..source.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private val OPUS_HEAD_MAGIC = "OpusHead".toByteArray(Charsets.US_ASCII)
    private val OPUS_TAGS_MAGIC = "OpusTags".toByteArray(Charsets.US_ASCII)
}
