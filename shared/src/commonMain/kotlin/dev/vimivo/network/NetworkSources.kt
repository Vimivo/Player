package dev.vimivo.network

import dev.vimivo.core.model.MediaItem

/** A user-authorized media location. Passwords are deliberately never part of this model. */
data class NetworkLocation(
    val id: String,
    val name: String,
    val protocol: NetworkProtocol,
    val endpoint: String,
    val username: String? = null,
    val favorite: Boolean = false,
)

enum class NetworkProtocol { SMB, WEBDAV, UPNP, DLNA, FTP, SFTP, RTSP }

data class RemoteDirectoryEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val modifiedEpochMillis: Long? = null,
    val media: MediaItem? = null,
)

sealed interface NetworkBrowseResult {
    data class Success(val entries: List<RemoteDirectoryEntry>) : NetworkBrowseResult
    data class Failure(val message: String, val recoverable: Boolean = true) : NetworkBrowseResult
}

interface NetworkSourceRepository {
    suspend fun locations(): List<NetworkLocation>
    suspend fun save(location: NetworkLocation): Result<Unit>
    suspend fun remove(id: String): Result<Unit>
    suspend fun browse(location: NetworkLocation, path: String = location.endpoint): NetworkBrowseResult
}
