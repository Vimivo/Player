@file:Suppress("ArgumentListWrapping", "MagicNumber", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop.playback

import dev.vimivo.playback.CodecSupportLevel
import dev.vimivo.playback.ColorSpace
import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.HardwareAccelerationMode
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaCompatibilityTest {

    private val probeService = DesktopMediaProbeService()

    @Test
    fun testProbeMissingFileReturnsUnsupportedGracefully() = runBlocking {
        val missing = File("/nonexistent/path/to/media_${System.currentTimeMillis()}.mp4")
        val result = probeService.probeUri(missing.toURI().toString())
        assertTrue(result.isSuccess)
        val details = result.getOrThrow()
        assertEquals(CodecSupportLevel.UNSUPPORTED, details.overallCompatibility)
        assertTrue(details.statusMessage.contains("does not exist", ignoreCase = true))
    }

    @Test
    fun testProbeEmptyZeroByteFileReturnsUnsupported() = runBlocking {
        val emptyFile = File.createTempFile("vimivo_test_empty_", ".mp3")
        try {
            val result = probeService.probeUri(emptyFile.toURI().toString())
            assertTrue(result.isSuccess)
            val details = result.getOrThrow()
            assertEquals(CodecSupportLevel.UNSUPPORTED, details.overallCompatibility)
            assertTrue(details.statusMessage.contains("0 bytes", ignoreCase = true))
        } finally {
            emptyFile.delete()
        }
    }

    @Test
    fun testProbeTruncatedFileBelowFourBytes() = runBlocking {
        val truncatedFile = File.createTempFile("vimivo_test_truncated_", ".mkv")
        try {
            truncatedFile.writeBytes(byteArrayOf(0x1A, 0x45))
            val result = probeService.probeUri(truncatedFile.toURI().toString())
            assertTrue(result.isSuccess)
            val details = result.getOrThrow()
            assertEquals(CodecSupportLevel.UNSUPPORTED, details.overallCompatibility)
            assertTrue(details.statusMessage.contains("Truncated", ignoreCase = true))
        } finally {
            truncatedFile.delete()
        }
    }

    @Test
    fun testProbeSniffsFlacContainerHeader() = runBlocking {
        val flacFile = File.createTempFile("vimivo_test_", ".flac")
        try {
            flacFile.writeBytes(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(), 0, 0, 0, 0))
            val result = probeService.probeUri(flacFile.toURI().toString())
            assertTrue(result.isSuccess)
            val details = result.getOrThrow()
            assertEquals("FLAC", details.container)
            assertEquals(CodecSupportLevel.SUPPORTED_AND_VERIFIED, details.overallCompatibility)
        } finally {
            flacFile.delete()
        }
    }

    @Test
    fun testProbeSniffsOggContainerHeader() = runBlocking {
        val oggFile = File.createTempFile("vimivo_test_", ".ogg")
        try {
            oggFile.writeBytes(byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(), 0, 2, 0, 0))
            val result = probeService.probeUri(oggFile.toURI().toString())
            assertTrue(result.isSuccess)
            val details = result.getOrThrow()
            assertEquals("OGG", details.container)
            assertEquals(CodecSupportLevel.SUPPORTED_AND_VERIFIED, details.overallCompatibility)
        } finally {
            oggFile.delete()
        }
    }

    @Test
    fun testProbeSniffsWavContainerHeader() = runBlocking {
        val wavFile = File.createTempFile("vimivo_test_", ".wav")
        try {
            val bytes = ByteArray(16)
            bytes[0] = 'R'.code.toByte()
            bytes[1] = 'I'.code.toByte()
            bytes[2] = 'F'.code.toByte()
            bytes[3] = 'F'.code.toByte()
            bytes[8] = 'W'.code.toByte()
            bytes[9] = 'A'.code.toByte()
            bytes[10] = 'V'.code.toByte()
            bytes[11] = 'E'.code.toByte()
            wavFile.writeBytes(bytes)
            val result = probeService.probeUri(wavFile.toURI().toString())
            assertTrue(result.isSuccess)
            val details = result.getOrThrow()
            assertEquals("WAV", details.container)
            assertEquals(CodecSupportLevel.SUPPORTED_AND_VERIFIED, details.overallCompatibility)
        } finally {
            wavFile.delete()
        }
    }

    @Test
    fun testProbeSniffsMatroskaEbmlHeader() = runBlocking {
        val mkvFile = File.createTempFile("vimivo_test_", ".mkv")
        try {
            mkvFile.writeBytes(byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00, 0x00, 0x00))
            val result = probeService.probeUri(mkvFile.toURI().toString())
            assertTrue(result.isSuccess)
            val details = result.getOrThrow()
            assertEquals("MKV", details.container)
            assertEquals(CodecSupportLevel.SUPPORTED_AND_VERIFIED, details.overallCompatibility)
        } finally {
            mkvFile.delete()
        }
    }

    @Test
    fun testColorTransferAndHdrSemantics() {
        assertFalse(ColorTransfer.SDR_BT709.isHdr)
        assertTrue(ColorTransfer.HDR10_PQ.isHdr)
        assertTrue(ColorTransfer.HLG.isHdr)
        assertFalse(ColorTransfer.UNKNOWN.isHdr)

        assertEquals("ITU-R BT.2020 (Wide Color Gamut)", ColorSpace.BT2020.label)
        assertEquals("ITU-R BT.709", ColorSpace.BT709.label)
    }

    @Test
    fun testHardwareAccelerationModeEnums() {
        assertEquals("Automatic (Hardware with Software Fallback)", HardwareAccelerationMode.AUTO.label)
        assertEquals("Software Only (CPU)", HardwareAccelerationMode.SOFTWARE.label)
        assertEquals("Hardware Acceleration Preferred", HardwareAccelerationMode.HARDWARE_PREFERRED.label)
    }

    @Test
    fun testMpvPlayerEngineHardwareModeAndDiagnosticsSafelyHandledWhenClosed() {
        val engine = MpvPlayerEngine()
        try {
            // Calling hardware mode before open should safely return failure without throwing uncaught exception
            val modeResult = engine.setHardwareAccelerationMode(HardwareAccelerationMode.SOFTWARE)
            assertTrue(modeResult.isFailure)

            // Seeking with exact=false before open should return failure safely
            val seekFastResult = engine.seekTo(1000L, exact = false)
            assertTrue(seekFastResult.isFailure)

            val seekExactResult = engine.seekTo(1000L, exact = true)
            assertTrue(seekExactResult.isFailure)

            // Diagnostics should be null before any media is loaded
            assertNull(engine.getDiagnosticsSnapshot())

            // Software decoder fallback should fail cleanly when not open
            val fallbackResult = engine.attemptSoftwareDecoderFallback()
            assertTrue(fallbackResult.isFailure)
        } finally {
            engine.close()
        }
    }
}
