package dev.vimivo.playback

import dev.vimivo.core.util.NaturalOrder

/** A queue built around one opened file, and where in it playback starts. */
data class SiblingQueue<T>(val items: List<T>, val startIndex: Int)

/**
 * "Open with" on one file plays it and then the files of the same kind beside it, in natural order ("ep2" before
 * "ep10"), on every platform. Platforms list the folder; this decides order and start.
 */
object SiblingQueues {
    /**
     * @param opened the file the user opened.
     * @param siblings the playable files of the same kind in its folder; may or may not contain [opened].
     * @param name display or file name used for ordering.
     * @param sameFile identifies [opened] among [siblings].
     * @return all siblings in natural order starting at [opened], or [opened] alone when it is not among them.
     */
    fun <T> build(
        opened: T,
        siblings: List<T>,
        name: (T) -> String,
        sameFile: (T, T) -> Boolean,
    ): SiblingQueue<T> {
        val ordered = siblings.sortedWith(compareBy(NaturalOrder, name))
        val index = ordered.indexOfFirst { sameFile(it, opened) }
        return if (index < 0) SiblingQueue(listOf(opened), 0) else SiblingQueue(ordered, index)
    }
}
