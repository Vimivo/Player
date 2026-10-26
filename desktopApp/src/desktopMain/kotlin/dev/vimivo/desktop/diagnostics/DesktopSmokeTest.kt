package dev.vimivo.desktop.diagnostics

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.PresentedFrame
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
internal data class DesktopSmokeTestRequest(
    val files: List<File>,
    /** `--ui-capture <dir>`: instead of the gate, save screenshots of each player surface there. */
    val captureDir: File? = null,
)

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

    const val CAPTURE_FLAG = "--ui-capture"

    fun parse(args: Array<String>): DesktopSmokeTestRequest? {
        if (FLAG !in args) return null
        val captureAt = args.indexOf(CAPTURE_FLAG)
        val captureDir = if (captureAt >= 0) args.getOrNull(captureAt + 1)?.let(::File) else null
        val skipped = if (captureAt >= 0) setOf(captureAt, captureAt + 1) else emptySet()
        val files = args.filterIndexed { index, arg -> arg != FLAG && index !in skipped }
        return DesktopSmokeTestRequest(files.map(::File), captureDir)
    }

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
        val playing = withTimeoutOrNull(timeoutMillis) {
            state.first { playback -> playback.media?.uri == media.uri && playback.isTerminalFor() }
        }
        // libmpv lists a file's tracks a moment after it starts; give a video a short time to show its video track
        // before judging, so a read that raced the demuxer is not reported as "no video track was decoded".
        val reached = if (playing != null && media.kind == MediaKind.VIDEO && playing.videoTracks.isEmpty()) {
            withTimeoutOrNull(TRACK_SETTLE_MILLIS) {
                state.first { it.media?.uri == media.uri && it.videoTracks.isNotEmpty() }
            } ?: playing
        } else {
            playing
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
        presentedFrame: () -> PresentedFrame? = { null },
    ): List<DesktopSmokeResult> = steps.map { (name, step) ->
        replay()
        val before = presentedFrame()
        step()
        awaitFrameAfterWindowChange(before, presentedFrame)
        val passed = DesktopSmokeResult.Passed(File("${file.name}#$name"), positionMillis = 0, videoTracks = 1)
        checkPicture(passed, probePicture)
    }

    /**
     * Waits until the renderer has presented a frame at the window's new, settled size: the size differs from
     * [before], stays the same across polls, and another frame arrives at it. A cold software renderer needs several
     * seconds for its first 1080p frame after entering fullscreen, which a fixed delay did not always cover. Renderers
     * that do not count frames (Windows, where libmpv presents itself) fall back to a fixed settle time.
     */
    internal suspend fun awaitFrameAfterWindowChange(before: PresentedFrame?, presentedFrame: () -> PresentedFrame?) {
        if (before == null) {
            delay(TRANSITION_SETTLE_MILLIS)
            return
        }
        withTimeoutOrNull(FRAME_WAIT_MILLIS) {
            val tracker = FrameSettleTracker(before)
            var done = false
            while (!done) {
                val now = presentedFrame()
                done = now == null || tracker.isSettled(now)
                if (!done) delay(FRAME_POLL_MILLIS)
            }
        }
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
    private const val FRAME_WAIT_MILLIS = 15_000L
    private const val FRAME_POLL_MILLIS = 100L
    private const val PICTURE_RETRY_MILLIS = 300L
    private const val TRACK_SETTLE_MILLIS = 3_000L

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

/**
 * Decides when a window change has produced a real frame: the presented size differs from [before], the same new size
 * is seen twice, and the frame count has advanced at that size (so the frame was drawn after the size settled).
 */
internal class FrameSettleTracker(private val before: PresentedFrame) {
    private var candidate: PresentedFrame? = null

    fun isSettled(now: PresentedFrame): Boolean {
        val previous = candidate
        val settled = when {
            now.sameSizeAs(before) -> false
            previous == null || !previous.sameSizeAs(now) -> false
            else -> now.count > previous.count
        }
        if (now.sameSizeAs(before)) {
            candidate = null
        } else if (previous == null || !previous.sameSizeAs(now)) {
            candidate = now
        }
        return settled
    }
}
