package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.SubtitleStyle
import dev.vimivo.playback.TrackType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvSubtitleLiveTest {
    private val videoClip = File("../testmedia/video_landscape.mp4").absoluteFile
    private val srtClip = File("../testmedia/sample.srt").absoluteFile
    private val banglaClip = File("../testmedia/pack/bangla_utf8.srt").absoluteFile

    @Test
    fun externalSubtitleCanBeAddedSelectedAndDeselected() = withEngine { engine ->
        assertTrue(srtClip.isFile, "test subtitle must exist")
        val ext = ExternalSubtitle(
            uri = srtClip.toURI().toString(),
            label = "English Sample",
            mimeType = "application/x-subrip",
        )
        engine.addExternalSubtitle(ext).getOrThrow()

        val state = awaitState(engine) { it.subtitleTracks.isNotEmpty() }
        val track = state.subtitleTracks.first()
        assertEquals(TrackType.SUBTITLE, track.type)

        // Disable subtitle track
        engine.selectSubtitleTrack(null).getOrThrow()
        assertEquals("no", engine.readProperty("sid"))

        // Re-enable subtitle track
        engine.selectSubtitleTrack(track.id).getOrThrow()
        val sid = engine.readProperty("sid")
        assertTrue(sid != null && sid != "no", "sid should be active")
    }

    @Test
    fun subtitleStylingReachesLibmpv() = withEngine { engine ->
        val style = SubtitleStyle(
            fontSizeSp = 36,
            textColorHex = "#FFFF00",
            bottomMarginPercent = 15,
            outlineSize = 3.0f,
            outlineColorHex = "#000000",
            shadowOffset = 2.0f,
            assOverride = "force",
            encoding = "utf-8",
        )
        engine.applySubtitleStyle(style).getOrThrow()

        assertEquals(36.0, engine.readProperty("sub-font-size")!!.toDouble(), 0.01)
        val color = engine.readProperty("sub-color").orEmpty()
        assertTrue(color.startsWith("#") && (color.contains("FFFF00") || color == "#FFFFFF00"), "sub-color was '$color'")
        assertEquals(85, engine.readProperty("sub-pos")!!.toDouble().toInt()) // 100 - 15 = 85
        assertEquals(3.0, engine.readProperty("sub-border-size")!!.toDouble(), 0.01)
        assertEquals(2.0, engine.readProperty("sub-shadow-offset")!!.toDouble(), 0.01)
        assertEquals("force", engine.readProperty("sub-ass-override"))
        assertEquals("utf-8", engine.readProperty("sub-codepage"))
    }

    @Test
    fun subtitleTimingAndSecondarySubtitleControlsReachLibmpv() = withEngine { engine ->
        engine.setSubtitleDelay(1250L).getOrThrow()
        assertEquals(1.25, engine.readProperty("sub-delay")!!.toDouble(), 0.01)

        engine.setSubtitleDelay(-750L).getOrThrow()
        assertEquals(-0.75, engine.readProperty("sub-delay")!!.toDouble(), 0.01)

        engine.setSubtitleSpeed(1.25f).getOrThrow()
        assertEquals(1.25, engine.readProperty("sub-speed")!!.toDouble(), 0.01)

        engine.setSubtitleFps(23.976f).getOrThrow()
        assertEquals(23.976, engine.readProperty("sub-fps")!!.toDouble(), 0.01)

        engine.setSecondarySubtitleDelay(2000L).getOrThrow()
        assertEquals(2.0, engine.readProperty("secondary-sub-delay")!!.toDouble(), 0.01)
    }

    @Test
    fun banglaUtf8SubtitleLoadsCleanly() = withEngine { engine ->
        if (!banglaClip.isFile) {
            println("Skipping Bangla subtitle test: pack not generated")
            return@withEngine
        }
        val banglaExt = ExternalSubtitle(
            uri = banglaClip.toURI().toString(),
            label = "Bangla UTF-8",
            mimeType = "application/x-subrip",
        )
        engine.addExternalSubtitle(banglaExt).getOrThrow()

        val state = awaitState(engine) { it.subtitleTracks.isNotEmpty() }
        assertTrue(state.subtitleTracks.any { it.label.contains("bangla", ignoreCase = true) || it.isSelected })
    }

    private fun item(): MediaItem {
        val uri = videoClip.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, "video_landscape", MediaKind.VIDEO, fileName = videoClip.name)
    }

    private fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean): PlaybackState =
        runBlocking { withTimeout(TIMEOUT) { engine.state.first(predicate) } }

    private fun withEngine(block: (MpvPlayerEngine) -> Unit) {
        if (!videoClip.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        MpvPlayerEngine().use { engine ->
            engine.setVolume(0f)
            runBlocking {
                engine.open(item(), 0).getOrThrow()
                engine.play().getOrThrow()
                withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing && it.positionMillis > 50 } }
                engine.pause().getOrThrow()
            }
            block(engine)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
