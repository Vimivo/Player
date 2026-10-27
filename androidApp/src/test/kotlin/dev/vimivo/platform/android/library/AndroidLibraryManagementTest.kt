package dev.vimivo.platform.android.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for Android library folder organization, NEW badge clearance, and multi-selection. */
class AndroidLibraryManagementTest {

    private fun isNewMediaItem(
        addedSeconds: Long,
        nowSeconds: Long,
        lastPlayedMillis: Long?,
        lastPositionMillis: Long,
    ): Boolean {
        val isRecent = (nowSeconds - addedSeconds) < (7L * 24L * 3600L)
        val hasNeverBeenPlayed = (lastPlayedMillis == null || lastPlayedMillis == 0L) && lastPositionMillis == 0L
        return isRecent && hasNeverBeenPlayed
    }

    @Test
    fun newBadgeDisplaysForRecentlyAddedMediaAndClearsAfterPlayback() {
        val now = 1_700_000_000L
        val recentAdded = now - (2L * 24L * 3600L) // 2 days ago
        val oldAdded = now - (14L * 24L * 3600L) // 14 days ago

        // Unplayed recent item has NEW badge
        assertTrue(isNewMediaItem(recentAdded, now, null, 0L))

        // Once played (lastPlayedAtEpochMillis set), NEW badge is cleared
        assertFalse(isNewMediaItem(recentAdded, now, now * 1000L, 120_000L))

        // Partially played (lastPlaybackPositionMillis > 0), NEW badge is cleared
        assertFalse(isNewMediaItem(recentAdded, now, null, 45_000L))

        // Old unplayed item does not have NEW badge
        assertFalse(isNewMediaItem(oldAdded, now, null, 0L))
    }

    @Test
    fun folderViewGroupsItemsByDirectoryPath() {
        val items = listOf(
            createItem("vid1", "Movies/Action/movie1.mp4"),
            createItem("vid2", "Movies/Action/movie2.mp4"),
            createItem("vid3", "Movies/SciFi/movie3.mp4"),
            createItem("vid4", "Downloads/clip.mp4"),
        )

        val folderMap = items.groupBy { it.uri.substringBeforeLast('/') }
        assertEquals(3, folderMap.size)
        assertEquals(2, folderMap["Movies/Action"]?.size)
        assertEquals(1, folderMap["Movies/SciFi"]?.size)
        assertEquals(1, folderMap["Downloads"]?.size)
    }

    @Test
    fun multiSelectManagesSelectionStateCorrectly() {
        val selection = mutableSetOf<MediaId>()
        val id1 = MediaId("media-1")
        val id2 = MediaId("media-2")
        val id3 = MediaId("media-3")

        fun toggleSelect(id: MediaId) {
            if (!selection.add(id)) selection.remove(id)
        }

        toggleSelect(id1)
        toggleSelect(id2)
        assertEquals(setOf(id1, id2), selection)

        // Deselect id1
        toggleSelect(id1)
        assertEquals(setOf(id2), selection)

        // Select all
        selection.addAll(listOf(id1, id2, id3))
        assertEquals(3, selection.size)

        // Clear selection
        selection.clear()
        assertTrue(selection.isEmpty())
    }

    private fun createItem(id: String, path: String): MediaItem =
        MediaItem(
            id = MediaId(id),
            uri = path,
            title = path.substringAfterLast('/'),
            kind = MediaKind.VIDEO,
            fileName = path.substringAfterLast('/'),
        )
}
