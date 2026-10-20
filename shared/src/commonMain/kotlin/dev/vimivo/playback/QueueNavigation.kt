package dev.vimivo.playback

import kotlin.random.Random

/**
 * The "what plays next" rule for platform queues that keep their own item list (Android's session player,
 * iOS). Shuffle walks a shuffled order without replacement, so with repeat off it ends after every item.
 */
object QueueNavigation {
    /** A play order that starts with [current] and visits every other index once. */
    fun shuffledOrder(size: Int, current: Int, random: Random = Random.Default): List<Int> {
        if (size <= 0) return emptyList()
        val start = current.coerceIn(0, size - 1)
        return listOf(start) + (0 until size).filter { it != start }.shuffled(random)
    }

    /**
     * The index to play after [current] finishes, or null to stop. [order] is the shuffled order, or null for
     * list order. [afterFailure] means the item could not be played, so repeat-one must not retry it.
     */
    fun next(current: Int, size: Int, repeat: QueueRepeatMode, order: List<Int>?, afterFailure: Boolean = false): Int? {
        if (size <= 0) return null
        if (repeat == QueueRepeatMode.ONE && !afterFailure) return current.coerceIn(0, size - 1)
        val sequence = order?.takeIf { it.size == size } ?: (0 until size).toList()
        val position = sequence.indexOf(current)
        return when {
            position in 0 until sequence.lastIndex -> sequence[position + 1]
            repeat == QueueRepeatMode.ALL -> sequence.first()
            else -> null
        }
    }
}
