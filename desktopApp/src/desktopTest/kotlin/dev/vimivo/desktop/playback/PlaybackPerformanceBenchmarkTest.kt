@file:Suppress("ArgumentListWrapping", "MagicNumber", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop.playback

import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackDiagnosticsSnapshot
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackPerformanceBenchmarkTest {

    private val probeService = DesktopMediaProbeService()

    @Test
    fun testProbeHeaderSniffingPerformanceUnderTenMilliseconds() = runBlocking {
        val flacFile = File.createTempFile("vimivo_bench_", ".flac")
        try {
            flacFile.writeBytes(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(), 0, 0, 0, 0))

            // Warmup
            probeService.probeUri(flacFile.toURI().toString())

            // Benchmark 20 iterations
            val elapsed = measureTimeMillis {
                repeat(20) {
                    val result = probeService.probeUri(flacFile.toURI().toString())
                    assertTrue(result.isSuccess)
                }
            }
            val avgMs = elapsed / 20.0
            assertTrue(avgMs < 10.0, "Average probe sniffing time ($avgMs ms) should be well under 10ms")
        } finally {
            flacFile.delete()
        }
    }

    @Test
    fun testPlaybackDiagnosticsSnapshotDataIntegrity() {
        val snapshot = PlaybackDiagnosticsSnapshot(
            engineId = EngineId.MPV,
            mediaUri = "file:///tmp/fixture_4k_hdr.mkv",
            activeVideoCodec = "hevc",
            activeAudioCodec = "flac",
            activeDecoder = "hw:videotoolbox",
            hwdecActive = true,
            hwdecMode = "videotoolbox",
            activeRenderer = "libmpv/cocoa",
            droppedFrames = 2L,
            totalFrames = 1800L,
            avDriftMillis = 1.25,
            estimatedFps = 59.94f,
            cacheBufferBytes = 16_777_216L,
            colorTransfer = ColorTransfer.HDR10_PQ,
            positionMillis = 30_000L,
            durationMillis = 120_000L,
        )

        assertEquals(EngineId.MPV, snapshot.engineId)
        assertEquals("hevc", snapshot.activeVideoCodec)
        assertEquals("flac", snapshot.activeAudioCodec)
        assertTrue(snapshot.hwdecActive)
        assertEquals("hw:videotoolbox", snapshot.activeDecoder)
        assertEquals(2L, snapshot.droppedFrames)
        assertEquals(1.25, snapshot.avDriftMillis)
        assertEquals(59.94f, snapshot.estimatedFps)
        assertEquals(16_777_216L, snapshot.cacheBufferBytes)
        assertEquals(ColorTransfer.HDR10_PQ, snapshot.colorTransfer)
        assertTrue(snapshot.colorTransfer?.isHdr == true)
        assertEquals(30_000L, snapshot.positionMillis)
        assertEquals(120_000L, snapshot.durationMillis)
    }

    @Test
    fun testSeekModeValidation() {
        val engine = MpvPlayerEngine()
        try {
            // Validate that seeking while uninitialized returns safe failure rather than crashing
            val fastSeek = engine.seekTo(15_000L, exact = false)
            assertTrue(fastSeek.isFailure)

            val exactSeek = engine.seekTo(15_000L, exact = true)
            assertTrue(exactSeek.isFailure)

            assertNotNull(fastSeek.exceptionOrNull())
            assertNotNull(exactSeek.exceptionOrNull())
        } finally {
            engine.close()
        }
    }
}
