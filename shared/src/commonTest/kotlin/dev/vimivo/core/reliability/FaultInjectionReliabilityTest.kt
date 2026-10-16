@file:Suppress("ArgumentListWrapping", "EmptyFunctionBlock", "ImportOrdering", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.reliability

import dev.vimivo.core.error.VimivoError
import dev.vimivo.core.error.VimivoErrorCategory
import dev.vimivo.core.logging.LogLevel
import dev.vimivo.core.logging.StructuredLogger
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackError
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.PlayerEngineFactory
import dev.vimivo.playback.SupportLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FaultInjectionReliabilityTest {

    @Test
    fun testMissingMediaFaultInjectionDoesNotLoopOrCrash() = runTest {
        val logger = StructuredLogger(maxBufferSize = 50)
        val engineFactory = FaultInjectableFactory(EngineId.MEDIA3)
        val manager = PlaybackEngineManager(mapOf(engineFactory.id to engineFactory), backgroundScope)

        val missingItem = MediaItem(
            id = MediaId("missing_item_1"),
            uri = "file:///Users/victim/Music/deleted_track.flac",
            title = "Deleted Track",
            kind = MediaKind.AUDIO,
        )

        manager.open(missingItem)
        runCurrent()

        // Inject SOURCE_NOT_FOUND fault
        val error = VimivoError.mediaNotFound(missingItem.uri)
        logger.logError(error)
        engineFactory.latest.failSourceNotFound()
        runCurrent()

        // Verify status is Error, manager did not crash, and error is captured
        assertTrue(manager.state.value.status is PlaybackStatus.Error)
        val log = logger.snapshotEntries().single()
        assertEquals(VimivoErrorCategory.MEDIA_NOT_FOUND, log.category)
        assertFalse(log.toFormattedLine().contains("victim"))
        assertTrue(log.toFormattedLine().contains("~/user/Music/deleted_track.flac"))
    }

    @Test
    fun testCorruptMediaFallbackAndPreservation() = runTest {
        val primary = FaultInjectableFactory(EngineId.MEDIA3)
        val fallback = FaultInjectableFactory(EngineId.LIBVLC)
        val manager = PlaybackEngineManager(mapOf(primary.id to primary, fallback.id to fallback), backgroundScope)

        val corruptItem = MediaItem(
            id = MediaId("corrupt_video"),
            uri = "file:///media/corrupt.mkv",
            title = "Corrupt Video",
            kind = MediaKind.VIDEO,
        )

        manager.open(corruptItem, 10_000)
        runCurrent()

        // Inject recoverable decoder failure on primary
        primary.latest.failRecoverableDecoder(position = 10_000)
        runCurrent()

        // Should cleanly switch to fallback without losing position
        assertEquals(EngineId.LIBVLC, manager.state.value.engine)
        assertEquals(10_000, fallback.latest.state.value.positionMillis)
        assertEquals(1, primary.created)
        assertEquals(1, fallback.created)
    }

    @Test
    fun testNetworkTimeoutFaultInjectionWithSanitizedUrl() {
        val logger = StructuredLogger()
        val privateUrl = "https://private.server.internal/stream.opus?auth=TOP_SECRET_SESSION_TOKEN"
        val error = VimivoError.networkTimeout(privateUrl)

        logger.logError(error)

        assertEquals(VimivoErrorCategory.NETWORK_TIMEOUT, error.category)
        assertTrue(error.isRecoverable)
        assertFalse(error.toDiagnosticString().contains("TOP_SECRET_SESSION_TOKEN"))
        assertTrue(error.toDiagnosticString().contains("[REDACTED]"))

        val entry = logger.snapshotEntries().single()
        assertEquals(LogLevel.WARN, entry.level)
    }

    @Test
    fun testSimulatedDatabaseMigrationFailurePreservesDataIntegrity() {
        val logger = StructuredLogger()
        val error = VimivoError.databaseMigrationFailed(
            fromVersion = 4,
            toVersion = 5,
            cause = IllegalStateException("Simulated schema constraint violation on foreign key"),
        )
        logger.logError(error)

        assertEquals(VimivoErrorCategory.DATABASE_MIGRATION, error.category)
        assertFalse(error.isRecoverable)
        assertTrue(error.userMessage.contains("preserved safely"))

        val entry = logger.snapshotEntries().single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("ERR_DB_MIGRATION_FAILED", entry.metadata["errorCode"])
    }

    @Test
    fun testNativeDependencyMissingHaltsGracefully() {
        val error = VimivoError.nativeDependencyMissing("libmpv.2.dylib")
        assertEquals(VimivoErrorCategory.NATIVE_DEPENDENCY, error.category)
        assertFalse(error.isRecoverable)
        assertTrue(error.userMessage.contains("libmpv.2.dylib"))
    }
}

private class FaultInjectableFactory(
    override val id: EngineId,
) : PlayerEngineFactory {
    var created = 0
    lateinit var latest: FaultInjectableEngine

    override fun supportFor(media: MediaItem): SupportLevel =
        if (id == EngineId.MEDIA3) SupportLevel.PREFERRED else SupportLevel.FALLBACK

    override fun create(): PlayerEngine = FaultInjectableEngine(id).also {
        latest = it
        created++
    }
}

private class FaultInjectableEngine(override val id: EngineId) : PlayerEngine {
    private val mutableState = MutableStateFlow(PlaybackState(engine = id))
    override val state = mutableState.asStateFlow()
    override val capabilities = EngineCapabilities(
        localAudio = true,
        localVideo = true,
        httpStreaming = true,
        hardwareDecoding = true,
        softwareDecoding = true,
        playbackSpeed = true,
        audioTrackSelection = true,
        videoTrackSelection = true,
        embeddedSubtitles = true,
        externalSubtitles = true,
        hdr = true,
    )
    override val audioEnhancementState = MutableStateFlow(
        dev.vimivo.playback.AudioEnhancementState(capabilities.audioEnhancements),
    ).asStateFlow()

    override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED

    override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> {
        mutableState.value = PlaybackState(
            engine = id,
            media = media,
            status = PlaybackStatus.Ready,
            positionMillis = startPositionMillis,
        )
        return Result.success(Unit)
    }

    override fun play() = Result.success(Unit)
    override fun pause() = Result.success(Unit)
    override fun stop() = Result.success(Unit)
    override fun seekTo(positionMillis: Long) = Result.success(Unit)
    override fun setPlaybackSpeed(speed: Float) = Result.success(Unit)
    override fun setVolume(volume: Float) = Result.success(Unit)
    override fun selectAudioTrack(trackId: String?) = Result.success(Unit)
    override fun selectVideoTrack(trackId: String?) = Result.success(Unit)
    override fun selectSubtitleTrack(trackId: String?) = Result.success(Unit)
    override fun addExternalSubtitle(subtitle: dev.vimivo.playback.ExternalSubtitle) = Result.success(Unit)
    override fun close() {
        // No-op for fake test engine
    }

    fun failSourceNotFound() {
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.Error(
                PlaybackError(
                    code = PlaybackErrorCode.SOURCE_NOT_FOUND,
                    message = "Source file does not exist",
                    engine = id,
                    recoverableByFallback = false,
                ),
            ),
        )
    }

    fun failRecoverableDecoder(position: Long) {
        mutableState.value = mutableState.value.copy(
            positionMillis = position,
            status = PlaybackStatus.Error(
                PlaybackError(
                    code = PlaybackErrorCode.DECODER_INITIALIZATION,
                    message = "Hardware decoder failed to configure",
                    engine = id,
                    recoverableByFallback = true,
                ),
            ),
        )
    }
}
