package dev.vimivo.desktop.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.vimivo.desktop.platform.MacWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/** The OS window that hosts the current composition; overlays opened from it are owned by it. */
internal val LocalHostWindow = staticCompositionLocalOf<Window?> { null }

/**
 * Something drawn over the video: native video is a layer above Compose inside the player window, so overlays
 * (fullscreen controller, its settings panel, timeline previews) are their own transparent, undecorated windows,
 * owned by [owner] and on macOS attached as real child windows so they follow it into fullscreen.
 *
 * [position] is in screen coordinates (AWT points = dp).
 */
@Composable
internal fun OverlayWindow(
    owner: Window,
    position: DpOffset,
    size: DpSize,
    title: String,
    visible: Boolean = true,
    focusable: Boolean = false,
    onCloseRequest: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val state = rememberDialogState(size = size)
    LaunchedEffect(position) { state.position = WindowPosition(position.x, position.y) }
    LaunchedEffect(size) { state.size = size }
    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = focusable,
    ) {
        LaunchedEffect(window, visible) {
            if (visible) withContext(Dispatchers.IO) { MacWindows.attachChild(owner, window) }
        }
        CompositionLocalProvider(LocalHostWindow provides window, content = content)
    }
}

/** [window]'s bounds on screen, updated as it moves and resizes (fullscreen transitions included). */
@Composable
internal fun rememberWindowBounds(window: Window): State<Rectangle> {
    val bounds = remember(window) { mutableStateOf(window.bounds) }
    DisposableEffect(window) {
        val listener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                bounds.value = window.bounds
            }

            override fun componentMoved(event: ComponentEvent) {
                bounds.value = window.bounds
            }
        }
        window.addComponentListener(listener)
        bounds.value = window.bounds
        onDispose { window.removeComponentListener(listener) }
    }
    return bounds
}
