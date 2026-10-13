package dev.vimivo.platform.android.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["kind", "title"]),
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["genre"]),
        Index(value = ["folderPath"]),
        Index(value = ["dateAddedEpochSeconds"]),
        Index(value = ["lastPlayedAtEpochMillis"]),
        Index(value = ["source", "volumeName", "isAvailable"]),
    ],
)
data class MediaEntity(
    @androidx.room.PrimaryKey val id: String,
    val uri: String,
    val kind: String,
    val source: String,
    val volumeName: String?,
    val mediaStoreId: Long?,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val durationMillis: Long?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val dateAddedEpochSeconds: Long?,
    val dateModifiedEpochSeconds: Long?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitrate: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val artworkUri: String?,
    val folderName: String?,
    val folderPath: String?,
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val lastPlaybackPositionMillis: Long = 0,
    val lastPlayedAtEpochMillis: Long? = null,
    val scanToken: Long,
)

@Entity(tableName = "playback_history", indices = [Index("lastPlayedAtEpochMillis")])
data class PlaybackHistoryEntity(
    @androidx.room.PrimaryKey val mediaId: String,
    val positionMillis: Long,
    val lastPlayedAtEpochMillis: Long,
    val playCount: Int,
    val completed: Boolean,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("mediaId")],
)
data class PlaylistItemEntity(val playlistId: String, val mediaId: String, val position: Int)

@Entity(tableName = "scan_state", primaryKeys = ["source", "volumeName", "kind"])
data class ScanStateEntity(
    val source: String,
    val volumeName: String,
    val kind: String,
    val mediaStoreVersion: String?,
    val generation: Long?,
    val lastScanEpochMillis: Long,
)

@Entity(tableName = "folder_exclusions")
data class FolderExclusionEntity(@androidx.room.PrimaryKey val path: String)

@Entity(tableName = "saf_grants")
data class SafGrantEntity(
    @androidx.room.PrimaryKey val uri: String,
    val displayName: String,
    val isTree: Boolean,
    val lastScanEpochMillis: Long?,
)

data class CategoryRow(val name: String, val itemCount: Int, val artworkUri: String?)
data class UserMediaState(
    val id: String,
    val isFavorite: Boolean,
    val lastPlaybackPositionMillis: Long,
    val lastPlayedAtEpochMillis: Long?,
)
