package dev.vimivo.core.model

/** Platform-neutral metadata extracted from a media file without modifying it. */
data class ExtractedMediaMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val releaseYear: Int? = null,
    val durationMillis: Long? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
    val audioTrackCount: Int? = null,
    val subtitleTrackCount: Int? = null,
)

interface MediaMetadataExtractor {
    suspend fun extract(media: MediaItem): Result<ExtractedMediaMetadata>
}
