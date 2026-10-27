package dev.vimivo.core.model

import kotlin.jvm.JvmInline

/** Platform-neutral identifier; Android content URIs must remain opaque to common code. */
@JvmInline
value class MediaId(val value: String)

enum class MediaKind { AUDIO, VIDEO, STREAM }

enum class MediaSource { MEDIA_STORE, SAF_FILE, SAF_TREE, NETWORK }

data class MediaItem(
    val id: MediaId,
    val uri: String,
    val title: String,
    val kind: MediaKind,
    val durationMillis: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val dateAddedEpochSeconds: Long? = null,
    val dateModifiedEpochSeconds: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val releaseYear: Int? = null,
    val folderName: String? = null,
    val folderPath: String? = null,
    val source: MediaSource = MediaSource.NETWORK,
    val volumeName: String? = null,
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val lastPlaybackPositionMillis: Long = 0,
    val lastPlayedAtEpochMillis: Long? = null,
)

enum class LibrarySort {
    TITLE,
    ARTIST,
    ALBUM,
    DURATION,
    DATE_ADDED,
    DATE_MODIFIED,
    FILE_SIZE,
    TRACK_NUMBER,
    LAST_PLAYED,
}

data class LibraryQuery(
    val text: String = "",
    val kind: MediaKind? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val folderPath: String? = null,
    val favoritesOnly: Boolean = false,
    val recentlyWatchedOnly: Boolean = false,
    val recentlyPlayedOnly: Boolean = false,
    val continueWatchingOnly: Boolean = false,
    val minimumDurationMillis: Long? = null,
    val maximumDurationMillis: Long? = null,
    val mimeType: String? = null,
    val sort: LibrarySort = LibrarySort.TITLE,
    val descending: Boolean = false,
    val offset: Int = 0,
    val limit: Int = 100,
) {
    init {
        require(offset >= 0)
        require(limit in 1..500)
    }
}

data class LibraryPage(
    val items: List<MediaItem>,
    val offset: Int,
    val totalCount: Int,
) {
    val hasMore: Boolean get() = offset + items.size < totalCount
}

data class LibraryCategory(val name: String, val itemCount: Int, val artworkUri: String? = null, val newCount: Int = 0)
enum class LibraryCategoryField { ALBUM, ARTIST, ALBUM_ARTIST, GENRE, FOLDER }

enum class MediaPermissionAccess { FULL, PARTIAL, DENIED, NOT_REQUIRED }

data class MediaPermissionState(
    val audio: MediaPermissionAccess,
    val video: MediaPermissionAccess,
)

sealed interface LibraryStatus {
    data object Idle : LibraryStatus
    data class Scanning(val source: String, val processed: Int) : LibraryStatus
    data class Ready(val itemCount: Int, val lastScanEpochMillis: Long?) : LibraryStatus
    data class PermissionRequired(val state: MediaPermissionState) : LibraryStatus
    data class Failed(val message: String) : LibraryStatus
}

data class Playlist(
    val id: String,
    val name: String,
    val items: List<MediaId>,
    val updatedAtEpochMillis: Long,
)

data class Bookmark(
    val id: String,
    val mediaId: MediaId,
    val name: String,
    val positionMillis: Long,
    val createdAtEpochMillis: Long,
)

data class PlaybackHistory(
    val mediaId: MediaId,
    val positionMillis: Long,
    val lastPlayedAtEpochMillis: Long,
    val playCount: Int,
    val completed: Boolean,
)

data class AlbumItem(
    val id: String,
    val title: String,
    val artist: String?,
    val albumArtist: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val totalDurationMillis: Long,
    val releaseYear: Int?,
)

data class AlbumDetails(
    val album: AlbumItem,
    val tracks: List<MediaItem>,
)

data class ArtistItem(
    val id: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val artworkUri: String?,
)

data class ArtistDetails(
    val artist: ArtistItem,
    val albums: List<AlbumItem>,
    val tracks: List<MediaItem>,
)

data class MediaPlaybackPreference(
    val mediaId: MediaId,
    val speed: Float? = null,
    val subtitleTrackId: String? = null,
    val subtitleDelayMillis: Long? = null,
    val audioTrackId: String? = null,
    val audioSyncDelayMillis: Long? = null,
    val aspectRatio: String? = null,
    val resumeEligible: Boolean = true,
    val updatedAtMillis: Long = 0L,
)
