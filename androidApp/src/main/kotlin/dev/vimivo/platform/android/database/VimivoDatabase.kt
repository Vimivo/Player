package dev.vimivo.platform.android.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        ScanStateEntity::class,
        FolderExclusionEntity::class,
        SafGrantEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VimivoDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun playlistDao(): PlaylistDao
}
