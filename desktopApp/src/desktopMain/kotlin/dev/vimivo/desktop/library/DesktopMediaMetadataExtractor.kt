@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaMetadataExtractor
import java.io.File
import java.net.URI
import javax.sound.sampled.AudioSystem

/**
 * JVM-provided metadata extraction for audio formats supported by the active runtime.
 * Unsupported codecs are reported to the coordinator and never filled with guessed values.
 */
internal class DesktopMediaMetadataExtractor : MediaMetadataExtractor {
    override suspend fun extract(media: MediaItem): Result<ExtractedMediaMetadata> = runCatching {
        require(media.uri.startsWith("file:")) { "Only local files can be inspected" }
        val file = File(URI(media.uri))
        require(file.isFile) { "Media file is unavailable" }
        val fileFormat = AudioSystem.getAudioFileFormat(file)
        val format = fileFormat.format
        val properties = fileFormat.properties()
        val durationMicros = properties["duration"] as? Number
        val computedDuration = if (durationMicros != null) durationMicros.toLong() / 1_000 else {
            val frameRate = format.frameRate.takeIf { it > 0f }
            fileFormat.frameLength.takeIf { it >= 0 && frameRate != null }?.let { frames -> (frames / frameRate!! * 1_000).toLong() }
        }
        ExtractedMediaMetadata(
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
}
