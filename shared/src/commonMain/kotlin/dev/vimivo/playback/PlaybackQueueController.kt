@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "NestedBlockDepth", "ReturnCount", "TooManyFunctions")

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
    /** Human-readable origin of the temporary queue, for example an album or playlist name. */
    val sourceLabel: String? = null,
) {
    val current: QueueItem? get() = items.getOrNull(currentIndex)
    val currentMedia: MediaItem? get() = current?.media
    val upNext: List<QueueItem> get() = if (currentIndex < 0) items else items.drop(currentIndex + 1)
}

/** A portable, paused queue representation. Native player handles are deliberately excluded. */
data class PlaybackQueueSnapshot(
    val sourceItems: List<QueueItem>,
    val playbackItems: List<QueueItem>,
    val currentQueueId: Long?,
    val shuffleEnabled: Boolean,
    val repeatMode: QueueRepeatMode,
    val sourceLabel: String? = null,
) {
    /**
     * Keeps only entries accepted by [predicate] while preserving occurrence identities and order.
     * When the active entry is dropped, the next retained entry in playback order becomes active.
     */
    fun retainOnly(predicate: (MediaItem) -> Boolean): PlaybackQueueSnapshot {
        val playback = playbackItems.filter { predicate(it.media) }
        val retainedIds = playback.mapTo(HashSet(), QueueItem::queueId)
        val activeIndex = playbackItems.indexOfFirst { it.queueId == currentQueueId }
        val current = when {
            currentQueueId != null && currentQueueId in retainedIds -> currentQueueId
            activeIndex < 0 -> playback.firstOrNull()?.queueId
            else -> playbackItems.drop(activeIndex + 1).firstOrNull { it.queueId in retainedIds }?.queueId
                ?: playback.lastOrNull()?.queueId
        }
        return copy(
            sourceItems = sourceItems.filter { it.queueId in retainedIds },
            playbackItems = playback,
            currentQueueId = current,
        )
    }
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
        sourceLabel: String? = null,
    ): Result<Unit> = mutex.withLock {
        require(items.isNotEmpty()) { "A playback queue cannot be empty" }
        require(startIndex in items.indices) { "Queue index is out of bounds" }
        sourceOrder = entries(items)
        val current = sourceOrder[startIndex]
        val ordered = if (shuffle) listOf(current) + sourceOrder.filterIndexed { index, _ -> index != startIndex }.shuffled(random) else sourceOrder
        mutableState.value = mutableState.value.copy(
            items = ordered,
            currentIndex = ordered.indexOf(current),
            shuffleEnabled = shuffle,
            sourceLabel = sourceLabel,
        )
        openAvailable(mutableState.value.currentIndex, 1, startPositionMillis)
    }

    suspend fun next(): Result<Unit> = mutex.withLock { advanceLocked(automatic = false) }

    /** Opens the restored/current item only after an explicit user action. */
    suspend fun playCurrent(startPositionMillis: Long = 0): Result<Unit> = mutex.withLock {
        val current = mutableState.value
        if (current.items.isEmpty()) {
            Result.failure(IllegalStateException("The playback queue is empty"))
        } else {
            openAvailable(current.currentIndex.coerceAtLeast(0), 1, startPositionMillis)
        }
    }

    /** Opens a specific queue entry selected by the user, keeping the remaining queue intact. */
    suspend fun playAt(index: Int, startPositionMillis: Long = 0): Result<Unit> = mutex.withLock {
        if (index !in mutableState.value.items.indices) {
            Result.failure(IndexOutOfBoundsException("Queue index is out of bounds"))
        } else {
            openAvailable(index, 1, startPositionMillis)
        }
    }

    suspend fun snapshot(): PlaybackQueueSnapshot = mutex.withLock {
        val current = mutableState.value
        PlaybackQueueSnapshot(
            sourceItems = sourceOrder.toList(),
            playbackItems = current.items.toList(),
            currentQueueId = current.current?.queueId,
            shuffleEnabled = current.shuffleEnabled,
            repeatMode = current.repeatMode,
            sourceLabel = current.sourceLabel,
        )
    }

    /** Restores state without opening media or starting audible playback. */
    suspend fun restore(snapshot: PlaybackQueueSnapshot) = mutex.withLock {
        val playbackIds = snapshot.playbackItems.map(QueueItem::queueId)
        val sourceIds = snapshot.sourceItems.map(QueueItem::queueId)
        require(playbackIds.distinct().size == playbackIds.size) { "Playback queue contains duplicate occurrence identifiers" }
        require(sourceIds.distinct().size == sourceIds.size) { "Queue source contains duplicate occurrence identifiers" }
        require(playbackIds.toSet() == sourceIds.toSet()) { "Queue source and playback entries differ" }
        val restoredIndex = snapshot.currentQueueId?.let(playbackIds::indexOf)?.takeIf { it >= 0 }
        sourceOrder = snapshot.sourceItems.toList()
        nextQueueId = maxOf(nextQueueId, (playbackIds.maxOrNull() ?: -1L) + 1L)
        completionHandled = false
        mutableState.value = PlaybackQueueState(
            items = snapshot.playbackItems.toList(),
            currentIndex = restoredIndex ?: if (playbackIds.isEmpty()) -1 else 0,
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = snapshot.repeatMode,
            sourceLabel = snapshot.sourceLabel,
        )
    }

    suspend fun previous(): Result<Unit> = mutex.withLock {
        if (engine.state.value.positionMillis > PREVIOUS_RESTART_THRESHOLD_MILLIS) return@withLock engine.seekTo(0)
        openAvailable(mutableState.value.currentIndex - 1, -1, 0)
    }

    fun addToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val additions = entries(items)
        val current = mutableState.value
        sourceOrder += additions
        mutableState.value = current.copy(
            items = current.items + additions,
            currentIndex = if (current.items.isEmpty()) 0 else current.currentIndex,
        )
    }

    /** Inserts after the active entry in both playback order and the unshuffled source order. */
    fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val current = mutableState.value
        val additions = entries(items)
        val insertion = (current.currentIndex + 1).coerceIn(0, current.items.size)
        val queue = current.items.toMutableList().apply { addAll(insertion, additions) }
        val activeSourceIndex = sourceOrder.indexOfFirst { it.queueId == current.current?.queueId }
        sourceOrder = sourceOrder.toMutableList().apply {
            addAll(if (activeSourceIndex < 0) size else activeSourceIndex + 1, additions)
        }
        mutableState.value = current.copy(items = queue, currentIndex = if (current.items.isEmpty()) 0 else current.currentIndex)
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

    /** Reorders the temporary queue only; saved playlists are never modified here. */
    fun move(from: Int, to: Int) {
        val current = mutableState.value
        if (from !in current.items.indices || to !in current.items.indices || from == to) return
        val activeId = current.current?.queueId
        val updated = current.items.toMutableList().apply { add(to, removeAt(from)) }
        if (!current.shuffleEnabled) sourceOrder = updated
        mutableState.value = current.copy(items = updated, currentIndex = updated.indexOfFirst { it.queueId == activeId })
    }

    fun clear() {
        sourceOrder = emptyList()
        mutableState.value = mutableState.value.copy(items = emptyList(), currentIndex = -1, sourceLabel = null)
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
            currentIndex = items.indexOfFirst { it.queueId == active?.queueId }.let { if (it < 0 && items.isNotEmpty()) 0 else it },
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

    /**
     * Opens the first available entry from [startIndex]. Entries are tracked by occurrence identity because
     * UI mutations may change indices while the engine is opening media.
     */
    private suspend fun openAvailable(startIndex: Int, direction: Int, positionMillis: Long): Result<Unit> {
        val queue = mutableState.value.items
        var index = startIndex
        var attempts = 0
        var lastFailure: Throwable? = null
        while (index in queue.indices && attempts < queue.size) {
            val entry = queue[index]
            if (isAvailable(entry.media)) {
                val result = engine.open(entry.media, if (attempts == 0) positionMillis else 0)
                if (result.isSuccess) {
                    val latest = mutableState.value
                    val activeIndex = latest.items.indexOfFirst { it.queueId == entry.queueId }
                    if (activeIndex >= 0) mutableState.value = latest.copy(currentIndex = activeIndex)
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
