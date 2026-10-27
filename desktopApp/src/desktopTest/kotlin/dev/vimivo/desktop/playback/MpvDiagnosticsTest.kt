package dev.vimivo.desktop.playback

import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.EngineId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvDiagnosticsTest {
    private class FakeProperties(private val values: Map<String, Any>) : MpvProperties {
        override fun string(name: String) = values[name] as? String
        override fun flag(name: String) = values[name] as? Boolean
        override fun long(name: String) = (values[name] as? Number)?.toLong()
        override fun double(name: String) = (values[name] as? Number)?.toDouble()
    }

    private val context = DiagnosticsContext(
        engineId = EngineId.MPV,
        mediaUri = "file:///movie.mkv",
        positionMillis = 1_500,
        durationMillis = 5_000,
        requestedHwdec = "auto",
        defaultRenderer = "gpu",
        softwareRendering = false,
    )

    @Test
    fun transferFunctionComesFromVideoParamsGammaAsMpvReportsIt() {
        // E5: for the HDR10 file mpv 0.41 reports colormatrix "bt.2020-ncl" and gamma "pq".
        assertEquals(ColorTransfer.HDR10_PQ, MpvDiagnostics.colorTransfer("pq"))
        assertEquals(ColorTransfer.HLG, MpvDiagnostics.colorTransfer("hlg"))
        assertEquals(ColorTransfer.SDR_BT709, MpvDiagnostics.colorTransfer("bt.1886"))
        assertEquals(ColorTransfer.UNKNOWN, MpvDiagnostics.colorTransfer(null))
        assertEquals(ColorTransfer.UNKNOWN, MpvDiagnostics.colorTransfer("something-new"))
    }

    @Test
    fun anHdr10ClipIsReportedAsHdrEvenThoughItsColourMatrixNamesNeitherPqNorHlg() {
        val snapshot = MpvDiagnostics.snapshot(
            FakeProperties(mapOf("colormatrix" to "bt.2020-ncl", "video-params/gamma" to "pq")),
            context,
        )
        assertEquals(ColorTransfer.HDR10_PQ, snapshot.colorTransfer)
        assertTrue(snapshot.colorTransfer!!.isHdr)
    }

    @Test
    fun anSdrClipIsNotReportedAsHdr() {
        val snapshot = MpvDiagnostics.snapshot(FakeProperties(mapOf("video-params/gamma" to "bt.1886")), context)
        assertFalse(snapshot.colorTransfer!!.isHdr)
    }

    @Test
    fun anActiveHardwareDecoderIsReportedWithItsApi() {
        val snapshot = MpvDiagnostics.snapshot(
            FakeProperties(mapOf("video-codec" to "hevc", "hwdec-current" to "videotoolbox", "hwdec-active" to true)),
            context,
        )
        assertTrue(snapshot.hwdecActive)
        assertEquals("hw:videotoolbox", snapshot.activeDecoder)
        assertEquals("videotoolbox", snapshot.hwdecMode)
    }

    @Test
    fun softwareDecodingNamesTheCodecAndKeepsTheRequestedMode() {
        val snapshot = MpvDiagnostics.snapshot(
            FakeProperties(mapOf("video-codec" to "av1", "hwdec-current" to "no")),
            context,
        )
        assertFalse(snapshot.hwdecActive)
        assertEquals("sw:av1", snapshot.activeDecoder)
        assertEquals("no", snapshot.hwdecMode)
    }

    @Test
    fun rendererFallsBackToTheDefaultAndMarksSoftwareRendering() {
        val plain = MpvDiagnostics.snapshot(FakeProperties(emptyMap()), context)
        assertEquals("gpu", plain.activeRenderer)
        val software = MpvDiagnostics.snapshot(FakeProperties(emptyMap()), context.copy(softwareRendering = true))
        assertTrue("software rendering" in software.activeRenderer!!)
    }

    @Test
    fun countersAndDriftAreCopiedAndConverted() {
        val snapshot = MpvDiagnostics.snapshot(
            FakeProperties(
                mapOf(
                    "drop-frame-count" to 3,
                    "estimated-frame-count" to 120,
                    "avsync" to 0.02,
                    "container-fps" to 24.0,
                ),
            ),
            context,
        )
        assertEquals(3L, snapshot.droppedFrames)
        assertEquals(120L, snapshot.totalFrames)
        assertEquals(20.0, snapshot.avDriftMillis, 0.001)
        assertEquals(24f, snapshot.estimatedFps)
        assertEquals(1_500L, snapshot.positionMillis)
    }
}
