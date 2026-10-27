package dev.vimivo.desktop.playback

import dev.vimivo.playback.CodecSupportLevel
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Live libmpv: Media Info reads real streams without external programs; skipped where libmpv is unavailable. */
class DesktopMediaProbeLibmpvTest {
    private val mkv = File("../testmedia/video_sample.mkv").absoluteFile
    private val mp4 = File("../testmedia/video_landscape.mp4").absoluteFile

    @Test
    fun readsVideoAudioAndSubtitleStreamsOfAMatroskaFile() = withProbe { probe ->
        val details = probe.probeUri(mkv.toURI().toString()).getOrThrow()
        assertEquals("MKV", details.container)
        assertEquals("h264", details.videoStreams.single().codec)
        assertEquals(1280, details.videoStreams.single().width)
        assertEquals(720, details.videoStreams.single().height)
        assertEquals(8, details.videoStreams.single().bitDepth)
        assertEquals("aac", details.audioStreams.single().codec)
        val subtitle = details.subtitleStreams.single()
        assertEquals("subrip", subtitle.codec)
        assertEquals("eng", subtitle.language)
        assertTrue(assertNotNull(details.durationMillis) in 4_900L..5_100L)
        assertNotNull(details.bitrate)
        assertTrue(details.statusMessage.startsWith("Probed via native inspection"))
        assertEquals(CodecSupportLevel.HARDWARE_ACCELERATED_VERIFIED, details.overallCompatibility)
    }

    @Test
    fun readsAnMp4FileAsMp4() = withProbe { probe ->
        val details = probe.probeUri(mp4.toURI().toString()).getOrThrow()
        assertEquals("MP4", details.container)
        assertEquals(1920, details.videoStreams.single().width)
        assertEquals("aac", details.audioStreams.single().codec)
    }

    private fun withProbe(block: suspend (DesktopMediaProbeService) -> Unit) {
        if (!mkv.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        runBlocking { block(DesktopMediaProbeService()) }
    }
}
