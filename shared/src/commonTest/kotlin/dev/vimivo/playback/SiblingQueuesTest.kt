package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class SiblingQueuesTest {
    private fun build(opened: String, siblings: List<String>) =
        SiblingQueues.build(opened, siblings, name = { it }, sameFile = { a, b -> a == b })

    @Test
    fun startsAtTheOpenedFileWithSiblingsInNaturalOrder() {
        val queue = build("Episode 2.mkv", listOf("Episode 10.mkv", "episode 1.mkv", "Episode 2.mkv"))
        assertEquals(listOf("episode 1.mkv", "Episode 2.mkv", "Episode 10.mkv"), queue.items)
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun opensTheFileAloneWhenTheFolderCannotBeListed() {
        val queue = build("clip.mp4", emptyList())
        assertEquals(listOf("clip.mp4"), queue.items)
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun opensTheFileAloneWhenItIsNotAmongTheListedSiblings() {
        assertEquals(SiblingQueue(listOf("a.mp4"), 0), build("a.mp4", listOf("b.mp4", "c.mp4")))
    }
}
