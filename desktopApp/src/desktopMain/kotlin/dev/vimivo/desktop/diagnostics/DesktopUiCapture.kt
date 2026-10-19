package dev.vimivo.desktop.diagnostics

import dev.vimivo.desktop.platform.MacWindows
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerPanel
import dev.vimivo.desktop.player.PlayerState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * `--smoke-test --ui-capture <dir> <video>`: drives the real player through its surfaces and saves what the screen
 * shows (the app's own windows, so macOS needs no Screen Recording permission). Used for the UI review evidence:
 * windowed controls, every side-panel tab, a narrow panel, the timeline hover, fullscreen with the floating
 * controller, and the controller hidden after idling.
 */
internal object DesktopUiCapture {
    suspend fun run(
        directory: File,
        video: File,
        state: StateFlow<PlayerState>,
        dispatch: (PlayerIntent) -> Unit,
        playerWindow: () -> Window?,
    ): Boolean {
        directory.mkdirs()
        dispatch(PlayerIntent.OpenFiles(listOf(video)))
        val playing = withTimeoutOrNull(START_TIMEOUT_MILLIS) {
            state.first { it.playback.status == PlaybackStatus.Playing && it.playback.positionMillis > MIN_POSITION }
        }
        if (playing == null) return false
        val window = playerWindow() ?: return false
        settle()
        capture(window, directory, "01-windowed")
        hoverTimeline(window, directory)
        PlayerPanel.entries.forEachIndexed { index, panel ->
            dispatch(PlayerIntent.ShowPanel(panel))
            settle()
            capture(window, directory, "%02d-panel-%s".format(index + 3, panel.name.lowercase()))
        }
        dispatch(PlayerIntent.ShowPanel(PlayerPanel.SYNC))
        onEdt { window.setSize(NARROW_WIDTH, NARROW_HEIGHT) }
        settle()
        capture(window, directory, "08-panel-narrow")
        dispatch(PlayerIntent.ShowPanel(null))
        onEdt { window.setSize(WINDOW_WIDTH, WINDOW_HEIGHT) }
        dispatch(PlayerIntent.SetFullscreen(true))
        delay(FULLSCREEN_SETTLE_MILLIS)
        captureScreen(window, directory, "09-fullscreen-controller")
        delay(IDLE_HIDE_MILLIS)
        captureScreen(window, directory, "10-fullscreen-idle")
        dispatch(PlayerIntent.SetFullscreen(false))
        delay(FULLSCREEN_SETTLE_MILLIS)
        return true
    }

    /** Moves the pointer over the middle of the timeline so the hover tooltip shows, then puts it back. */
    private suspend fun hoverTimeline(window: Window, directory: File) {
        // macOS delivers mouse-moved events only to the active app.
        MacWindows.activate()
        onEdt { window.toFront() }
        delay(HOVER_SETTLE_MILLIS)
        val robot = Robot()
        val original = MouseInfo.getPointerInfo()?.location
        val bounds = onEdt { window.bounds }
        val y = bounds.y + bounds.height - TIMELINE_FROM_BOTTOM
        robot.mouseMove(bounds.x + bounds.width / 2 - HOVER_NUDGE, y)
        delay(HOVER_STEP_MILLIS)
        robot.mouseMove(bounds.x + bounds.width / 2, y)
        delay(HOVER_SETTLE_MILLIS)
        capture(window, directory, "02-timeline-hover")
        original?.let { robot.mouseMove(it.x, it.y) }
    }

    private suspend fun capture(window: Window, directory: File, name: String) {
        val bounds = onEdt { window.bounds }
        save(bounds, directory, name)
    }

    /** Fullscreen: the whole screen, which includes the controller's own window. */
    private suspend fun captureScreen(window: Window, directory: File, name: String) {
        val bounds = onEdt { window.graphicsConfiguration.bounds }
        save(bounds, directory, name)
    }

    private suspend fun save(bounds: Rectangle, directory: File, name: String) = withContext(Dispatchers.IO) {
        ImageIO.write(Robot().createScreenCapture(bounds), "png", File(directory, "$name.png"))
    }

    private suspend fun settle() = delay(SETTLE_MILLIS)

    private suspend fun <T> onEdt(block: () -> T): T = withContext(Dispatchers.IO) {
        var result: T? = null
        SwingUtilities.invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        result as T
    }

    private const val START_TIMEOUT_MILLIS = 20_000L
    private const val MIN_POSITION = 1_000L
    private const val SETTLE_MILLIS = 900L
    private const val HOVER_SETTLE_MILLIS = 700L
    private const val HOVER_STEP_MILLIS = 120L
    private const val HOVER_NUDGE = 40
    private const val FULLSCREEN_SETTLE_MILLIS = 2_000L
    private const val IDLE_HIDE_MILLIS = 3_000L
    private const val TIMELINE_FROM_BOTTOM = 24
    private const val NARROW_WIDTH = 760
    private const val NARROW_HEIGHT = 560
    private const val WINDOW_WIDTH = 960
    private const val WINDOW_HEIGHT = 600
}
