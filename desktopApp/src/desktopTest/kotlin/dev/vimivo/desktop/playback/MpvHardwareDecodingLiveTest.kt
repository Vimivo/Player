package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.HardwareAccelerationMode
import dev.vimivo.playback.PlaybackDiagnosticsSnapshot
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V1 #8 with the real libmpv: the three hardware-decoding choices (Automatic, Software only, Hardware preferred) reach
 * the decoder, and diagnostics report which one is running. Software-only is checked everywhere; a hardware decoder is
 * required on macOS (VideoToolbox is always there) and wherever `VIMIVO_EXPECT_HWDEC=1` says the host has one. Hosts
 * without a GPU (CI runners) cannot prove hardware, so there the hardware cases only check that playback still works.
 */
class MpvHardwareDecodingLiveTest {
    private val clip = File("../testmedia/video_landscape.mp4").absoluteFile
    private val expectHardware =
        System.getProperty("os.name").startsWith("Mac") || System.getenv("VIMIVO_EXPECT_HWDEC") == "1"

    @Test
    fun softwareOnlyDecodesOnTheCpu() = withDecoding(HardwareAccelerationMode.SOFTWARE) { engine ->
        val diagnostics = awaitDiagnostics(engine) { it.activeVideoCodec != null }
        assertFalse(diagnostics.hwdecActive, "software mode reported a hardware decoder: ${diagnostics.activeDecoder}")
        assertTrue(diagnostics.activeDecoder.orEmpty().startsWith("sw:") || diagnostics.activeDecoder == null)
    }

    @Test
    fun automaticUsesTheHardwareDecoderWhereThereIsOne() = withDecoding(HardwareAccelerationMode.AUTO) { engine ->
        assertHardwareWhereExpected(engine)
    }

    @Test
    fun hardwarePreferredUsesTheHardwareDecoderWhereThereIsOne() =
        withDecoding(HardwareAccelerationMode.HARDWARE_PREFERRED) { engine ->
            assertHardwareWhereExpected(engine)
        }

    @Test
    fun theModeCanBeChangedBetweenFiles() = withDecoding(HardwareAccelerationMode.SOFTWARE) { engine ->
        assertFalse(awaitDiagnostics(engine) { it.activeVideoCodec != null }.hwdecActive)
        if (!expectHardware) return@withDecoding
        engine.setHardwareAccelerationMode(HardwareAccelerationMode.HARDWARE_PREFERRED).getOrThrow()
        runBlocking { engine.open(item(), 0).getOrThrow() }
        engine.play().getOrThrow()
        val after = awaitDiagnostics(engine) { it.hwdecActive }
        assertTrue(after.activeDecoder.orEmpty().startsWith("hw:"), "decoder: ${after.activeDecoder}")
    }

    private fun assertHardwareWhereExpected(engine: MpvPlayerEngine) {
        if (!expectHardware) {
            assertTrue(awaitDiagnostics(engine) { it.activeVideoCodec != null }.activeVideoCodec != null)
            return
        }
        val diagnostics = awaitDiagnostics(engine) { it.hwdecActive }
        assertTrue(diagnostics.activeDecoder.orEmpty().startsWith("hw:"), "decoder: ${diagnostics.activeDecoder}")
    }

    private fun awaitDiagnostics(
        engine: MpvPlayerEngine,
        predicate: (PlaybackDiagnosticsSnapshot) -> Boolean,
    ): PlaybackDiagnosticsSnapshot = runBlocking {
        withTimeout(TIMEOUT) {
            var snapshot = engine.getDiagnosticsSnapshot()
            while (snapshot == null || !predicate(snapshot)) {
                delay(POLL_MILLIS)
                snapshot = engine.getDiagnosticsSnapshot()
            }
            snapshot
        }
    }

    private fun item(): MediaItem {
        val uri = clip.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, "clip", MediaKind.VIDEO, fileName = clip.name)
    }

    private fun withDecoding(mode: HardwareAccelerationMode, block: (MpvPlayerEngine) -> Unit) {
        if (!clip.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            engine.setVolume(0f)
            runBlocking {
                engine.open(item(), 0).getOrThrow()
                engine.setHardwareAccelerationMode(mode).getOrThrow()
                engine.play().getOrThrow()
                withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing } }
            }
            block(engine)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val POLL_MILLIS = 100L
    }
}
