package dev.vimivo.platform.android.library

import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.domain.repository.OrganizationRepository
import dev.vimivo.platform.android.database.BookmarkEntity
import dev.vimivo.platform.android.database.OrganizationDao
import kotlinx.coroutines.flow.map

class AndroidOrganizationRepository(private val dao: OrganizationDao) : OrganizationRepository {
    override fun observeBookmarks(mediaId: MediaId?) = (
        mediaId?.let { dao.observeBookmarks(it.value) }
            ?: dao.observeBookmarks()
        ).map { rows -> rows.map(BookmarkEntity::toModel) }

    override fun observeHistory(limit: Int) = dao.observeHistory(limit.coerceIn(1, 500)).map { rows ->
        rows.map {
            PlaybackHistory(
                MediaId(it.mediaId),
                it.positionMillis,
                it.lastPlayedAtEpochMillis,
                it.playCount,
                it.completed,
            )
        }
    }

    override suspend fun saveBookmark(bookmark: Bookmark): Result<Unit> = runCatching {
        require(bookmark.name.isNotBlank()) { "Bookmark name cannot be empty" }
        require(bookmark.positionMillis >= 0) { "Bookmark position cannot be negative" }
        dao.upsertBookmark(
            BookmarkEntity(
                bookmark.id,
                bookmark.mediaId.value,
                bookmark.name.trim(),
                bookmark.positionMillis,
                bookmark.createdAtEpochMillis,
            ),
        )
    }

    override suspend fun renameBookmark(id: String, name: String): Result<Unit> = runCatching {
        val cleanName = name.trim().also { require(it.isNotEmpty()) { "Bookmark name cannot be empty" } }
        check(dao.renameBookmark(id, cleanName) == 1) { "Bookmark no longer exists" }
    }

    override suspend fun deleteBookmark(id: String): Result<Unit> = runCatching {
        check(dao.deleteBookmark(id) == 1) { "Bookmark no longer exists" }
    }
}

private fun BookmarkEntity.toModel() = Bookmark(
    id,
    MediaId(mediaId),
    name,
    positionMillis,
    createdAtEpochMillis,
)
