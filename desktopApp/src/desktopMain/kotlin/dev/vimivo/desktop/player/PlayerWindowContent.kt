package dev.vimivo.desktop.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.DesktopArtwork
import dev.vimivo.desktop.DesktopBookmarksPanel
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.DesktopVideoControlsPanel
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.player_back_10
import dev.vimivo.desktop.resources.player_close_panel
import dev.vimivo.desktop.resources.player_exit_fullscreen
import dev.vimivo.desktop.resources.player_forward_10
import dev.vimivo.desktop.resources.player_fullscreen
import dev.vimivo.desktop.resources.player_next
import dev.vimivo.desktop.resources.player_open_library
import dev.vimivo.desktop.resources.player_panel
import dev.vimivo.desktop.resources.player_pause
import dev.vimivo.desktop.resources.player_play
import dev.vimivo.desktop.resources.player_previous
import dev.vimivo.desktop.resources.player_seek
import dev.vimivo.desktop.resources.player_speed
import dev.vimivo.desktop.resources.player_tab_bookmarks
import dev.vimivo.desktop.resources.player_tab_video
import dev.vimivo.desktop.resources.player_unknown_time
import dev.vimivo.desktop.resources.player_volume
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackStatus
import org.jetbrains.compose.resources.stringResource

/**
 * The player window: only the player, never the library. Video fills the stage on black; audio shows its own
 * layout. The video surface is always composed (collapsed to 1 dp when hidden) because libmpv renders into it.
 */
@Composable
internal fun PlayerWindowContent(
    state: PlayerState,
    /** Hosts the native video surface; always composed, collapsed to 1 dp when no video shows. */
    videoSurface: @Composable (Modifier) -> Unit,
    engine: MpvPlayerEngine,
    commands: DesktopPlayerCommandExecutor,
    onIntent: (PlayerIntent) -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    val media = state.playback.media
    val showsVideo = state.presentation == PlayerPresentation.VIDEO && state.loadingTitle == null
    // Black stage with light content: text never falls back to the default dark content colour.
    Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    when {
                        state.loadingTitle != null -> PlayerLoadingCard(state.loadingTitle, Modifier.fillMaxSize())
                        state.presentation == PlayerPresentation.AUDIO && media != null -> AudioStage(media)
                    }
                    videoSurface(
                        if (showsVideo) Modifier.fillMaxSize() else Modifier.size(1.dp).align(Alignment.TopStart),
                    )
                }
                if (panelOpen && !state.fullscreen) {
                    PlayerSidePanel(state, engine, commands, onIntent, onClose = { panelOpen = false })
                }
            }
            PlayerMessages(state, onIntent)
            if (!state.fullscreen || state.presentation == PlayerPresentation.AUDIO) {
                PlayerControlBar(state, onIntent, panelOpen = panelOpen, onTogglePanel = { panelOpen = !panelOpen })
            }
        }
    }
}

@Composable
private fun AudioStage(media: MediaItem) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DesktopArtwork(uri = media.artworkUri, title = media.title, size = 240.dp)
        Text(
            media.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        listOfNotNull(media.artist, media.album).joinToString(" · ").takeIf(String::isNotBlank)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Resume prompt and HUD feedback; laid out above the controls because nothing can draw over native video. */
@Composable
private fun PlayerMessages(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    val prompt = state.resumePrompt
    val hud = state.hud
    if (prompt == null && hud == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        if (prompt != null) {
            ResumeToast(
                prompt = prompt,
                onStartOver = { onIntent(PlayerIntent.StartOver) },
                onDismiss = { onIntent(PlayerIntent.DismissResume) },
            )
        } else if (hud != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(hud.text(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun PlayerControlBar(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
) {
    val playback = state.playback
    val duration = playback.durationMillis?.takeIf { it > 0 }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val seekLabel = stringResource(Res.string.player_seek)
            Slider(
                value = dragPosition ?: playback.positionMillis.toFloat().coerceAtMost(duration?.toFloat() ?: 0f),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    dragPosition?.let { onIntent(PlayerIntent.Seek(it.toLong())) }
                    dragPosition = null
                },
                valueRange = 0f..(duration?.toFloat() ?: 1f),
                enabled = duration != null && playback.isSeekable,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val playing = playback.status == PlaybackStatus.Playing
                ControlButton(Icons.Outlined.SkipPrevious, stringResource(Res.string.player_previous)) {
                    onIntent(PlayerIntent.Previous)
                }
                ControlButton(Icons.Outlined.Replay10, stringResource(Res.string.player_back_10)) {
                    onIntent(PlayerIntent.Command(DesktopPlayerCommand.SEEK_BACK_MEDIUM))
                }
                ControlButton(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    stringResource(if (playing) Res.string.player_pause else Res.string.player_play),
                ) { onIntent(PlayerIntent.TogglePlayback) }
                ControlButton(Icons.Outlined.Forward10, stringResource(Res.string.player_forward_10)) {
                    onIntent(PlayerIntent.Command(DesktopPlayerCommand.SEEK_FORWARD_MEDIUM))
                }
                ControlButton(
                    Icons.Outlined.SkipNext,
                    stringResource(Res.string.player_next)
                ) { onIntent(PlayerIntent.Next) }
                val unknown = stringResource(Res.string.player_unknown_time)
                val elapsed = (dragPosition?.toLong() ?: playback.positionMillis).let(::formatClock)
                Text(
                    "$elapsed / ${duration?.let(::formatClock) ?: unknown}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Box(Modifier.weight(1f))
                VolumeControl(playback.volume, state.maxVolume, onIntent)
                SpeedButton(playback.speed, onIntent)
                ControlButton(
                    Icons.Outlined.Tune,
                    stringResource(Res.string.player_panel),
                    selected = panelOpen,
                    onClick = onTogglePanel
                )
                ControlButton(
                    Icons.AutoMirrored.Outlined.LibraryBooks,
                    stringResource(Res.string.player_open_library)
                ) {
                    onIntent(PlayerIntent.ShowLibrary)
                }
                ControlButton(
                    if (state.fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    stringResource(
                        if (state.fullscreen) Res.string.player_exit_fullscreen else Res.string.player_fullscreen
                    ),
                ) { onIntent(PlayerIntent.SetFullscreen(!state.fullscreen)) }
            }
        }
    }
}

@Composable
private fun VolumeControl(volume: Float, maxVolume: Float, onIntent: (PlayerIntent) -> Unit) {
    val label = stringResource(Res.string.player_volume)
    val muted = volume == 0f
    ControlButton(if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, label) {
        onIntent(PlayerIntent.Command(DesktopPlayerCommand.TOGGLE_MUTE))
    }
    val boosted = volume > 1f
    val tint = if (boosted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Slider(
        value = volume,
        onValueChange = { onIntent(PlayerIntent.SetVolume(it)) },
        valueRange = 0f..maxVolume,
        colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
        modifier = Modifier.width(VOLUME_SLIDER_WIDTH).semantics {
            contentDescription = label
            stateDescription = "${(volume * PERCENT).toInt()}%"
        },
    )
}

@Composable
private fun SpeedButton(speed: Float, onIntent: (PlayerIntent) -> Unit) {
    val presets = PlaybackSpeeds.PRESETS
    val label = stringResource(Res.string.player_speed, formatSpeed(speed))
    TextButton(
        onClick = {
            val index = presets.indexOfFirst { kotlin.math.abs(it - speed) < SPEED_EPSILON }
            onIntent(PlayerIntent.SetSpeed(presets[(index + 1).mod(presets.size)]))
        },
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text("${formatSpeed(speed)}×", maxLines = 1)
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Rectangular side panel (never pill-clipped): video and subtitle controls, and bookmarks. */
@Composable
private fun PlayerSidePanel(
    state: PlayerState,
    engine: MpvPlayerEngine,
    commands: DesktopPlayerCommandExecutor,
    onIntent: (PlayerIntent) -> Unit,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    Surface(
        modifier = Modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.weight(1f)) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(Res.string.player_tab_video), maxLines = 1) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(Res.string.player_tab_bookmarks), maxLines = 1) }
                    )
                }
                ControlButton(Icons.Outlined.Close, stringResource(Res.string.player_close_panel), onClick = onClose)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                if (tab == 0) {
                    DesktopVideoControlsPanel(engine = engine, state = state.playback, commands = commands)
                } else {
                    DesktopBookmarksPanel(
                        media = state.playback.media,
                        positionMillis = state.playback.positionMillis,
                        bookmarks = state.bookmarks,
                        onAdd = { _, _, label -> onIntent(PlayerIntent.AddBookmark(label)) },
                        onSeek = { onIntent(PlayerIntent.Seek(it)) },
                        onRename = { id, label -> onIntent(PlayerIntent.RenameBookmark(id, label)) },
                        onDelete = { onIntent(PlayerIntent.DeleteBookmark(it)) },
                    )
                }
            }
        }
    }
}

private val SIDE_PANEL_WIDTH = 340.dp
private val VOLUME_SLIDER_WIDTH = 110.dp
private const val PERCENT = 100
private const val SPEED_EPSILON = 0.01f
