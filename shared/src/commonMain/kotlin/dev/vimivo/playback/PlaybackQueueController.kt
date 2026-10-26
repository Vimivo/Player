@file:Suppress("ArgumentListWrapping", "ComplexCondition", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "NestedBlockDepth", "ReturnCount", "TooManyFunctions")

package dev.vimivo.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val stopRequest: QueueStopRequest = QueueStopRequest.NONE,
) : AutoCloseable {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackQueueState())
    private var sourceOrder: List<QueueItem> = emptyList()
    private var nextQueueId = 0L
    private var sessionGeneration = 0L
    private var lastHandledCompletionQueueId: Long? = null
    private var consecutiveSkips = 0

    /** The entry the engine was asked to start by itself after the current one; null when none is prepared. */
    private var prepared: QueueItem? = null

    /** The (current, following) pair the engine was last asked about, so a refusal is not repeated on every tick. */
    private var preparedFor: Pair<Long, Long?>? = null
    private val mutableSkipped = MutableSharedFlow<MediaItem>(extraBufferCapacity = SKIP_BUFFER)

    /** Items that failed while playing and were skipped, so the UI can say "Skipped: name". */
    val skipped: SharedFlow<MediaItem> = mutableSkipped.asSharedFlow()

    private val observer: Job = scope.launch {
        engine.state.collect { playback ->
            val current = mutableState.value.current
            val status = playback.status
            when {
                status == PlaybackStatus.Completed && shouldHandleCompletion(playback, current) -> {
                    lastHandledCompletionQueueId = current?.queueId
                    consecutiveSkips = 0
                    // "Stop after this track" (a sleep timer): stay on the finished item instead of moving on.
                    if (!stopRequest.consume()) advance(automatic = true)
                }
                // Only an error reported for the current item skips it: a control failure with no media loaded
                // (for example setting the volume before the first file opens) is not a failed item.
                status is PlaybackStatus.Error && !status.error.recoverableByFallback && playback.media != null &&
                    shouldHandleCompletion(playback, current) -> skipFailed(checkNotNull(current))
                status == PlaybackStatus.Playing -> {
                    lastHandledCompletionQueueId = null
                    consecutiveSkips = 0
                }
            }
            playback.media?.let { adoptPreparedItem(it) }
            if (status == PlaybackStatus.Playing || status == PlaybackStatus.Paused) {
                mutex.withLock { prepareFollowing() }
            }
        }
    }

    private val preparer: Job = scope.launch {
        mutableState.map { Triple(it.items, it.currentIndex, it.repeatMode) }.distinctUntilChanged().collect {
            mutex.withLock { prepareFollowing() }
        }
    }

    /**
     * The engine started the prepared item by itself (gapless): the queue follows it without opening anything, so
     * the current index, resume bookkeeping and the next preparation all move on as for any other advance.
     */
    private suspend fun adoptPreparedItem(playing: MediaItem) = mutex.withLock {
        val next = prepared ?: return@withLock
        val current = mutableState.value
        if (next.media.id != playing.id || current.current?.media?.id == playing.id) return@withLock
        val index = current.items.indexOfFirst { it.queueId == next.queueId }
        if (index < 0) return@withLock
        prepared = null
        lastHandledCompletionQueueId = null
        consecutiveSkips = 0
        mutableState.value = current.copy(currentIndex = index)
    }

    /**
     * Prepares the entry that follows the current one for a gapless start. Only audio followed by different audio is
     * prepared; repeat-one and a queue of one are left to the normal path. Anything else clears the preparation.
     */
    private suspend fun prepareFollowing() {
        val current = mutableState.value
        val playing = engine.state.value
        val entry = current.current ?: return
        val ready = playing.media?.id == entry.media.id &&
            (playing.status == PlaybackStatus.Playing || playing.status == PlaybackStatus.Paused)
        if (!ready) return
        val following = followingEntry(current, entry)
        val key = entry.queueId to following?.queueId
        if (key == preparedFor || (following == null && prepared == null)) return
        preparedFor = key
        val candidate = following?.takeIf { gaplessPair(entry.media, it.media) && isAvailable(it.media) }
        if (candidate == null && prepared == null) return
        prepared = if (engine.prepareNext(candidate?.media).isSuccess) candidate else null
    }

    private fun followingEntry(state: PlaybackQueueState, current: QueueItem): QueueItem? = when {
        stopRequest.armed -> null
        state.repeatMode == QueueRepeatMode.ONE -> null
        state.currentIndex < state.items.lastIndex -> state.items[state.currentIndex + 1]
        state.repeatMode == QueueRepeatMode.ALL -> state.items.firstOrNull()
        else -> null
    }?.takeIf { it.queueId != current.queueId }

    private fun gaplessPair(current: MediaItem, next: MediaItem) =
        current.kind == MediaKind.AUDIO && next.kind == MediaKind.AUDIO && current.id != next.id

    /**
     * An item that cannot be played never stops the queue: it is reported and the next one starts. Repeat-one
     * is ignored for failures, and once every item has failed in a row the queue stops instead of looping.
     */
    private suspend fun skipFailed(failed: QueueItem) {
        lastHandledCompletionQueueId = failed.queueId
        mutableSkipped.tryEmit(failed.media)
        consecutiveSkips++
        if (consecutiveSkips >= mutableState.value.items.size) {
            engine.stop()
            return
        }
        mutex.withLock {
            val current = mutableState.value
            val next = current.currentIndex + 1
            when {
                next <= current.items.lastIndex -> openAvailable(next, 1, 0)
                current.repeatMode == QueueRepeatMode.ALL -> openAvailable(0, 1, 0)
                else -> engine.stop()
            }
        }
    }

    private fun shouldHandleCompletion(playback: PlaybackState, current: QueueItem?): Boolean {
        if (current == null || lastHandledCompletionQueueId == current.queueId) return false
        val media = playback.media
        return media == null || media.id == current.media.id
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

    /** Attempts to open and play the active entry specifically without auto-advancing. */
    suspend fun retryCurrent(startPositionMillis: Long = 0): Result<Unit> = mutex.withLock {
        val current = mutableState.value.current
            ?: return@withLock Result.failure(IllegalStateException("No media item is currently active in queue"))
        lastHandledCompletionQueueId = null
        val currentSession = ++sessionGeneration
        val result = engine.open(current.media, startPositionMillis)
        if (sessionGeneration != currentSession) {
            engine.stop()
            return@withLock Result.failure(CancellationException("Superseded by newer playback session"))
        }
        if (result.isSuccess) {
            engine.play()
        } else {
            result
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
        sessionGeneration++
        lastHandledCompletionQueueId = null
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
        val current = mutableState.value
        val prevIndex = current.currentIndex - 1
        val targetIndex = if (prevIndex >= 0) {
            prevIndex
        } else if (current.repeatMode == QueueRepeatMode.ALL && current.items.isNotEmpty()) {
            current.items.lastIndex
        } else {
            -1
        }
        if (targetIndex >= 0) {
            openAvailable(targetIndex, -1, 0)
        } else {
            engine.seekTo(0)
        }
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
        sessionGeneration++
        lastHandledCompletionQueueId = null
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

    fun setRepeatMode(mode: QueueRepeatMode) {
        mutableState.value = mutableState.value.copy(repeatMode = mode)
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
        val currentSession = ++sessionGeneration
        prepared = null
        preparedFor = null
        lastHandledCompletionQueueId = null
        val queue = mutableState.value.items
        var index = startIndex
        var attempts = 0
        var lastFailure: Throwable? = null
        while (index in queue.indices && attempts < queue.size) {
            val entry = queue[index]
            if (isAvailable(entry.media)) {
                val result = engine.open(entry.media, if (attempts == 0) positionMillis else 0)
                if (sessionGeneration != currentSession) {
                    engine.stop()
                    return Result.failure(CancellationException("Superseded by newer playback session"))
                }
                if (result.isSuccess) {
                    val latest = mutableState.value
                    val activeIndex = latest.items.indexOfFirst { it.queueId == entry.queueId }
                    return if (activeIndex >= 0) {
                        mutableState.value = latest.copy(currentIndex = activeIndex)
                        engine.play()
                    } else {
                        engine.stop()
                        Result.failure(IllegalStateException("Queue entry was removed or cleared during opening"))
                    }
                }
                lastFailure = result.exceptionOrNull()
            }
            index += direction
            attempts++
        }
        engine.stop()
        return Result.failure(lastFailure ?: IllegalStateException("No available media remains in the queue"))
    }

    override fun close() {
        sessionGeneration++
        observer.cancel()
        preparer.cancel()
    }

    private fun entries(items: List<MediaItem>) = items.map { QueueItem(nextQueueId++, it) }

    private companion object {
        const val PREVIOUS_RESTART_THRESHOLD_MILLIS = 3_000L
        private const val SKIP_BUFFER = 8
    }
}
