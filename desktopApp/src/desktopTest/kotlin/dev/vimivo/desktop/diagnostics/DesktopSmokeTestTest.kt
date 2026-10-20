package dev.vimivo.desktop.diagnostics

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackError
import dev.vimivo.playback.PlaybackErrorCode
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSmokeTestTest {

    @Test
    fun parsesFlagAndFilesInAnyOrder() {
        val request = DesktopSmokeTest.parse(arrayOf("/a.mp4", "--smoke-test", "/b.mp3"))

        assertEquals(listOf(File("/a.mp4"), File("/b.mp3")), request?.files)
        assertNull(DesktopSmokeTest.parse(arrayOf("/a.mp4")))
    }

    @Test
    fun passesOnceVideoPlaybackAdvancesAndThePictureIsOnScreen() = runTest {
        val file = tempFile("clip.mp4")
        val state = MutableStateFlow(PlaybackState())

        val results = DesktopSmokeTest.run(
            listOf(file),
            state,
            ::media,
            play = playsVideo(state),
            probePicture = { PICTURE },
        )

        val passed = assertIs<DesktopSmokeResult.Passed>(results.single())
        assertEquals(2_000, passed.positionMillis)
        assertTrue(DesktopSmokeTest.format(passed).startsWith("VIMIVO_SMOKE PASS clip.mp4"))
    }

    /** Regression (D1): audio and a moving clock with a black window used to pass. */
    @Test
    fun failsVideoThatDecodesButShowsABlackWindow() = runTest {
        val file = tempFile("clip.mp4")
        val state = MutableStateFlow(PlaybackState())

        val results = DesktopSmokeTest.run(
            listOf(file),
            state,
            ::media,
            play = playsVideo(state),
            probePicture = { BLACK },
        )

        val failed = assertIs<DesktopSmokeResult.Failed>(results.single())
        assertTrue("shows no picture" in failed.reason, failed.reason)
    }

    private fun playsVideo(state: MutableStateFlow<PlaybackState>): suspend (MediaItem) -> Result<Unit> = { media ->
        state.value = PlaybackState(
            media = media,
            status = PlaybackStatus.Playing,
            positionMillis = 2_000,
            videoTracks = listOf(TrackInfo("1", TrackType.VIDEO, "h264")),
        )
        Result.success(Unit)
    }

    @Test
    fun failsVideoThatPlaysWithoutAVideoTrack() = runTest {
        val file = tempFile("clip.mkv")
        val state = MutableStateFlow(PlaybackState())

        val results = DesktopSmokeTest.run(listOf(file), state, ::media, play = { media ->
            state.value = PlaybackState(media = media, status = PlaybackStatus.Playing, positionMillis = 2_000)
            Result.success(Unit)
        })

        assertIs<DesktopSmokeResult.Failed>(results.single())
    }

    @Test
    fun reportsEngineErrorsAndTimeouts() = runTest {
        val broken = tempFile("broken.mp3")
        val stuck = tempFile("stuck.flac")
        val state = MutableStateFlow(PlaybackState())

        val unsupported = PlaybackStatus.Error(PlaybackError(PlaybackErrorCode.UNSUPPORTED_CODEC, "nope"))

        val play: suspend (MediaItem) -> Result<Unit> = { media ->
            val status = if (media.fileName == broken.name) unsupported else PlaybackStatus.Buffering
            state.value = PlaybackState(media = media, status = status)
            Result.success(Unit)
        }

        val results = DesktopSmokeTest.run(listOf(broken, stuck), state, ::media, play, timeoutMillis = 1_000)

        val error = assertIs<DesktopSmokeResult.Failed>(results[0])
        assertTrue(error.reason.startsWith("UNSUPPORTED_CODEC"))
        val timeout = assertIs<DesktopSmokeResult.Failed>(results[1])
        assertTrue(timeout.reason.startsWith("timed out"))
    }

    @Test
    fun reportsOpenFailuresImmediately() = runTest {
        val file = tempFile("locked.mp3")

        val results = DesktopSmokeTest.run(listOf(file), MutableStateFlow(PlaybackState()), ::media, play = {
            Result.failure(IllegalStateException("libmpv missing"))
        })

        assertEquals("open failed: libmpv missing", assertIs<DesktopSmokeResult.Failed>(results.single()).reason)
    }

    @Test
    fun failsMissingFilesWithoutPlaying() = runTest {
        var played = false
        val missing = File("/does/not/exist.mp4")

        val results = DesktopSmokeTest.run(listOf(missing), MutableStateFlow(PlaybackState()), ::media, play = {
            played = true
            Result.success(Unit)
        })

        assertEquals("file not found", assertIs<DesktopSmokeResult.Failed>(results.single()).reason)
        assertEquals(false, played)
    }

    @Test
    fun publishesToStdoutAndOptionalReportFile() {
        val report = Files.createTempFile("vimivo-smoke", ".log").toFile().apply { deleteOnExit() }
        val printed = mutableListOf<String>()
        val results = listOf(DesktopSmokeResult.Failed(File("a.mp4"), "nope"))

        DesktopSmokeTest.publish(results, report.path, printed::add)

        assertEquals(listOf("VIMIVO_SMOKE FAIL a.mp4 reason=nope"), printed)
        assertEquals("VIMIVO_SMOKE FAIL a.mp4 reason=nope\n", report.readText())
    }

    private fun tempFile(name: String): File =
        Files.createTempDirectory("vimivo-smoke").resolve(name).toFile().apply {
            writeText("x")
            deleteOnExit()
        }

    private fun media(file: File) = MediaItem(
        id = MediaId(file.toURI().toString()),
        uri = file.toURI().toString(),
        title = file.nameWithoutExtension,
        kind = if (file.extension in setOf("mp4", "mkv")) MediaKind.VIDEO else MediaKind.AUDIO,
        fileName = file.name,
    )

    private companion object {
        val PICTURE = VideoPixelSample(litFraction = 0.7, distinctColours = 12, width = 960, height = 456)
        val BLACK = VideoPixelSample(litFraction = 0.0, distinctColours = 0, width = 960, height = 456)
    }
}
