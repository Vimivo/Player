package dev.vimivo.desktop.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.desktop.DesktopArtwork
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerState
import dev.vimivo.playback.PlaybackStatus

@Composable
internal fun DesktopMiniPlayerView(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val media = state.playback.media
    val playing = state.playback.status == PlaybackStatus.Playing

    Surface(
        modifier = modifier.fillMaxWidth().height(110.dp),
        color = Color(0xFF141416),
        contentColor = Color.White,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Seek bar across the top of mini player
            VimivoTimeline(
                playback = state.playback,
                label = "Mini Player Timeline",
                bookmarks = emptyList(),
                onAbChange = {},
                modifier = Modifier.fillMaxWidth().height(16.dp),
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Artwork
                DesktopArtwork(
                    uri = media?.artworkUri,
                    title = media?.title ?: "Vimivo",
                    size = 48.dp,
                )

                Spacer(Modifier.width(10.dp))

                // Title and Artist
                Column(Modifier.weight(1f)) {
                    Text(
                        media?.title ?: "No media playing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        media?.artist ?: "Vimivo Player",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Transport Controls
                IconButton(onClick = { onIntent(PlayerIntent.Previous) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.SkipPrevious, "Previous track", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onIntent(PlayerIntent.TogglePlayback) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = { onIntent(PlayerIntent.Next) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.SkipNext, "Next track", modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(4.dp))

                // Expand back to standard window
                IconButton(
                    onClick = { onIntent(PlayerIntent.ToggleMiniPlayer) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Outlined.OpenInFull, "Restore full player", modifier = Modifier.size(18.dp))
                }

                // Close
                IconButton(
                    onClick = { onIntent(PlayerIntent.ClosePlayer) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Outlined.Close, "Close player", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun DesktopPipOverlay(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = state.playback.status == PlaybackStatus.Playing

    Box(modifier = modifier.fillMaxSize()) {
        // Exit PiP button at top right
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onIntent(PlayerIntent.TogglePipMode) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.OpenInFull,
                        contentDescription = "Exit Picture-in-Picture",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Mini floating controls at bottom
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                VimivoTimeline(
                    playback = state.playback,
                    label = "PiP Timeline",
                    bookmarks = emptyList(),
                    onAbChange = {},
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onIntent(PlayerIntent.Previous) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onIntent(PlayerIntent.TogglePlayback) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { onIntent(PlayerIntent.Next) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
