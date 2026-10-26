@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.playback

import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.CancellationException
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
    private val externalSubtitles = mutableListOf<ExternalSubtitle>()
    private var audioEnhancements = AudioEnhancementSettings()
    private var sessionGeneration = 0L

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    val activeEngine: StateFlow<PlayerEngine?> = mutableActiveEngine.asStateFlow()

    suspend fun open(queue: QueueContext, startPositionMillis: Long = 0): Result<Unit> = mutex.withLock {
        desiredPlayback = false
        attemptedEngines.clear()
        externalSubtitles.clear()
        fallbackJob?.cancel()
        val currentSession = ++sessionGeneration
        openBestEngine(queue, startPositionMillis, currentSession)
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
        sessionGeneration++
        fallbackJob?.cancel()
        return activeOrFailure().flatMap(PlayerEngine::stop)
    }

    fun seekTo(positionMillis: Long): Result<Unit> = activeOrFailure().flatMap { it.seekTo(positionMillis) }

    fun setABRepeat(range: ABRepeatRange): Result<Unit> = activeOrFailure().flatMap { it.setABRepeat(range) }

    /** Hands the active engine the item that follows the current one, for a gapless start (see [PlayerEngine.prepareNext]). */
    fun prepareNext(media: MediaItem?): Result<Unit> = activeOrFailure().flatMap { it.prepareNext(media) }
    fun setPlaybackSpeed(speed: Float): Result<Unit> = activeOrFailure().flatMap { it.setPlaybackSpeed(speed) }
    fun setVolume(volume: Float): Result<Unit> = activeOrFailure().flatMap { it.setVolume(volume) }
    fun selectAudioTrack(id: String?): Result<Unit> = activeOrFailure().flatMap { it.selectAudioTrack(id) }
    fun selectVideoTrack(id: String?): Result<Unit> = activeOrFailure().flatMap { it.selectVideoTrack(id) }
    fun selectSubtitleTrack(id: String?): Result<Unit> = activeOrFailure().flatMap { it.selectSubtitleTrack(id) }
    fun addExternalSubtitle(subtitle: ExternalSubtitle): Result<Unit> =
        activeOrFailure().flatMap { engine ->
            engine.addExternalSubtitle(subtitle).onSuccess {
                externalSubtitles.removeAll { it.uri == subtitle.uri }
                externalSubtitles += subtitle
            }
        }
    fun setSubtitleDelay(delayMillis: Long): Result<Unit> =
        activeOrFailure().flatMap { it.setSubtitleDelay(delayMillis) }
    fun setAudioDelay(delayMillis: Long): Result<Unit> =
        activeOrFailure().flatMap { it.setAudioDelay(delayMillis) }
    fun applyAudioEnhancements(settings: AudioEnhancementSettings): Result<Unit> {
        audioEnhancements = settings
        return activeOrFailure().flatMap { it.applyAudioEnhancements(settings) }
    }

    private suspend fun openBestEngine(queue: QueueContext, positionMillis: Long, session: Long): Result<Unit> {
        var lastFailure: Result<Unit>? = null
        while (true) {
            val candidate = candidatesFor(queue.current).firstOrNull() ?: break
            val result = switchTo(candidate, queue, positionMillis, session)
            if (result.isSuccess) return result
            lastFailure = result
        }
        releaseCurrent()
        val cause = lastFailure?.exceptionOrNull()
        return failure(
            PlaybackError(
                code = PlaybackErrorCode.UNSUPPORTED_CONTAINER,
                message = cause?.message ?: "No installed playback engine supports this media source",
                cause = cause,
            ),
            queue,
            session,
        )
    }

    private fun candidatesFor(media: MediaItem): List<EngineId> =
        factories.entries
            .filter { (id, factory) ->
                id !in attemptedEngines && factory.supportFor(media) != SupportLevel.UNSUPPORTED
            }
            .sortedBy { (_, factory) ->
                when (factory.supportFor(media)) {
                    SupportLevel.PREFERRED -> 0
                    SupportLevel.FALLBACK -> 1
                    SupportLevel.UNSUPPORTED -> 2
                }
            }
            .map { it.key }

    private suspend fun switchTo(engineId: EngineId, queue: QueueContext, positionMillis: Long, session: Long): Result<Unit> {
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
                session,
            )
        }
        mutableActiveEngine.value = engine
        mutableState.value = PlaybackState(
            engine = engine.id,
            queue = queue,
            media = queue.current,
            status = PlaybackStatus.Preparing,
            positionMillis = positionMillis,
            sessionId = session,
        )
        observe(engine, queue, session)
        val openResult = engine.open(queue.current, positionMillis)
        if (sessionGeneration != session) {
            engine.stop()
            return Result.failure(CancellationException("Superseded by newer playback session"))
        }
        return openResult.onSuccess {
            if (engine.capabilities.audioEnhancements.graphicEqualizer) {
                engine.applyAudioEnhancements(audioEnhancements)
            }
            externalSubtitles.forEach { engine.addExternalSubtitle(it) }
        }.onFailure { cause ->
            val error = (mutableState.value.status as? PlaybackStatus.Error)?.error ?: PlaybackError(
                code = PlaybackErrorCode.ENGINE_INITIALIZATION,
                message = cause.message ?: "Could not open media on $engineId",
                engine = engineId,
                recoverableByFallback = true,
                cause = cause,
            )
            mutableState.value = PlaybackState(
                engine = engine.id,
                queue = queue,
                media = queue.current,
                status = PlaybackStatus.Error(error),
                sessionId = session,
            )
        }
    }

    private fun observe(engine: PlayerEngine, queue: QueueContext, session: Long) {
        engineStateJob?.cancel()
        engineStateJob = scope.launch {
            engine.state.collect { engineState ->
                if (mutableActiveEngine.value !== engine || sessionGeneration != session) return@collect
                val merged = engineState.copy(queue = queue, sessionId = session)
                mutableState.value = merged
                val error = (merged.status as? PlaybackStatus.Error)?.error
                if (error?.recoverableByFallback == true && fallbackJob?.isActive != true) {
                    fallbackJob = scope.launch { attemptFallback(engine, queue, merged.positionMillis, error, session) }
                }
            }
        }
    }

    private suspend fun attemptFallback(
        failedEngine: PlayerEngine,
        queue: QueueContext,
        positionMillis: Long,
        originalError: PlaybackError,
        session: Long,
    ) = mutex.withLock {
        if (mutableActiveEngine.value !== failedEngine || sessionGeneration != session) return@withLock
        val next = candidatesFor(queue.current).firstOrNull()
        if (next == null) {
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Error(originalError), sessionId = session)
            return@withLock
        }
        val result = switchTo(next, queue, positionMillis, session)
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

    private fun failure(error: PlaybackError, queue: QueueContext, session: Long): Result<Unit> {
        mutableState.value = PlaybackState(queue = queue, media = queue.current, status = PlaybackStatus.Error(error), sessionId = session)
        return Result.failure(IllegalStateException(error.message, error.cause))
    }

    override fun close() {
        sessionGeneration++
        fallbackJob?.cancel()
        releaseCurrent()
        mutableState.value = PlaybackState()
    }
}

private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = fold(
    onSuccess = transform,
    onFailure = { Result.failure(it) },
)
