package dev.vimivo.desktop.library

import dev.vimivo.playback.RecentMediaEntry
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopRecentMediaRepositoryTest {
    @Test
    fun persistsAcrossInstancesAndClears() {
        val dir = Files.createTempDirectory("vimivo-recent")
        val repository = DesktopRecentMediaRepository(dir, capacity = 2)
        repository.record(RecentMediaEntry("file:///a.mkv", "A", true)).getOrThrow()
        repository.record(RecentMediaEntry("file:///b.mp3", "B", false)).getOrThrow()
        repository.record(RecentMediaEntry("file:///c.mp3", "C", false)).getOrThrow()

        val reloaded = DesktopRecentMediaRepository(dir, capacity = 2)
        assertEquals(listOf("C", "B"), reloaded.entries.value.map(RecentMediaEntry::title))
        assertEquals(false, reloaded.entries.value.first().isVideo)

        reloaded.clear().getOrThrow()
        assertTrue(DesktopRecentMediaRepository(dir).entries.value.isEmpty())
        dir.toFile().deleteRecursively()
    }

    @Test
    fun corruptFileStartsEmpty() {
        val dir = Files.createTempDirectory("vimivo-recent-corrupt")
        dir.resolve("recent_media.json").writeText("{not json")
        val repository = DesktopRecentMediaRepository(dir)
        assertTrue(repository.entries.value.isEmpty())
        repository.record(RecentMediaEntry("file:///a.mkv", "A", true)).getOrThrow()
        assertEquals(1, DesktopRecentMediaRepository(dir).entries.value.size)
        dir.toFile().deleteRecursively()
    }
}
