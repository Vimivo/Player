@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ReturnCount")

package dev.vimivo.playback

import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

enum class QueueRepeatMode { OFF, ONE, ALL }

data class QueueItem(val queueId: Long, val media: MediaItem)

data class PlaybackQueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: QueueRepeatMode = QueueRepeatMode.OFF,
) {
    val current: QueueItem? get() = items.getOrNull(currentIndex)
    val currentMedia: MediaItem? get() = current?.media
}

/** Owns queue ordering and transitions while delegating all decoding to one [PlayerEngine]. */
class PlaybackQueueController(
    private val engine: PlayerEngine,
    scope: CoroutineScope,
    private val isAvailable: suspend (MediaItem) -> Boolean = { true },
    private val random: Random = Random.Default,
) : AutoCloseable {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackQueueState())
    private var sourceOrder: List<QueueItem> = emptyList()
    private var nextQueueId = 0L
    private var completionHandled = false
    private val observer: Job = scope.launch {
        engine.state.collect { playback ->
            if (playback.status == PlaybackStatus.Completed && !completionHandled) {
                completionHandled = true
                advance(automatic = true)
            } else if (playback.status != PlaybackStatus.Completed) {
                completionHandled = false
            }
        }
    }

    val state: StateFlow<PlaybackQueueState> = mutableState.asStateFlow()

    suspend fun play(
        items: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMillis: Long = 0,
        shuffle: Boolean = false,
    ): Result<Unit> = mutex.withLock {
        require(items.isNotEmpty()) { "A playback queue cannot be empty" }
        require(startIndex in items.indices) { "Queue index is out of bounds" }
        sourceOrder = entries(items)
        val current = sourceOrder[startIndex]
        val ordered = if (shuffle) listOf(current) + sourceOrder.filterIndexed { index, _ -> index != startIndex }.shuffled(random) else sourceOrder
        mutableState.value = mutableState.value.copy(items = ordered, currentIndex = ordered.indexOf(current), shuffleEnabled = shuffle)
        openAvailable(mutableState.value.currentIndex, 1, startPositionMillis)
    }

    suspend fun next(): Result<Unit> = mutex.withLock { advanceLocked(automatic = false) }

    suspend fun previous(): Result<Unit> = mutex.withLock {
        if (engine.state.value.positionMillis > PREVIOUS_RESTART_THRESHOLD_MILLIS) return@withLock engine.seekTo(0)
        openAvailable(mutableState.value.currentIndex - 1, -1, 0)
    }

    fun addToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val additions = entries(items)
        sourceOrder += additions
        mutableState.value = mutableState.value.copy(items = mutableState.value.items + additions)
    }

    fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val insertion = (mutableState.value.currentIndex + 1).coerceAtLeast(0)
        val additions = entries(items)
        val queue = mutableState.value.items.toMutableList().apply { addAll(insertion, additions) }
        sourceOrder = queue
        mutableState.value = mutableState.value.copy(items = queue)
    }

    fun remove(index: Int) {
        val current = mutableState.value
        if (index !in current.items.indices || index == current.currentIndex) return
        val removed = current.items[index]
        val updated = current.items.toMutableList().apply { removeAt(index) }
        sourceOrder = sourceOrder.filterNot { it.queueId == removed.queueId }
        val nextIndex = when {
            updated.isEmpty() -> -1
            index < current.currentIndex -> current.currentIndex - 1
            current.currentIndex >= updated.size -> updated.lastIndex
            else -> current.currentIndex
        }
        mutableState.value = current.copy(items = updated, currentIndex = nextIndex)
    }

    fun move(from: Int, to: Int) {
        val current = mutableState.value
        if (from !in current.items.indices || to !in current.items.indices || from == to) return
        val activeId = current.current?.queueId
        val updated = current.items.toMutableList().apply { add(to, removeAt(from)) }
        sourceOrder = updated
        mutableState.value = current.copy(items = updated, currentIndex = updated.indexOfFirst { it.queueId == activeId })
    }

    fun clear() {
        sourceOrder = emptyList()
        mutableState.value = mutableState.value.copy(items = emptyList(), currentIndex = -1)
        engine.stop()
    }

    fun toggleShuffle() {
        val current = mutableState.value
        val active = current.current
        val items = if (current.shuffleEnabled) sourceOrder else {
            val remaining = current.items.filterIndexed { index, _ -> index != current.currentIndex }.shuffled(random)
            listOfNotNull(active) + remaining
        }
        mutableState.value = current.copy(
            items = items,
            currentIndex = items.indexOfFirst { it.queueId == active?.queueId },
            shuffleEnabled = !current.shuffleEnabled,
        )
    }

    fun cycleRepeat() {
        val next = when (mutableState.value.repeatMode) {
            QueueRepeatMode.OFF -> QueueRepeatMode.ONE
            QueueRepeatMode.ONE -> QueueRepeatMode.ALL
            QueueRepeatMode.ALL -> QueueRepeatMode.OFF
        }
        mutableState.value = mutableState.value.copy(repeatMode = next)
    }

    private suspend fun advance(automatic: Boolean): Result<Unit> = mutex.withLock { advanceLocked(automatic) }

    private suspend fun advanceLocked(automatic: Boolean): Result<Unit> {
        val current = mutableState.value
        if (current.items.isEmpty()) return Result.failure(IllegalStateException("The playback queue is empty"))
        if (automatic && current.repeatMode == QueueRepeatMode.ONE) return openAvailable(current.currentIndex, 1, 0)
        val next = current.currentIndex + 1
        if (next <= current.items.lastIndex) return openAvailable(next, 1, 0)
        if (current.repeatMode == QueueRepeatMode.ALL) return openAvailable(0, 1, 0)
        return engine.stop()
    }

    private suspend fun openAvailable(startIndex: Int, direction: Int, positionMillis: Long): Result<Unit> {
        val queue = mutableState.value.items
        var index = startIndex
        var attempts = 0
        var lastFailure: Throwable? = null
        while (index in queue.indices && attempts < queue.size) {
            val media = queue[index].media
            if (isAvailable(media)) {
                val result = engine.open(media, if (attempts == 0) positionMillis else 0)
                if (result.isSuccess) {
                    mutableState.value = mutableState.value.copy(currentIndex = index)
                    return engine.play()
                }
                lastFailure = result.exceptionOrNull()
            }
            index += direction
            attempts++
        }
        return Result.failure(lastFailure ?: IllegalStateException("No available media remains in the queue"))
    }

    override fun close() = observer.cancel()

    private fun entries(items: List<MediaItem>) = items.map { QueueItem(nextQueueId++, it) }

    private companion object {
        const val PREVIOUS_RESTART_THRESHOLD_MILLIS = 3_000L
    }
}
