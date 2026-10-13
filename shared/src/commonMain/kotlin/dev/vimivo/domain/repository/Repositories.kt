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
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeLibrary(): Flow<List<MediaItem>>
    val status: Flow<LibraryStatus>
    val changes: Flow<Long>
    suspend fun refresh(): Result<Unit>
    suspend fun page(query: LibraryQuery): Result<LibraryPage>
    suspend fun categories(kind: MediaKind, field: LibraryCategoryField): Result<List<LibraryCategory>>
    suspend fun media(ids: List<MediaId>): Result<List<MediaItem>>
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
    suspend fun create(name: String, items: List<MediaId> = emptyList()): Result<Playlist>
    suspend fun rename(id: String, name: String): Result<Unit>
    suspend fun add(id: String, mediaIds: List<MediaId>): Result<Unit>
    suspend fun remove(id: String, position: Int): Result<Unit>
    suspend fun move(id: String, from: Int, to: Int): Result<Unit>
    suspend fun duplicate(id: String, name: String): Result<Playlist>
    suspend fun findByName(query: String, limit: Int = 20): Result<List<Playlist>>
}

interface OrganizationRepository {
    fun observeBookmarks(mediaId: MediaId? = null): Flow<List<Bookmark>>
    fun observeHistory(limit: Int = 100): Flow<List<PlaybackHistory>>
    suspend fun saveBookmark(bookmark: Bookmark): Result<Unit>
    suspend fun renameBookmark(id: String, name: String): Result<Unit>
    suspend fun deleteBookmark(id: String): Result<Unit>
}
