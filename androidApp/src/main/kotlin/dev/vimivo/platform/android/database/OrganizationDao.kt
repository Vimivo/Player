package dev.vimivo.platform.android.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAtEpochMillis DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE mediaId = :mediaId ORDER BY positionMillis")
    fun observeBookmarks(mediaId: String): Flow<List<BookmarkEntity>>

    @Upsert
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET name = :name WHERE id = :id")
    suspend fun renameBookmark(id: String, name: String): Int

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String): Int

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAtEpochMillis DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAtEpochMillis")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAtEpochMillis")
    suspend fun allHistory(): List<PlaybackHistoryEntity>
}
