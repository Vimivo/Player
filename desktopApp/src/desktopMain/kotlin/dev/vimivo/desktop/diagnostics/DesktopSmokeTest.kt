package dev.vimivo.desktop.diagnostics

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * `--smoke-test <file>...` launches the real window and renderer, plays each file, and reports
 * whether playback actually advanced. It exists so the release gate can verify launch + playback
 * on every desktop OS (including headless CI runners) without screen-recording permissions.
 */
internal data class DesktopSmokeTestRequest(val files: List<File>)

internal sealed interface DesktopSmokeResult {
    val file: File

    data class Passed(
        override val file: File,
        val positionMillis: Long,
        val videoTracks: Int,
        val picture: VideoPixelSample? = null,
    ) : DesktopSmokeResult
    data class Failed(override val file: File, val reason: String) : DesktopSmokeResult
}

internal object DesktopSmokeTest {
    const val FLAG = "--smoke-test"
    const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    const val MIN_POSITION_MILLIS = 1_500L

    fun parse(args: Array<String>): DesktopSmokeTestRequest? =
        if (FLAG in args) DesktopSmokeTestRequest(args.filterNot { it == FLAG }.map(::File)) else null

    @Suppress("LongParameterList") // Each collaborator is injected so the gate runs against the real app.
    suspend fun run(
        files: List<File>,
        state: StateFlow<PlaybackState>,
        toMedia: (File) -> MediaItem,
        play: suspend (MediaItem) -> Result<Unit>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        /** Reads the pixels under the video surface; null where no surface is shown. */
        probePicture: () -> VideoPixelSample? = { null },
    ): List<DesktopSmokeResult> = files.map { file ->
        if (!file.isFile) return@map DesktopSmokeResult.Failed(file, "file not found")
        val media = toMedia(file)
        play(media).exceptionOrNull()?.let { error ->
            return@map DesktopSmokeResult.Failed(file, "open failed: ${error.message ?: error::class.simpleName}")
        }
        val reached = withTimeoutOrNull(timeoutMillis) {
            state.first { playback -> playback.media?.uri == media.uri && playback.isTerminalFor() }
        }
        val latest = reached ?: state.value.takeIf { it.media?.uri == media.uri }
        val result = evaluate(file, media, latest, timedOut = reached == null)
        val isVideo = media.kind == MediaKind.VIDEO
        if (result is DesktopSmokeResult.Passed && isVideo) checkPicture(result, probePicture) else result
    }

    /**
     * Replays [file] before each step, runs the step (fullscreen, resize, …) and checks the picture is still on screen
     * afterwards, so a renderer that loses its surface on a window change fails the gate.
     */
    suspend fun checkTransitions(
        file: File,
        steps: List<Pair<String, () -> Unit>>,
        replay: suspend () -> Unit,
        probePicture: () -> VideoPixelSample?,
    ): List<DesktopSmokeResult> = steps.map { (name, step) ->
        replay()
        step()
        delay(TRANSITION_SETTLE_MILLIS)
        val passed = DesktopSmokeResult.Passed(File("${file.name}#$name"), positionMillis = 0, videoTracks = 1)
        checkPicture(passed, probePicture)
    }

    /**
     * Prints each result and, when [reportPath] is set, also writes them to that file: Windows GUI executables
     * have no console attached, so CI reads the report file there.
     */
    fun publish(results: List<DesktopSmokeResult>, reportPath: String?, print: (String) -> Unit = ::println) {
        val lines = results.map(::format)
        lines.forEach(print)
        val report = lines.joinToString(separator = "\n", postfix = "\n")
        reportPath?.takeIf(String::isNotBlank)?.let { File(it).writeText(report) }
    }

    fun format(result: DesktopSmokeResult): String = when (result) {
        is DesktopSmokeResult.Passed -> with(result) {
            "VIMIVO_SMOKE PASS ${file.name} position=${positionMillis}ms videoTracks=$videoTracks" +
                (picture?.let { " picture=[$it]" } ?: "")
        }
        is DesktopSmokeResult.Failed -> "VIMIVO_SMOKE FAIL ${result.file.name} reason=${result.reason}"
    }

    /** Video counts only when the picture is on screen: audio and a moving clock are not enough (black-video bug). */
    private suspend fun checkPicture(
        passed: DesktopSmokeResult.Passed,
        probePicture: () -> VideoPixelSample?,
    ): DesktopSmokeResult {
        var picture: VideoPixelSample? = null
        repeat(PICTURE_ATTEMPTS) {
            // The probe reads bounds on the event thread and the screen off it.
            picture = withContext(Dispatchers.IO) { probePicture() }
            if (picture?.showsPicture == true) return passed.copy(picture = picture)
            delay(PICTURE_RETRY_MILLIS)
        }
        val seen = picture?.toString() ?: "surface not shown"
        return DesktopSmokeResult.Failed(passed.file, "video decoded but the window shows no picture ($seen)")
    }

    // Up to 3 s: a cold software renderer (no GPU, CI) presents its first 1080p frames slowly, in strips.
    private const val PICTURE_ATTEMPTS = 10
    private const val TRANSITION_SETTLE_MILLIS = 1_500L
    private const val PICTURE_RETRY_MILLIS = 300L

    private fun PlaybackState.isTerminalFor(): Boolean =
        status is PlaybackStatus.Error || (status == PlaybackStatus.Playing && positionMillis >= MIN_POSITION_MILLIS)

    private fun evaluate(
        file: File,
        media: MediaItem,
        playback: PlaybackState?,
        timedOut: Boolean,
    ): DesktopSmokeResult {
        val status = playback?.status
        return when {
            status is PlaybackStatus.Error ->
                DesktopSmokeResult.Failed(file, "${status.error.code}: ${status.error.message}")
            timedOut || playback == null -> {
                val position = playback?.positionMillis ?: 0
                DesktopSmokeResult.Failed(file, "timed out (status=${status ?: "none"} position=${position}ms)")
            }
            media.kind == MediaKind.VIDEO && playback.videoTracks.isEmpty() ->
                DesktopSmokeResult.Failed(file, "playback advanced but no video track was decoded")
            else -> DesktopSmokeResult.Passed(file, playback.positionMillis, playback.videoTracks.size)
        }
    }
}
