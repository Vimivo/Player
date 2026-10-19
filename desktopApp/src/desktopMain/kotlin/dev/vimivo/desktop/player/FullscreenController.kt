package dev.vimivo.desktop.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.player.ui.PlayerIconButton
import dev.vimivo.desktop.player.ui.PlayerIconFace
import dev.vimivo.desktop.player.ui.PlayerPalette
import dev.vimivo.desktop.player.ui.PlayerVolumeControl
import dev.vimivo.desktop.player.ui.VimivoTimeline
import dev.vimivo.desktop.player.ui.playerClock
import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.player_back_10
import dev.vimivo.desktop.resources.player_exit_fullscreen
import dev.vimivo.desktop.resources.player_forward_10
import dev.vimivo.desktop.resources.player_next
import dev.vimivo.desktop.resources.player_panel
import dev.vimivo.desktop.resources.player_pause
import dev.vimivo.desktop.resources.player_play
import dev.vimivo.desktop.resources.player_previous
import dev.vimivo.desktop.resources.player_seek
import dev.vimivo.desktop.resources.player_unknown_time
import dev.vimivo.desktop.resources.player_volume
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

/**
 * The fullscreen floating controller (spec §2.2), in its own transparent window owned by the player window: nothing
 * can be drawn over the native video inside the player window, and an owned window stays in the player's fullscreen
 * space on macOS. It never takes keyboard focus, so shortcuts keep working.
 *
 * It hides (with the cursor) after [IDLE_MILLIS] without mouse movement over the video; it stays while hovered or
 * paused. Dragging its background moves it; the position is remembered ([PlayerIntent.MoveController]).
 */
@Composable
internal fun FullscreenController(
    state: PlayerState,
    owner: Window,
    videoSurface: Component,
    onIntent: (PlayerIntent) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var lastActivity by remember { mutableLongStateOf(System.nanoTime()) }
    var awake by remember { mutableStateOf(true) }
    val paused = state.playback.status != PlaybackStatus.Playing
    DisposableEffect(videoSurface) {
        val listener = object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                lastActivity = System.nanoTime()
                awake = true
            }
        }
        videoSurface.addMouseMotionListener(listener)
        onDispose {
            videoSurface.removeMouseMotionListener(listener)
            videoSurface.cursor = Cursor.getDefaultCursor()
        }
    }
    LaunchedEffect(lastActivity, hovered, paused) {
        awake = true
        if (hovered || paused) return@LaunchedEffect
        delay(IDLE_MILLIS)
        awake = false
    }
    LaunchedEffect(awake) { videoSurface.cursor = if (awake) Cursor.getDefaultCursor() else BLANK_CURSOR }
    val alpha by animateFloatAsState(if (awake) 1f else 0f, tween(FADE_MILLIS), label = "controller fade")

    val screen = owner.graphicsConfiguration.bounds
    val width = (CONTROLLER_WIDTH.value.coerceAtMost(screen.width / 2f)).coerceAtLeast(MIN_WIDTH.value).dp
    // Follows the pointer while dragging; saved once when the drag ends.
    var liveOffset by remember(state.controllerOffset) { mutableStateOf(state.controllerOffset) }
    val dialogState = rememberDialogState(size = DpSize(width + SHADOW * 2, CONTROLLER_HEIGHT + SHADOW * 2))
    LaunchedEffect(liveOffset, width) {
        val bounds = owner.bounds
        dialogState.position = WindowPosition(
            (bounds.x + (bounds.width - width.value) / 2 - SHADOW.value + liveOffset.x).dp,
            (bounds.y + bounds.height - CONTROLLER_HEIGHT.value - BOTTOM_MARGIN.value - SHADOW.value + liveOffset.y).dp,
        )
    }
    DialogWindow(
        onCloseRequest = {},
        state = dialogState,
        visible = alpha > 0f,
        title = "Vimivo controls",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(SHADOW)
                .alpha(alpha)
                .shadow(16.dp, SHAPE)
                .clip(SHAPE)
                .background(PlayerPalette.glass)
                .border(1.dp, PlayerPalette.glassBorder, SHAPE)
                .hoverable(interaction)
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onIntent(PlayerIntent.MoveController(liveOffset)) }) { change, amount ->
                        change.consume()
                        liveOffset = ControllerOffset(liveOffset.x + amount.x.toInt(), liveOffset.y + amount.y.toInt())
                    }
                },
        ) {
            ControllerContent(state, onIntent)
        }
    }
}

@Composable
private fun ControllerContent(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    val playback = state.playback
    val playing = playback.status == PlaybackStatus.Playing
    val duration = playback.durationMillis?.takeIf { it > 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(playerClock(playback.positionMillis), color = PlayerPalette.content, style = SMALL_TEXT, maxLines = 1)
            Text(
                playback.media?.fileName ?: playback.media?.title.orEmpty(),
                color = PlayerPalette.content,
                style = SMALL_TEXT,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(duration?.let(::playerClock) ?: stringResource(Res.string.player_unknown_time), color = PlayerPalette.content, style = SMALL_TEXT, maxLines = 1)
        }
        VimivoTimeline(
            playback = playback,
            label = stringResource(Res.string.player_seek),
            onSeek = { position, exact -> onIntent(PlayerIntent.Seek(position, exact)) },
            alwaysShowThumb = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                PlayerVolumeControl(
                    volume = playback.volume,
                    maxVolume = state.maxVolume,
                    muteIcon = Icons.AutoMirrored.Outlined.VolumeOff,
                    volumeIcon = Icons.AutoMirrored.Outlined.VolumeUp,
                    label = stringResource(Res.string.player_volume),
                    onVolume = { onIntent(PlayerIntent.SetVolume(it)) },
                    onToggleMute = { onIntent(PlayerIntent.Command(DesktopPlayerCommand.TOGGLE_MUTE)) },
                    sliderWidth = 72.dp,
                )
            }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerIconButton(Icons.Outlined.SkipPrevious, stringResource(Res.string.player_previous)) { onIntent(PlayerIntent.Previous) }
                ScanButton(Icons.Outlined.FastRewind, stringResource(Res.string.player_back_10), forward = false, onIntent = onIntent)
                PlayerIconButton(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    stringResource(if (playing) Res.string.player_pause else Res.string.player_play),
                    size = 44.dp,
                    iconSize = 30.dp,
                ) { onIntent(PlayerIntent.TogglePlayback) }
                ScanButton(Icons.Outlined.FastForward, stringResource(Res.string.player_forward_10), forward = true, onIntent = onIntent)
                PlayerIconButton(Icons.Outlined.SkipNext, stringResource(Res.string.player_next)) { onIntent(PlayerIntent.Next) }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(Icons.Outlined.Settings, stringResource(Res.string.player_panel), selected = state.panel != null) {
                    onIntent(PlayerIntent.ShowPanel(if (state.panel == null) PlayerPanel.VIDEO else null))
                }
                Spacer(Modifier.width(2.dp))
                PlayerIconButton(Icons.Outlined.CloseFullscreen, stringResource(Res.string.player_exit_fullscreen)) {
                    onIntent(PlayerIntent.SetFullscreen(false))
                }
            }
        }
    }
}

/** The settings panel in fullscreen: its own owned window along the right edge (it cannot share the video window). */
@Composable
internal fun FullscreenPanelWindow(
    state: PlayerState,
    panel: PlayerPanel,
    owner: Window,
    engine: dev.vimivo.desktop.playback.MpvPlayerEngine,
    commands: dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor,
    onIntent: (PlayerIntent) -> Unit,
) {
    val bounds = owner.bounds
    val height = (bounds.height - PANEL_MARGIN.value * 2).dp
    val dialogState = rememberDialogState(size = DpSize(PANEL_WIDTH, height))
    LaunchedEffect(bounds) {
        dialogState.position = WindowPosition(
            (bounds.x + bounds.width - PANEL_WIDTH.value - PANEL_MARGIN.value).dp,
            (bounds.y + PANEL_MARGIN.value).dp,
        )
    }
    DialogWindow(
        onCloseRequest = { onIntent(PlayerIntent.ShowPanel(null)) },
        state = dialogState,
        title = "Vimivo settings",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = true,
    ) {
        dev.vimivo.desktop.player.ui.PlayerSidePanel(state, panel, engine, commands, onIntent, Modifier.clip(RoundedCornerShape(12.dp)))
    }
}

/** ⏪ / ⏩: a click jumps 10 s; holding scans in 5 s keyframe steps until release. */
@Composable
private fun ScanButton(icon: ImageVector, label: String, forward: Boolean, onIntent: (PlayerIntent) -> Unit) {
    val scope = rememberCoroutineScope()
    val step = if (forward) DesktopPlayerCommand.SEEK_FORWARD_SHORT else DesktopPlayerCommand.SEEK_BACK_SHORT
    val jump = if (forward) DesktopPlayerCommand.SEEK_FORWARD_MEDIUM else DesktopPlayerCommand.SEEK_BACK_MEDIUM
    PlayerIconFace(
        icon,
        label,
        Modifier.pointerInput(forward) {
            awaitEachGesture {
                awaitFirstDown()
                var scanned = false
                val scan = scope.launch {
                    delay(HOLD_MILLIS)
                    while (true) {
                        scanned = true
                        onIntent(PlayerIntent.Command(step))
                        delay(SCAN_INTERVAL_MILLIS)
                    }
                }
                waitForUpOrCancellation()
                scan.cancel()
                if (!scanned) onIntent(PlayerIntent.Command(jump))
            }
        },
    )
}

private val BLANK_CURSOR: Cursor = Toolkit.getDefaultToolkit().createCustomCursor(
    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
    Point(0, 0),
    "vimivo-hidden",
)
private val SHAPE = RoundedCornerShape(14.dp)
private val SMALL_TEXT = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
private val CONTROLLER_WIDTH = 720.dp
private val MIN_WIDTH = 480.dp
private val CONTROLLER_HEIGHT = 112.dp
private val BOTTOM_MARGIN = 24.dp
private val SHADOW: Dp = 16.dp
private val PANEL_WIDTH = 380.dp
private val PANEL_MARGIN = 24.dp
private const val IDLE_MILLIS = 2_000L
private const val FADE_MILLIS = 180
private const val HOLD_MILLIS = 350L
private const val SCAN_INTERVAL_MILLIS = 150L
