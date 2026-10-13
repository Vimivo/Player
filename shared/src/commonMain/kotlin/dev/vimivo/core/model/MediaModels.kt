package dev.vimivo.core.model

/** Platform-neutral identifier; Android content URIs must remain opaque to common code. */
@JvmInline
value class MediaId(val value: String)

enum class MediaKind { AUDIO, VIDEO, STREAM }

data class MediaItem(
    val id: MediaId,
    val uri: String,
    val title: String,
    val kind: MediaKind,
    val durationMillis: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
)

data class Playlist(
    val id: String,
    val name: String,
    val items: List<MediaId>,
    val updatedAtEpochMillis: Long,
)
