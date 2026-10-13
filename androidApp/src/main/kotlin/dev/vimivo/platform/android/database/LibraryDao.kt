@file:Suppress("TooManyFunctions")

package dev.vimivo.platform.android.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM media WHERE isAvailable = 1 ORDER BY title COLLATE NOCASE LIMIT :limit")
    fun observePreview(limit: Int = 200): Flow<List<MediaEntity>>

    @RawQuery(observedEntities = [MediaEntity::class])
    suspend fun page(query: SupportSQLiteQuery): List<MediaEntity>

    @RawQuery(observedEntities = [MediaEntity::class])
    suspend fun count(query: SupportSQLiteQuery): Int

    @RawQuery(observedEntities = [MediaEntity::class])
    suspend fun categories(query: SupportSQLiteQuery): List<CategoryRow>

    @Upsert
    suspend fun upsertMedia(items: List<MediaEntity>)

    @Query(
        "SELECT id, isFavorite, lastPlaybackPositionMillis, lastPlayedAtEpochMillis " +
            "FROM media WHERE id IN (:ids)",
    )
    suspend fun userState(ids: List<String>): List<UserMediaState>

    @Query(
        "UPDATE media SET isAvailable = 0 WHERE source = :source AND volumeName = :volume " +
            "AND kind = :kind AND scanToken != :scanToken",
    )
    suspend fun markUnseenUnavailable(source: String, volume: String, kind: String, scanToken: Long): Int

    @Query("UPDATE media SET isAvailable = 0 WHERE id = :id")
    suspend fun markUnavailable(id: String)

    @Query("DELETE FROM media WHERE isAvailable = 0")
    suspend fun deleteUnavailable(): Int

    @Query("DELETE FROM playback_history WHERE mediaId IN (SELECT id FROM media WHERE isAvailable = 0)")
    suspend fun deleteHistoryForUnavailable(): Int

    @Transaction
    suspend fun deleteUnavailableWithHistory(): Int {
        deleteHistoryForUnavailable()
        return deleteUnavailable()
    }

    @Query("SELECT * FROM media WHERE source = :source AND isAvailable = 1")
    suspend fun mediaBySource(source: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id IN (:ids) AND isAvailable = 1")
    suspend fun mediaByIds(ids: List<String>): List<MediaEntity>

    @Query("SELECT * FROM media WHERE uri IN (:uris) AND isAvailable = 1")
    suspend fun mediaByUris(uris: List<String>): List<MediaEntity>

    @Query("UPDATE media SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query(
        "UPDATE media SET lastPlaybackPositionMillis = :position, lastPlayedAtEpochMillis = :playedAt " +
            "WHERE id = :id",
    )
    suspend fun updatePlayback(id: String, position: Long, playedAt: Long)

    @Query("SELECT * FROM playback_history WHERE mediaId = :id")
    suspend fun history(id: String): PlaybackHistoryEntity?

    @Upsert
    suspend fun upsertHistory(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM scan_state WHERE source = :source AND volumeName = :volume AND kind = :kind")
    suspend fun scanState(source: String, volume: String, kind: String): ScanStateEntity?

    @Upsert
    suspend fun upsertScanState(state: ScanStateEntity)

    @Query("SELECT path FROM folder_exclusions")
    suspend fun exclusions(): List<String>

    @Upsert
    suspend fun upsertExclusion(exclusion: FolderExclusionEntity)

    @Query("DELETE FROM folder_exclusions WHERE path = :path")
    suspend fun deleteExclusion(path: String)

    @Upsert
    suspend fun upsertSafGrant(grant: SafGrantEntity)

    @Query("SELECT * FROM saf_grants ORDER BY displayName COLLATE NOCASE")
    suspend fun safGrants(): List<SafGrantEntity>

    @Query("SELECT COUNT(*) FROM media WHERE isAvailable = 1")
    suspend fun availableCount(): Int
}
