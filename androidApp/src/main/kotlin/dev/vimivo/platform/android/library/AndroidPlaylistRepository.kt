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
import java.util.UUID

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

    override suspend fun create(name: String, items: List<MediaId>): Result<Playlist> = runCatching {
        val cleanName = name.trim().also { require(it.isNotEmpty()) { "Playlist name cannot be empty" } }
        Playlist(UUID.randomUUID().toString(), cleanName, items.distinct(), System.currentTimeMillis()).also {
            save(it).getOrThrow()
        }
    }

    override suspend fun rename(id: String, name: String): Result<Unit> = mutate(id) { playlist ->
        playlist.copy(name = name.trim().also { require(it.isNotEmpty()) { "Playlist name cannot be empty" } })
    }

    override suspend fun add(id: String, mediaIds: List<MediaId>): Result<Unit> = mutate(id) { playlist ->
        playlist.copy(items = (playlist.items + mediaIds).distinct())
    }

    override suspend fun remove(id: String, position: Int): Result<Unit> = mutate(id) { playlist ->
        require(position in playlist.items.indices) { "Playlist position is out of range" }
        playlist.copy(items = playlist.items.toMutableList().apply { removeAt(position) })
    }

    override suspend fun move(id: String, from: Int, to: Int): Result<Unit> = mutate(id) { playlist ->
        require(from in playlist.items.indices && to in playlist.items.indices) { "Playlist position is out of range" }
        playlist.copy(items = playlist.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    override suspend fun duplicate(id: String, name: String): Result<Playlist> = runCatching {
        val source = requireNotNull(dao.get(id)) { "Playlist no longer exists" }.toModel()
        create(name, source.items).getOrThrow()
    }

    override suspend fun findByName(query: String, limit: Int): Result<List<Playlist>> = runCatching {
        require(limit in 1..100)
        dao.findByName(query.trim(), limit).map { it.toModel() }
    }

    private suspend fun mutate(id: String, transform: (Playlist) -> Playlist): Result<Unit> = runCatching {
        val current = requireNotNull(dao.get(id)) { "Playlist no longer exists" }.toModel()
        save(transform(current).copy(updatedAtEpochMillis = System.currentTimeMillis())).getOrThrow()
    }
}

private fun dev.vimivo.platform.android.database.PlaylistWithItems.toModel() = Playlist(
    id = playlist.id,
    name = playlist.name,
    items = items.sortedBy(PlaylistItemEntity::position).map { MediaId(it.mediaId) },
    updatedAtEpochMillis = playlist.updatedAtEpochMillis,
)
