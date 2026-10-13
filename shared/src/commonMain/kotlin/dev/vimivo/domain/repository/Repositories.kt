package dev.vimivo.domain.repository

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.Playlist
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeLibrary(): Flow<List<MediaItem>>
    suspend fun refresh(): Result<Unit>
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun save(playlist: Playlist): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
