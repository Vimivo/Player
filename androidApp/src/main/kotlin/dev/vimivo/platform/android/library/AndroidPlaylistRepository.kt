package dev.vimivo.platform.android.library

import androidx.room.withTransaction
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.Playlist
import dev.vimivo.domain.repository.PlaylistRepository
import dev.vimivo.platform.android.database.PlaylistDao
import dev.vimivo.platform.android.database.PlaylistEntity
import dev.vimivo.platform.android.database.PlaylistItemEntity
import dev.vimivo.platform.android.database.VimivoDatabase
import kotlinx.coroutines.flow.map

class AndroidPlaylistRepository(
    private val database: VimivoDatabase,
    private val dao: PlaylistDao,
) : PlaylistRepository {
    override fun observePlaylists() = dao.observe().map { playlists ->
        playlists.map { relation ->
            Playlist(
                id = relation.playlist.id,
                name = relation.playlist.name,
                items = relation.items.sortedBy(PlaylistItemEntity::position).map { MediaId(it.mediaId) },
                updatedAtEpochMillis = relation.playlist.updatedAtEpochMillis,
            )
        }
    }

    override suspend fun save(playlist: Playlist): Result<Unit> = runCatching {
        database.withTransaction {
            dao.upsertPlaylist(PlaylistEntity(playlist.id, playlist.name, playlist.updatedAtEpochMillis))
            dao.deleteItems(playlist.id)
            dao.upsertItems(playlist.items.mapIndexed { index, id -> PlaylistItemEntity(playlist.id, id.value, index) })
        }
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching { dao.delete(id) }
}
