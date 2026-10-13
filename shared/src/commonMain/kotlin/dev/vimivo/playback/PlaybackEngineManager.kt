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

class PlaybackEngineManager(
    private val factories: Map<EngineId, PlayerEngineFactory>,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackState())
    private val mutableActiveEngine = MutableStateFlow<PlayerEngine?>(null)
    private var engineStateJob: Job? = null
    private var fallbackJob: Job? = null
    private var attemptedEngines = mutableSetOf<EngineId>()
    private var desiredPlayback = false

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    val activeEngine: StateFlow<PlayerEngine?> = mutableActiveEngine.asStateFlow()

    suspend fun open(queue: QueueContext, startPositionMillis: Long = 0): Result<Unit> = mutex.withLock {
        desiredPlayback = false
        attemptedEngines.clear()
        openBestEngine(queue, startPositionMillis)
    }

    suspend fun open(media: MediaItem, startPositionMillis: Long = 0): Result<Unit> =
        open(QueueContext(listOf(media), 0), startPositionMillis)

    fun play(): Result<Unit> {
        desiredPlayback = true
        return activeOrFailure().flatMap(PlayerEngine::play)
    }

    fun pause(): Result<Unit> {
        desiredPlayback = false
        return activeOrFailure().flatMap(PlayerEngine::pause)
    }

    fun resume(): Result<Unit> = play()

    fun stop(): Result<Unit> {
        desiredPlayback = false
        return activeOrFailure().flatMap(PlayerEngine::stop)
    }

    fun seekTo(positionMillis: Long): Result<Unit> = activeOrFailure().flatMap { it.seekTo(positionMillis) }
    fun setPlaybackSpeed(speed: Float): Result<Unit> = activeOrFailure().flatMap { it.setPlaybackSpeed(speed) }
    fun setVolume(volume: Float): Result<Unit> = activeOrFailure().flatMap { it.setVolume(volume) }
    fun selectAudioTrack(id: String?): Result<Unit> = activeOrFailure().flatMap { it.selectAudioTrack(id) }
    fun selectVideoTrack(id: String?): Result<Unit> = activeOrFailure().flatMap { it.selectVideoTrack(id) }
    fun selectSubtitleTrack(id: String?): Result<Unit> = activeOrFailure().flatMap { it.selectSubtitleTrack(id) }

    private suspend fun openBestEngine(queue: QueueContext, positionMillis: Long): Result<Unit> {
        val candidates = candidatesFor(queue.current)
        if (candidates.isEmpty()) {
            return failure(
                PlaybackError(
                    code = PlaybackErrorCode.UNSUPPORTED_CONTAINER,
                    message = "No installed playback engine supports this media source",
                ),
                queue,
            )
        }
        return switchTo(candidates.first(), queue, positionMillis)
    }

    private fun candidatesFor(media: MediaItem): List<EngineId> =
        listOf(EngineId.MEDIA3, EngineId.LIBVLC).filter { id ->
            id !in attemptedEngines && factories[id]?.supportFor(media) != SupportLevel.UNSUPPORTED
        }

    private suspend fun switchTo(engineId: EngineId, queue: QueueContext, positionMillis: Long): Result<Unit> {
        attemptedEngines += engineId
        releaseCurrent()
        val engine = runCatching { factories.getValue(engineId).create() }.getOrElse { cause ->
            return failure(
                PlaybackError(
                    code = PlaybackErrorCode.ENGINE_INITIALIZATION,
                    message = "Could not initialize $engineId",
                    engine = engineId,
                    recoverableByFallback = true,
                    cause = cause,
                ),
                queue,
            )
        }
        mutableActiveEngine.value = engine
        mutableState.value = PlaybackState(
            engine = engine.id,
            queue = queue,
            media = queue.current,
            status = PlaybackStatus.Preparing,
            positionMillis = positionMillis,
        )
        observe(engine, queue)
        return engine.open(queue.current, positionMillis)
    }

    private fun observe(engine: PlayerEngine, queue: QueueContext) {
        engineStateJob?.cancel()
        engineStateJob = scope.launch {
            engine.state.collect { engineState ->
                if (mutableActiveEngine.value !== engine) return@collect
                val merged = engineState.copy(queue = queue)
                mutableState.value = merged
                val error = (merged.status as? PlaybackStatus.Error)?.error
                if (error?.recoverableByFallback == true && fallbackJob?.isActive != true) {
                    fallbackJob = scope.launch { attemptFallback(engine, queue, merged.positionMillis, error) }
                }
            }
        }
    }

    private suspend fun attemptFallback(
        failedEngine: PlayerEngine,
        queue: QueueContext,
        positionMillis: Long,
        originalError: PlaybackError,
    ) = mutex.withLock {
        if (mutableActiveEngine.value !== failedEngine) return@withLock
        val next = candidatesFor(queue.current).firstOrNull()
        if (next == null) {
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Error(originalError))
            return@withLock
        }
        val result = switchTo(next, queue, positionMillis)
        if (result.isSuccess && desiredPlayback) mutableActiveEngine.value?.play()
    }

    private fun releaseCurrent() {
        engineStateJob?.cancel()
        engineStateJob = null
        mutableActiveEngine.value?.close()
        mutableActiveEngine.value = null
    }

    private fun activeOrFailure(): Result<PlayerEngine> = mutableActiveEngine.value?.let(Result.Companion::success)
        ?: Result.failure(IllegalStateException("No media is open"))

    private fun failure(error: PlaybackError, queue: QueueContext): Result<Unit> {
        mutableState.value = PlaybackState(queue = queue, media = queue.current, status = PlaybackStatus.Error(error))
        return Result.failure(IllegalStateException(error.message, error.cause))
    }

    override fun close() {
        fallbackJob?.cancel()
        releaseCurrent()
        mutableState.value = PlaybackState()
    }
}

private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = fold(
    onSuccess = transform,
    onFailure = { Result.failure(it) },
)
