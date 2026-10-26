package dev.vimivo.desktop.diagnostics

import dev.vimivo.desktop.playback.PresentedFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** The window-change part of the pixel smoke test: fullscreen, resize, and the render-target check. */
internal object DesktopTransitionSmoke {
    /**
     * Replays [file] before each step, runs the step (fullscreen, resize, …) and checks the picture is still on screen
     * afterwards, so a renderer that loses its surface on a window change fails the gate. Then the same changes run as
     * one sequence on a single playback with no replay between them (a replay restarts the frames and can hide a layer
     * that stopped drawing), the last of them with the video paused. After every sequence step the frame the renderer
     * presented must have the pixel size of the surface it draws into: that is the render-target check.
     */
    suspend fun checkTransitions(
        file: File,
        checks: SmokeWindowChecks,
        replay: suspend () -> Unit,
        probePicture: () -> VideoPixelSample?,
        presentedFrame: () -> PresentedFrame? = { null },
    ): List<DesktopSmokeResult> {
        val single = checks.steps.map { (name, step) ->
            replay()
            val before = presentedFrame()
            step()
            DesktopSmokeTest.awaitFrameAfterWindowChange(before, presentedFrame)
            DesktopSmokeTest.checkPicture(passedFor(file, name), probePicture)
        }
        if (checks.sequence.isEmpty()) return single
        replay()
        val sequenced = checks.sequence.map { step ->
            val name = step.name
            step.action()
            val frame = awaitFrameAtSurfaceSize(presentedFrame, checks.surfaceSize, step.reached)
            if (frame is SurfaceFrame.Mismatch) {
                DesktopSmokeResult.Failed(File("${file.name}#$name"), frame.describe())
            } else {
                DesktopSmokeTest.checkPicture(passedFor(file, name), probePicture)
            }
        }
        return single + sequenced
    }

    private fun passedFor(file: File, step: String) =
        DesktopSmokeResult.Passed(File("${file.name}#$step"), positionMillis = 0, videoTracks = 1)

    /** What waiting for a frame at the surface's size ended with. */
    internal sealed interface SurfaceFrame {
        object Matched : SurfaceFrame

        /** No renderer frame count to follow (Windows, where libmpv presents itself): a fixed settle time was used. */
        object Unobserved : SurfaceFrame

        class Mismatch(val surface: Pair<Int, Int>?, val frame: PresentedFrame?) : SurfaceFrame {
            fun describe() =
                "the render target does not match the surface: surface ${surface?.first}x${surface?.second}, " +
                    "last frame ${frame?.width}x${frame?.height}"
        }
    }

    /**
     * Waits until the step's target state is [reached] (the window is fullscreen, or has its restored size) and the
     * renderer has presented a frame at exactly the pixel size of the surface, seen with the surface unchanged. The
     * frame's size is the real size of the framebuffer it was drawn into, so a match means it was drawn after the
     * window got its final size, not at an intermediate size of a fullscreen or resize animation, and it holds for a
     * paused video too (its redraw after the change is the frame). A renderer that does not count frames gets the fixed
     * settle time.
     */
    internal suspend fun awaitFrameAtSurfaceSize(
        presentedFrame: () -> PresentedFrame?,
        surfaceSize: () -> Pair<Int, Int>?,
        reached: () -> Boolean,
    ): SurfaceFrame {
        if (presentedFrame() == null) {
            delay(DesktopSmokeTest.TRANSITION_SETTLE_MILLIS)
            return SurfaceFrame.Unobserved
        }
        val tracker = SurfaceFrameTracker()
        var last: Pair<Pair<Int, Int>?, PresentedFrame?> = null to null
        val settled = withTimeoutOrNull(DesktopSmokeTest.FRAME_WAIT_MILLIS) {
            var done = false
            while (!done) {
                val surface = surfaceSize()
                val frame = presentedFrame()
                last = surface to frame
                done = tracker.isSettled(reached(), surface, frame)
                if (!done) delay(DesktopSmokeTest.FRAME_POLL_MILLIS)
            }
            true
        }
        return if (settled == true) SurfaceFrame.Matched else SurfaceFrame.Mismatch(last.first, last.second)
    }
}

/** One step of the window sequence: what to do, and how to tell the window has got where the step sends it. */
internal class SmokeStep(val name: String, val action: () -> Unit, val reached: () -> Boolean = { true })

/** The window steps of the transition checks, and how to read the surface's pixel size for the render-target check. */
internal class SmokeWindowChecks(
    val steps: List<Pair<String, () -> Unit>> = emptyList(),
    val sequence: List<SmokeStep> = emptyList(),
    val surfaceSize: () -> Pair<Int, Int>? = { null },
)

/**
 * Decides when a window change has produced a real frame: the step's target is reached, the frame's size equals the
 * surface's, and the same was true at the previous look with the surface unchanged. No second frame is required: a
 * paused video is redrawn once after a resize, and that frame is the evidence.
 */
internal class SurfaceFrameTracker {
    private var candidateSurface: Pair<Int, Int>? = null

    fun isSettled(reached: Boolean, surface: Pair<Int, Int>?, frame: PresentedFrame?): Boolean {
        val matches = reached && frame != null && surface != null &&
            frame.width == surface.first && frame.height == surface.second
        val settled = matches && candidateSurface == surface
        candidateSurface = surface.takeIf { matches }
        return settled
    }
}
