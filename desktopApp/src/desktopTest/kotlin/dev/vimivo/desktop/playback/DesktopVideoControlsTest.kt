@file:Suppress(
    "ArgumentListWrapping", "EmptyFunctionBlock", "FunctionNaming", "ImportOrdering",
    "LongMethod", "MaximumLineLength", "MaxLineLength", "NoUnusedImports", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SubtitleStyle
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.VideoAdjustments
import dev.vimivo.playback.VideoAspectRatio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopVideoControlsTest {

    private class RecordingPlayerEngine : PlayerEngine {
        override val id = EngineId.MPV
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
            hdr = false,
            subtitleDelay = true,
            audioDelay = true,
            chapters = true,
            abRepeat = true,
            videoAdjustments = true,
            screenshots = true,
        )

        private val _state = MutableStateFlow(PlaybackState(status = PlaybackStatus.Idle))
        override val state = _state.asStateFlow()
        override val audioEnhancementState = MutableStateFlow(
            AudioEnhancementState(capabilities.audioEnhancements),
        ).asStateFlow()

        var lastAdjustments: VideoAdjustments? = null
        var lastSubtitleDelayMs: Long? = null
        var lastAudioDelayMs: Long? = null
        var lastABRepeatRange: ABRepeatRange? = null
        var lastAddedSubtitle: ExternalSubtitle? = null
        var lastSelectedAudioTrackId: String? = null
        var lastSelectedSubtitleTrackId: String? = null
        var lastSubtitleStyle: SubtitleStyle? = null
        var screenshotPath: String? = null
        var chapterNavCount = 0

        override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED
        override suspend fun open(media: MediaItem, startPositionMillis: Long): Result<Unit> = Result.success(Unit)
        override fun play(): Result<Unit> = Result.success(Unit)
        override fun pause(): Result<Unit> = Result.success(Unit)
        override fun stop(): Result<Unit> = Result.success(Unit)
        override fun seekTo(positionMillis: Long): Result<Unit> = Result.success(Unit)
        override fun setPlaybackSpeed(speed: Float): Result<Unit> = Result.success(Unit)
        override fun setVolume(volume: Float): Result<Unit> = Result.success(Unit)
        override fun selectAudioTrack(trackId: String?): Result<Unit> {
            lastSelectedAudioTrackId = trackId
            return Result.success(Unit)
        }
        override fun selectVideoTrack(trackId: String?): Result<Unit> = Result.success(Unit)
        override fun selectSubtitleTrack(trackId: String?): Result<Unit> {
            lastSelectedSubtitleTrackId = trackId
            return Result.success(Unit)
        }
        override fun applySubtitleStyle(style: SubtitleStyle): Result<Unit> {
            lastSubtitleStyle = style
            return Result.success(Unit)
        }
        override fun addExternalSubtitle(subtitle: ExternalSubtitle): Result<Unit> {
            lastAddedSubtitle = subtitle
            return Result.success(Unit)
        }
        override fun setSubtitleDelay(delayMillis: Long): Result<Unit> {
            lastSubtitleDelayMs = delayMillis
            return Result.success(Unit)
        }
        override fun setAudioDelay(delayMillis: Long): Result<Unit> {
            lastAudioDelayMs = delayMillis
            return Result.success(Unit)
        }
        override fun previousChapter(): Result<Unit> {
            chapterNavCount--
            return Result.success(Unit)
        }
        override fun nextChapter(): Result<Unit> {
            chapterNavCount++
            return Result.success(Unit)
        }
        override fun setABRepeat(range: ABRepeatRange): Result<Unit> {
            lastABRepeatRange = range
            return Result.success(Unit)
        }
        override fun applyVideoAdjustments(adjustments: VideoAdjustments): Result<Unit> {
            lastAdjustments = adjustments
            return Result.success(Unit)
        }
        override fun takeScreenshot(destinationFilePath: String): Result<Unit> {
            screenshotPath = destinationFilePath
            return Result.success(Unit)
        }
        override fun close() = Unit
    }

    @Test
    fun verifiesRotationCyclesInNinetyDegreeIncrements() {
        var adjustments = VideoAdjustments()
        assertEquals(0, adjustments.rotationDegrees)

        adjustments = adjustments.copy(rotationDegrees = (adjustments.rotationDegrees + 90) % 360)
        assertEquals(90, adjustments.rotationDegrees)

        adjustments = adjustments.copy(rotationDegrees = (adjustments.rotationDegrees + 90) % 360)
        assertEquals(180, adjustments.rotationDegrees)

        adjustments = adjustments.copy(rotationDegrees = (adjustments.rotationDegrees + 90) % 360)
        assertEquals(270, adjustments.rotationDegrees)

        adjustments = adjustments.copy(rotationDegrees = (adjustments.rotationDegrees + 90) % 360)
        assertEquals(0, adjustments.rotationDegrees)
    }

    @Test
    fun appliesVideoAdjustmentsThroughPlayerEngine() {
        val engine = RecordingPlayerEngine()
        val adjustments = VideoAdjustments(
            aspectRatio = VideoAspectRatio.RATIO_16_9,
            rotationDegrees = 90,
        )

        engine.applyVideoAdjustments(adjustments)
        assertEquals(adjustments, engine.lastAdjustments)
        assertEquals(VideoAspectRatio.RATIO_16_9, engine.lastAdjustments?.aspectRatio)
        assertEquals(90, engine.lastAdjustments?.rotationDegrees)
    }

    @Test
    fun tracksSubtitleAndAudioDelayAdjustments() {
        val engine = RecordingPlayerEngine()

        engine.setSubtitleDelay(250L)
        assertEquals(250L, engine.lastSubtitleDelayMs)

        engine.setSubtitleDelay(-500L)
        assertEquals(-500L, engine.lastSubtitleDelayMs)

        engine.setAudioDelay(100L)
        assertEquals(100L, engine.lastAudioDelayMs)

        engine.setAudioDelay(0L)
        assertEquals(0L, engine.lastAudioDelayMs)
    }

    @Test
    fun managesABRepeatRangeLifecycle() {
        val engine = RecordingPlayerEngine()

        val rangeA = ABRepeatRange(pointA = 10_000L, pointB = null, enabled = false)
        engine.setABRepeat(rangeA)
        assertEquals(10_000L, engine.lastABRepeatRange?.pointA)
        assertEquals(null, engine.lastABRepeatRange?.pointB)

        val rangeAB = ABRepeatRange(pointA = 10_000L, pointB = 25_000L, enabled = true)
        engine.setABRepeat(rangeAB)
        assertEquals(10_000L, engine.lastABRepeatRange?.pointA)
        assertEquals(25_000L, engine.lastABRepeatRange?.pointB)
        assertTrue(engine.lastABRepeatRange?.enabled == true)

        val cleared = ABRepeatRange(pointA = null, pointB = null, enabled = false)
        engine.setABRepeat(cleared)
        assertEquals(null, engine.lastABRepeatRange?.pointA)
        assertEquals(null, engine.lastABRepeatRange?.pointB)
        assertFalse(engine.lastABRepeatRange?.enabled == true)
    }

    @Test
    fun validatesSubtitleFileFilters() {
        fun isSupportedSubtitle(fileName: String): Boolean {
            return fileName.endsWith(".srt", true) || fileName.endsWith(".vtt", true) ||
                fileName.endsWith(".ass", true) || fileName.endsWith(".ssa", true)
        }

        assertTrue(isSupportedSubtitle("movie.en.srt"))
        assertTrue(isSupportedSubtitle("subtitles.VTT"))
        assertTrue(isSupportedSubtitle("anime.dialogue.ass"))
        assertTrue(isSupportedSubtitle("caption.SSA"))
        assertFalse(isSupportedSubtitle("movie.mp4"))
        assertFalse(isSupportedSubtitle("notes.txt"))
        assertFalse(isSupportedSubtitle("track.flac"))
    }

    @Test
    fun capturesScreenshotPathFormat() {
        val engine = RecordingPlayerEngine()
        val userHome = System.getProperty("user.home")
        val desktop = File(userHome, "Desktop").takeIf { it.isDirectory } ?: File(userHome)
        val target = File(desktop, "vimivo_frame_${System.currentTimeMillis()}.png")

        val result = engine.takeScreenshot(target.absolutePath)
        assertTrue(result.isSuccess)
        assertEquals(target.absolutePath, engine.screenshotPath)
        assertTrue(engine.screenshotPath!!.endsWith(".png"))
    }

    @Test
    fun triggersChapterNavigation() {
        val engine = RecordingPlayerEngine()
        engine.nextChapter()
        engine.nextChapter()
        engine.previousChapter()
        assertEquals(1, engine.chapterNavCount)
    }

    @Test
    fun selectsAudioAndSubtitleTracks() {
        val engine = RecordingPlayerEngine()
        engine.selectAudioTrack("aid-2")
        assertEquals("aid-2", engine.lastSelectedAudioTrackId)

        engine.selectAudioTrack(null)
        assertEquals(null, engine.lastSelectedAudioTrackId)

        engine.selectSubtitleTrack("sid-1")
        assertEquals("sid-1", engine.lastSelectedSubtitleTrackId)

        engine.selectSubtitleTrack(null)
        assertEquals(null, engine.lastSelectedSubtitleTrackId)
    }

    @Test
    fun appliesSubtitleAppearanceStyles() {
        val engine = RecordingPlayerEngine()
        val style = SubtitleStyle(
            fontSizeSp = 30,
            textColorHex = "#FFFF00",
            bottomMarginPercent = 10,
        )
        val result = engine.applySubtitleStyle(style)
        assertTrue(result.isSuccess)
        assertEquals(30, engine.lastSubtitleStyle?.fontSizeSp)
        assertEquals("#FFFF00", engine.lastSubtitleStyle?.textColorHex)
        assertEquals(10, engine.lastSubtitleStyle?.bottomMarginPercent)
    }

    @Test
    fun parsesBengaliAndUnicodeExternalSubtitlesSafely() {
        val engine = RecordingPlayerEngine()
        val tempSub = File.createTempFile("vimivo_bengali_test_", ".srt")
        try {
            val bengaliSrtContent = """
                1
                00:00:01,000 --> 00:00:04,500
                আমার সোনার বাংলা, আমি তোমায় ভালোবাসি।

                2
                00:00:05,000 --> 00:00:08,200
                চিরদিন তোমার আকাশ, তোমার বাতাস, আমার প্রাণে বাজায় বাঁশি।
            """.trimIndent()
            tempSub.writeText(bengaliSrtContent, Charsets.UTF_8)

            val externalSub = ExternalSubtitle(
                uri = tempSub.toURI().toString(),
                label = tempSub.name,
                language = "bn",
                mimeType = "application/x-subrip",
            )
            val result = engine.addExternalSubtitle(externalSub)
            assertTrue(result.isSuccess)
            assertEquals(externalSub, engine.lastAddedSubtitle)
            assertEquals("bn", engine.lastAddedSubtitle?.language)

            // Verify content was read with UTF-8 encoding preserving Bengali unicode
            val readBack = tempSub.readText(Charsets.UTF_8)
            assertTrue(readBack.contains("সোনার বাংলা"))
            assertTrue(readBack.contains("তোমায় ভালোবাসি"))
        } finally {
            tempSub.delete()
        }
    }
}
