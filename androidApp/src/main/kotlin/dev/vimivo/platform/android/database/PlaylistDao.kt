package dev.vimivo.platform.android.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class PlaylistWithItems(
    @androidx.room.Embedded val playlist: PlaylistEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "playlistId",
        entity = PlaylistItemEntity::class,
    )
    val items: List<PlaylistItemEntity>,
)

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<PlaylistWithItems>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistWithItems?

    @Transaction
    @Query(
        "SELECT * FROM playlists WHERE name LIKE :prefix || '%' COLLATE NOCASE " +
            "ORDER BY name COLLATE NOCASE LIMIT :limit",
    )
    suspend fun findByName(prefix: String, limit: Int): List<PlaylistWithItems>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItems(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)
}
