package dev.vimivo.playback

import kotlin.random.Random

/**
 * A play queue's order and position as pure logic, for platforms whose player keeps its own item list (iOS): which
 * item is current, what "next" and "previous" mean, shuffle, repeat, and edits that keep the current item right. It
 * knows only item ids; the platform maps them to its own items and plays them.
 *
 * Shuffle reorders the items behind the current one and remembers the original order; turning it off restores that
 * order, keeping items that were added meanwhile at the end. Not thread-safe: use it from one thread.
 */
class PlayQueue(private val random: Random) {
    constructor() : this(Random.Default)

    private var items: List<String> = emptyList()
    private var originalOrder: List<String>? = null

    /** The ids in play order. */
    val ids: List<String> get() = items

    var currentIndex: Int = 0
        private set
    var shuffled: Boolean = false
        private set
    var repeat: QueueRepeatMode = QueueRepeatMode.OFF
        private set

    val size: Int get() = items.size
    val currentId: String? get() = items.getOrNull(currentIndex)

    /** Replaces the queue. A shuffled queue stays shuffled: the items behind [startIndex] are shuffled again. */
    fun replace(ids: List<String>, startIndex: Int = 0) {
        items = ids.toList()
        originalOrder = null
        currentIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (shuffled) shuffleBehindCurrent()
    }

    /** Makes [index] current; false (and no change) when there is no such item. */
    fun jumpTo(index: Int): Boolean {
        if (index !in items.indices) return false
        currentIndex = index
        return true
    }

    /** Makes the first item with [id] current; false when it is not queued. */
    fun jumpToId(id: String): Boolean = jumpTo(items.indexOf(id))

    fun append(id: String) {
        items = items + id
    }

    /** Queues [id] right behind the current item (at the start of an empty queue). */
    fun insertNext(id: String) {
        val at = if (items.isEmpty()) 0 else currentIndex + 1
        items = items.toMutableList().apply { add(at.coerceAtMost(size), id) }
    }

    /**
     * Removes the item at [index], keeping the current item current. Returns true when the removed item was the
     * current one (the item that followed it is now current, or the last item if it was the last).
     */
    fun remove(index: Int): Boolean {
        if (index !in items.indices) return false
        val removed = items[index]
        items = items.toMutableList().apply { removeAt(index) }
        originalOrder = originalOrder?.toMutableList()?.apply { remove(removed) }
        val wasCurrent = index == currentIndex
        if (index < currentIndex) currentIndex--
        currentIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        return wasCurrent
    }

    /** Moves the item at [from] to [to] (an index in the list after the move); the current item stays current. */
    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val current = currentId
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        if (current == null) return
        currentIndex = when {
            from == currentIndex -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
    }

    fun clear() = replace(emptyList())

    fun toggleShuffle() {
        shuffled = !shuffled
        if (items.isEmpty()) return
        if (shuffled) shuffleBehindCurrent() else restoreOrder()
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            QueueRepeatMode.OFF -> QueueRepeatMode.ALL
            QueueRepeatMode.ALL -> QueueRepeatMode.ONE
            QueueRepeatMode.ONE -> QueueRepeatMode.OFF
        }
    }

    /**
     * Moves to the next item and returns its index, or null when playback should stop. [automatic] is a track ending
     * by itself: with repeat-one it returns the same index again (play it again). Asking for the next item by hand
     * always moves on, whatever the repeat mode. At the end the queue wraps only with repeat-all.
     */
    fun next(automatic: Boolean = false): Int? {
        val target = when {
            items.isEmpty() -> null
            automatic && repeat == QueueRepeatMode.ONE -> currentIndex
            currentIndex + 1 < items.size -> currentIndex + 1
            repeat == QueueRepeatMode.ALL -> 0
            else -> null
        }
        if (target != null) currentIndex = target
        return target
    }

    /**
     * What the "previous" button does: [RESTART] when the current item has played more than [restartAfterMillis] (or
     * there is nothing before it and no repeat-all), otherwise the index it moved to.
     */
    fun previous(positionMillis: Long, restartAfterMillis: Long = DEFAULT_RESTART_AFTER_MILLIS): Int {
        if (items.isEmpty() || positionMillis > restartAfterMillis) return RESTART
        val target = when {
            currentIndex > 0 -> currentIndex - 1
            repeat == QueueRepeatMode.ALL -> items.lastIndex
            else -> return RESTART
        }
        currentIndex = target
        return target
    }

    /** The index that follows the current item for a gapless start, or null (repeat-one, the end, a lone item). */
    fun followingIndex(): Int? {
        if (items.isEmpty() || repeat == QueueRepeatMode.ONE) return null
        val following = when {
            currentIndex + 1 < items.size -> currentIndex + 1
            repeat == QueueRepeatMode.ALL -> 0
            else -> return null
        }
        return following.takeIf { it != currentIndex }
    }

    /**
     * After the current item could not be played: moves to the next one and returns its index, or null once every
     * item has failed in a row ([consecutiveFailures]) or the queue ends (and does not repeat), so playback stops
     * instead of looping.
     */
    fun skipFailed(consecutiveFailures: Int): Int? {
        if (consecutiveFailures >= items.size) return null
        val target = when {
            currentIndex + 1 < items.size -> currentIndex + 1
            repeat == QueueRepeatMode.ALL -> 0
            else -> return null
        }
        currentIndex = target
        return target
    }

    /** What to persist: the ids in play order and the current index. */
    fun snapshot(): PlayQueueSnapshot = PlayQueueSnapshot(items, currentIndex)

    /**
     * Restores a saved queue, dropping ids that are no longer [available]. The item that was current stays current
     * when it still exists, otherwise the first one is. Shuffle is not part of a saved queue.
     */
    fun restore(savedIds: List<String>, savedIndex: Int, available: Set<String>) {
        val kept = savedIds.filter { it in available }
        val currentId = savedIds.getOrNull(savedIndex)
        items = kept
        originalOrder = null
        shuffled = false
        currentIndex = kept.indexOf(currentId).takeIf { it >= 0 } ?: 0
    }

    private fun shuffleBehindCurrent() {
        originalOrder = items
        val current = items[currentIndex]
        val rest = items.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
        items = listOf(current) + rest
        currentIndex = 0
    }

    private fun restoreOrder() {
        val original = originalOrder ?: return
        val current = currentId
        items = original + items.filter { it !in original }
        currentIndex = items.indexOf(current).coerceAtLeast(0)
        originalOrder = null
    }

    companion object {
        /** [previous] returns this when the current item should restart instead of moving. */
        const val RESTART: Int = -1
        const val DEFAULT_RESTART_AFTER_MILLIS: Long = 3_000
    }
}

data class PlayQueueSnapshot(val ids: List<String>, val currentIndex: Int)
