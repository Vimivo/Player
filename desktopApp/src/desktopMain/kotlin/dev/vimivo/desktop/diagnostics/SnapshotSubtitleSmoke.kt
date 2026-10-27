package dev.vimivo.desktop.diagnostics

import dev.vimivo.playback.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** Smoke check that subtitles reach saved snapshots (V1 #16). */
internal object SnapshotSubtitleSmoke {
    private const val POLL_MILLIS = 50L
    private const val WAIT_MILLIS = 10_000L

    /** The fixture's second cue shows from 2.6 s to 4.8 s; pause well inside it. */
    private const val CUE_FROM_MILLIS = 3_000L
    private const val CUE_UNTIL_MILLIS = 4_400L
    private const val MIN_DIFFERENT_PIXELS = 200

    /**
     * Saves the same paused frame with and without subtitles and checks that only the first has subtitle pixels in
     * its bottom fifth. Burning subtitles in needs the real video output, so headless tests cannot prove it.
     */
    @Suppress("LongParameterList") // Each collaborator is injected so the gate runs against the real app.
    suspend fun check(
        file: File,
        replay: suspend () -> Unit,
        playback: () -> PlaybackState,
        selectSubtitles: () -> Unit,
        pause: suspend () -> Unit,
        snapshot: suspend (includeSubtitles: Boolean, destination: File) -> Result<Unit>,
    ): DesktopSmokeResult {
        val result = File("${file.name}#snapshot-subtitles")
        replay()
        val inCue = withTimeoutOrNull(WAIT_MILLIS) {
            var current = playback()
            while (!current.isInCue()) {
                if (current.subtitleTracks.none { it.isSelected }) selectSubtitles()
                delay(POLL_MILLIS)
                current = playback()
            }
            true
        }
        pause()
        if (inCue == null) return DesktopSmokeResult.Failed(result, "playback never reached the subtitle cue")
        val folder = Files.createTempDirectory("vimivo-smoke-snapshot").toFile()
        try {
            val plain = File(folder, "plain.png")
            val subtitled = File(folder, "subtitled.png")
            val failure = snapshot(false, plain).exceptionOrNull() ?: snapshot(true, subtitled).exceptionOrNull()
            if (failure != null) return DesktopSmokeResult.Failed(result, "snapshot failed: ${failure.message}")
            val changed = bottomFifthDifference(plain, subtitled)
            return if (changed > MIN_DIFFERENT_PIXELS) {
                DesktopSmokeResult.Passed(result, positionMillis = 0, videoTracks = 1)
            } else {
                DesktopSmokeResult.Failed(result, "subtitles were not burned in (only $changed pixels differ)")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun PlaybackState.isInCue(): Boolean =
        positionMillis in CUE_FROM_MILLIS..CUE_UNTIL_MILLIS && subtitleTracks.any { it.isSelected }

    private fun bottomFifthDifference(a: File, b: File): Int {
        val first = ImageIO.read(a)
        val second = ImageIO.read(b)
        if (first.width != second.width || first.height != second.height) return Int.MAX_VALUE
        var different = 0
        for (y in first.height * 4 / 5 until first.height) {
            for (x in 0 until first.width) if (first.getRGB(x, y) != second.getRGB(x, y)) different++
        }
        return different
    }
}
