@file:Suppress("ImportOrdering")

package dev.vimivo.domain.repository

import dev.vimivo.core.model.LibraryCategory
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibraryPage
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.LibraryStatus
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaPermissionState
import dev.vimivo.core.model.Playlist
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeLibrary(): Flow<List<MediaItem>>
    val status: Flow<LibraryStatus>
    val changes: Flow<Long>
    suspend fun refresh(): Result<Unit>
    suspend fun page(query: LibraryQuery): Result<LibraryPage>
    suspend fun categories(kind: MediaKind, field: LibraryCategoryField): Result<List<LibraryCategory>>
    suspend fun permissionState(): MediaPermissionState
    suspend fun importFile(uri: String): Result<MediaItem>
    suspend fun importFolder(uri: String): Result<Int>
    suspend fun removeMissing(): Result<Int>
    suspend fun setFavorite(id: MediaId, favorite: Boolean): Result<Unit>
    suspend fun recordPlayback(id: MediaId, positionMillis: Long, completed: Boolean): Result<Unit>
    suspend fun excludeFolder(path: String): Result<Unit>
    suspend fun includeFolder(path: String): Result<Unit>
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun save(playlist: Playlist): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
