package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ColorTransfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** V1 #9 with the real libmpv and tiny HEVC 10-bit BT.2020 clips: HDR10 and HLG are detected, SDR is not. */
class MpvHdrLiveTest {
    @Test
    fun hdr10ClipIsReportedAsPq() = assertTransfer("hdr10_tiny.mkv", ColorTransfer.HDR10_PQ)

    @Test
    fun hlgClipIsReportedAsHlg() = assertTransfer("hlg_tiny.mkv", ColorTransfer.HLG)

    @Test
    fun sdrClipIsReportedAsSdr() = assertTransfer("video_sample.mkv", ColorTransfer.SDR_BT709, orUnknown = true)

    private fun assertTransfer(name: String, expected: ColorTransfer, orUnknown: Boolean = false) {
        val file = File("../testmedia/$name").absoluteFile
        if (!file.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            val uri = file.toPath().toUri().toString()
            val media = MediaItem(MediaId(uri), uri, name, MediaKind.VIDEO, fileName = name)
            runBlocking {
                engine.setVolume(0f)
                engine.open(media).getOrThrow()
                engine.play().getOrThrow()
                withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 200 } }
            }
            val actual = engine.getDiagnosticsSnapshot()!!.colorTransfer
            // An SDR file without colour tags may report no transfer function at all.
            if (orUnknown && actual == ColorTransfer.UNKNOWN) return
            assertEquals(expected, actual)
        }
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
