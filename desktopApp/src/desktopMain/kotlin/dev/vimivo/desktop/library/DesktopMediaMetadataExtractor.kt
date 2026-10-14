@file:Suppress(
    "ArgumentListWrapping", "ImportOrdering", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse",
    "ParameterListWrapping", "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaMetadataExtractor
import java.io.File
import java.net.URI
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/**
 * JVM-provided metadata extraction for audio formats supported by the active runtime.
 * Unsupported codecs are reported to the coordinator and never filled with guessed values.
 */
internal class DesktopMediaMetadataExtractor(
    private val cache: DesktopMediaCache = DesktopMediaCache(),
) : MediaMetadataExtractor {
    override suspend fun extract(media: MediaItem): Result<ExtractedMediaMetadata> = try {
        Result.success(withContext(Dispatchers.IO) { extractLocal(media) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun extractLocal(media: MediaItem): ExtractedMediaMetadata {
        kotlin.coroutines.coroutineContext.ensureActive()
        require(media.uri.startsWith("file:")) { "Only local files can be inspected" }
        val file = File(URI(media.uri))
        require(file.isFile) { "Media file is unavailable" }
        return if (media.kind == MediaKind.AUDIO && file.extension.lowercase() in TAGGED_AUDIO_EXTENSIONS) {
            extractTagged(media, file)
        } else {
            extractJavaSound(media, file)
        }
    }

    private suspend fun extractTagged(media: MediaItem, file: File): ExtractedMediaMetadata {
        val audioFile = AudioFileIO.read(file)
        kotlin.coroutines.coroutineContext.ensureActive()
        val tag = audioFile.tag
        val header = audioFile.audioHeader
        val artwork = tag?.firstArtwork?.binaryData?.takeIf(ByteArray::isNotEmpty)
        val artworkUri = artwork?.let {
            cache.storeArtwork("${media.id.value}:${media.sizeBytes}:${media.dateModifiedEpochSeconds}", it).getOrNull()
        }
        return ExtractedMediaMetadata(
            title = tag?.text(FieldKey.TITLE),
            artist = tag?.text(FieldKey.ARTIST),
            album = tag?.text(FieldKey.ALBUM),
            albumArtist = tag?.text(FieldKey.ALBUM_ARTIST),
            genre = tag?.text(FieldKey.GENRE),
            trackNumber = tag?.number(FieldKey.TRACK),
            discNumber = tag?.number(FieldKey.DISC_NO),
            releaseYear = tag?.text(FieldKey.YEAR)?.take(4)?.toIntOrNull(),
            durationMillis = (header.preciseTrackLength * 1_000).toLong().takeIf { it > 0 },
            bitrate = header.bitRateAsNumber.toInt().takeIf { it > 0 }?.let { if (it < 100_000) it * 1_000 else it },
            sampleRate = header.sampleRateAsNumber.takeIf { it > 0 },
            channels = header.channels.substringBefore(' ').toIntOrNull(),
            audioCodec = header.encodingType.trim().takeIf(String::isNotEmpty),
            container = header.format.trim().uppercase().takeIf(String::isNotEmpty) ?: file.extension.uppercase(),
            audioTrackCount = 1,
            artworkUri = artworkUri,
        )
    }

    private fun extractJavaSound(media: MediaItem, file: File): ExtractedMediaMetadata {
        val fileFormat = AudioSystem.getAudioFileFormat(file)
        val format = fileFormat.format
        val properties = fileFormat.properties()
        val durationMicros = properties["duration"] as? Number
        val computedDuration = if (durationMicros != null) durationMicros.toLong() / 1_000 else {
            val frameRate = format.frameRate.takeIf { it > 0f }
            fileFormat.frameLength.takeIf { it >= 0 && frameRate != null }?.let { frames -> (frames / frameRate!! * 1_000).toLong() }
        }
        return ExtractedMediaMetadata(
            title = properties.string("title") ?: media.title,
            artist = properties.string("author") ?: properties.string("artist"),
            album = properties.string("album"),
            genre = properties.string("genre"),
            trackNumber = properties.integer("track"),
            releaseYear = properties.integer("date"),
            durationMillis = computedDuration,
            bitrate = properties.integer("bitrate"),
            sampleRate = format.sampleRate.takeIf { it > 0f }?.toInt(),
            channels = format.channels.takeIf { it > 0 },
            audioCodec = format.encoding.toString(),
            container = fileFormat.type.toString(),
            audioTrackCount = 1,
        )
    }

    private fun Map<String, Any>.string(key: String): String? = this[key]?.toString()?.trim()?.takeIf(String::isNotEmpty)
    private fun Map<String, Any>.integer(key: String): Int? = (this[key] as? Number)?.toInt() ?: string(key)?.toIntOrNull()

    private fun org.jaudiotagger.tag.Tag.text(key: FieldKey): String? =
        runCatching { getFirst(key).trim() }.getOrNull()?.takeIf(String::isNotEmpty)

    private fun org.jaudiotagger.tag.Tag.number(key: FieldKey): Int? = text(key)?.substringBefore('/')?.trim()?.toIntOrNull()

    private companion object {
        val TAGGED_AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "m4b", "mp4", "ogg", "opus", "wav", "aif", "aiff")
    }
}
