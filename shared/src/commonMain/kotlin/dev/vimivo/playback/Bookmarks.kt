package dev.vimivo.playback

import dev.vimivo.storage.StoredBookmark

/** The rules for bookmarks that every platform follows: how one is named and when a new one would be a duplicate. */
object Bookmarks {
    /** Bookmarks closer together than this (at the same track) are one place, not two. */
    const val DUPLICATE_TOLERANCE_MILLIS: Long = 1_000

    /** The default name: the position as m:ss, or h:mm:ss from an hour on. */
    fun defaultName(positionMillis: Long): String {
        val total = (positionMillis.coerceAtLeast(0) / MILLIS_PER_SECOND).toInt()
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        val secondsText = seconds.toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secondsText" else "$minutes:$secondsText"
    }

    /** The bookmark to save, named by its position, or null when [existing] already has one at (nearly) that place. */
    fun create(
        mediaId: String,
        positionMillis: Long,
        existing: List<StoredBookmark>,
        id: String,
        createdAtMillis: Long,
    ): StoredBookmark? {
        val duplicate = existing.any {
            it.mediaId == mediaId && kotlin.math.abs(it.positionMillis - positionMillis) < DUPLICATE_TOLERANCE_MILLIS
        }
        if (duplicate) return null
        val position = positionMillis.coerceAtLeast(0)
        return StoredBookmark(id, mediaId, defaultName(position), position, createdAtMillis)
    }

    private const val MILLIS_PER_SECOND = 1_000
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
}
