@file:Suppress("LongParameterList") // Composables take their state, callbacks and styling explicitly.

package dev.vimivo.desktop.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.DesktopArtwork
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.player.ui.PlayerIconButton
import dev.vimivo.desktop.player.ui.PlayerPalette
import dev.vimivo.desktop.player.ui.PlayerSidePanel
import dev.vimivo.desktop.player.ui.PlayerVolumeControl
import dev.vimivo.desktop.player.ui.VimivoTimeline
import dev.vimivo.desktop.player.ui.playerClock
import dev.vimivo.desktop.player.ui.rememberPointerAwake
import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.player_controls
import dev.vimivo.desktop.resources.player_exit_fullscreen
import dev.vimivo.desktop.resources.player_fullscreen
import dev.vimivo.desktop.resources.player_next
import dev.vimivo.desktop.resources.player_panel
import dev.vimivo.desktop.resources.player_pause
import dev.vimivo.desktop.resources.player_play
import dev.vimivo.desktop.resources.player_playlist
import dev.vimivo.desktop.resources.player_previous
import dev.vimivo.desktop.resources.player_seek
import dev.vimivo.desktop.resources.player_stop
import dev.vimivo.desktop.resources.player_time_toggle
import dev.vimivo.desktop.resources.player_unknown_time
import dev.vimivo.desktop.resources.player_volume
import dev.vimivo.playback.PlaybackStatus
import org.jetbrains.compose.resources.stringResource
import java.awt.Component

/**
 * The player window: only the player, never the library. Video fills the stage on black; audio shows its own
 * layout. The video surface is always composed (collapsed to 1 dp when hidden) because libmpv renders into it.
 * In fullscreen the controls move to the floating controller ([FullscreenController]); nothing can draw over the
 * native video in this window.
 */
@Composable
internal fun PlayerWindowContent(
    state: PlayerState,
    /** Hosts the native video surface; always composed, collapsed to 1 dp when no video shows. */
    videoSurface: @Composable (Modifier) -> Unit,
    engine: MpvPlayerEngine,
    commands: DesktopPlayerCommandExecutor,
    onIntent: (PlayerIntent) -> Unit,
    /** Timeline hover preview for the current media, when it has pictures to show. */
    preview: (suspend (Long) -> ImageBitmap?)? = null,
    /** The native video component: mouse movement over it brings auto-hidden controls back. */
    videoComponent: Component? = null,
) {
    val media = state.playback.media
    val showsVideo = state.presentation == PlayerPresentation.VIDEO && state.loadingTitle == null
    val barInteraction = remember { MutableInteractionSource() }
    val barHovered by barInteraction.collectIsHoveredAsState()
    val paused = state.playback.status != PlaybackStatus.Playing
    // Optional (View ▸ Auto-hide Controls, off by default): windowed video hides the bar while the pointer rests.
    val controlsAwake = videoComponent?.takeIf { state.autoHidesControls(showsVideo) }
        ?.let { rememberPointerAwake(it, hold = barHovered || paused) } ?: true
    val panel = state.panel?.takeUnless { state.fullscreen }
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
                if (panel != null) PlayerSidePanel(state, panel, engine, commands, onIntent)
            }
            PlayerMessages(state, onIntent)
            if (state.showsControlBar() && controlsAwake) {
                PlayerControlBar(state, onIntent, preview, Modifier.hoverable(barInteraction))
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

/**
 * The windowed control bar (spec §2.1): 48 dp, #141416 at 92 % with a 6 % hairline on top, one row:
 * ⏮ ⏯ ⏭ ■ ≡ · timeline · time · volume · ⚙ · ⛶.
 */
@Composable
private fun PlayerControlBar(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    preview: (suspend (Long) -> ImageBitmap?)?,
    modifier: Modifier = Modifier,
) {
    val playback = state.playback
    val playing = playback.status == PlaybackStatus.Playing
    val controlsLabel = stringResource(Res.string.player_controls)
    Row(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .background(PlayerPalette.bar)
            .drawBehind { drawLine(PlayerPalette.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = controlsLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PlayerIconButton(
            Icons.Outlined.SkipPrevious,
            stringResource(Res.string.player_previous)
        ) { onIntent(PlayerIntent.Previous) }
        PlayerIconButton(
            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            stringResource(if (playing) Res.string.player_pause else Res.string.player_play),
        ) { onIntent(PlayerIntent.TogglePlayback) }
        PlayerIconButton(
            Icons.Outlined.SkipNext,
            stringResource(Res.string.player_next)
        ) { onIntent(PlayerIntent.Next) }
        PlayerIconButton(Icons.Outlined.Stop, stringResource(Res.string.player_stop)) { onIntent(PlayerIntent.Stop) }
        PlayerIconButton(Icons.AutoMirrored.Outlined.QueueMusic, stringResource(Res.string.player_playlist)) {
            onIntent(PlayerIntent.ShowLibrary)
        }
        Spacer(Modifier.width(8.dp))
        VimivoTimeline(
            playback = playback,
            label = stringResource(Res.string.player_seek),
            onSeek = { position, exact -> onIntent(PlayerIntent.Seek(position, exact)) },
            onAbChange = { onIntent(PlayerIntent.SetAbRepeat(it)) },
            modifier = Modifier.weight(1f),
            preview = preview,
        )
        Spacer(Modifier.width(8.dp))
        TimeReadout(state, onIntent)
        Spacer(Modifier.width(8.dp))
        PlayerVolumeControl(
            volume = playback.volume,
            maxVolume = state.maxVolume,
            muteIcon = Icons.AutoMirrored.Outlined.VolumeOff,
            volumeIcon = Icons.AutoMirrored.Outlined.VolumeUp,
            label = stringResource(Res.string.player_volume),
            onVolume = { onIntent(PlayerIntent.SetVolume(it)) },
            onToggleMute = { onIntent(PlayerIntent.Command(DesktopPlayerCommand.TOGGLE_MUTE)) },
        )
        PlayerIconButton(
            Icons.Outlined.Settings,
            stringResource(Res.string.player_panel),
            selected = state.panel != null
        ) {
            onIntent(PlayerIntent.ShowPanel(if (state.panel == null) PlayerPanel.VIDEO else null))
        }
        PlayerIconButton(
            if (state.fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
            stringResource(if (state.fullscreen) Res.string.player_exit_fullscreen else Res.string.player_fullscreen),
        ) { onIntent(PlayerIntent.SetFullscreen(!state.fullscreen)) }
    }
}

/** "00:24 / 05:12" in tabular figures; a click switches to "-04:48 / 05:12" (remembered). */
@Composable
internal fun TimeReadout(state: PlayerState, onIntent: (PlayerIntent) -> Unit, modifier: Modifier = Modifier) {
    val playback = state.playback
    val duration = playback.durationMillis?.takeIf { it > 0 }
    val unknown = stringResource(Res.string.player_unknown_time)
    val shown = if (state.showRemainingTime && duration != null) {
        "-" + playerClock(duration - playback.positionMillis)
    } else {
        playerClock(playback.positionMillis)
    }
    val label = stringResource(Res.string.player_time_toggle)
    Text(
        "$shown / ${duration?.let(::playerClock) ?: unknown}",
        color = PlayerPalette.content,
        style = TIME_TEXT,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clickable { onIntent(PlayerIntent.ToggleRemainingTime) }
            .semantics { contentDescription = label }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

private val BAR_HEIGHT = 48.dp
private val TIME_TEXT = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
