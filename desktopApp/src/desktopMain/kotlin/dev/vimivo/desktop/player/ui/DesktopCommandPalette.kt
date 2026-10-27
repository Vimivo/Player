package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerPanel
import dev.vimivo.desktop.player.PlayerState

internal data class PaletteAction(
    val title: String,
    val category: String,
    val shortcut: String? = null,
    val icon: ImageVector,
    val action: () -> Unit,
)

@Composable
internal fun DesktopCommandPalette(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    onDismiss: () -> Unit,
    onOpenStream: () -> Unit,
    onOpenDisc: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }

    val allActions = remember(state) {
        listOf(
            // Playback
            PaletteAction("Play / Pause", "Playback", "Space", Icons.Outlined.PlayArrow) {
                onIntent(PlayerIntent.TogglePlayback)
            },
            PaletteAction("Jump to Time", "Playback", "⌘T", Icons.Outlined.Timer) {
                onIntent(PlayerIntent.ToggleJumpToTime)
            },
            PaletteAction("Seek Forward 10s", "Playback", "→", Icons.Outlined.FastForward) {
                onIntent(PlayerIntent.Seek(state.playback.positionMillis + 10_000L))
            },
            PaletteAction("Seek Backward 10s", "Playback", "←", Icons.Outlined.FastRewind) {
                onIntent(PlayerIntent.Seek((state.playback.positionMillis - 10_000L).coerceAtLeast(0L)))
            },
            PaletteAction("Speed 1.0x (Normal)", "Playback", "1", Icons.Outlined.PlayArrow) {
                onIntent(PlayerIntent.SetSpeed(1.0f))
            },
            PaletteAction("Speed 1.5x", "Playback", null, Icons.Outlined.FastForward) {
                onIntent(PlayerIntent.SetSpeed(1.5f))
            },
            PaletteAction("Speed 2.0x", "Playback", "2", Icons.Outlined.FastForward) {
                onIntent(PlayerIntent.SetSpeed(2.0f))
            },

            // Window & View
            PaletteAction("Toggle Fullscreen", "View", "F", Icons.Outlined.Fullscreen) {
                onIntent(PlayerIntent.SetFullscreen(!state.fullscreen))
            },
            PaletteAction("Toggle Always On Top", "View", "T", Icons.AutoMirrored.Outlined.OpenInNew) {
                onIntent(PlayerIntent.ToggleAlwaysOnTop)
            },
            PaletteAction("Mini Player", "View", "M", Icons.Outlined.Headphones) {
                onIntent(PlayerIntent.ToggleMiniPlayer)
            },
            PaletteAction("Picture-in-Picture", "View", "P", Icons.Outlined.PictureInPicture) {
                onIntent(PlayerIntent.TogglePipMode)
            },
            PaletteAction("Audio-Only Mode (Hide Video)", "View", null, Icons.Outlined.Audiotrack) {
                onIntent(PlayerIntent.ToggleAudioOnlyMode)
            },

            // Panels
            PaletteAction("Audio Equalizer & DSP Effects", "Panels", null, Icons.Outlined.Equalizer) {
                onIntent(PlayerIntent.ShowPanel(PlayerPanel.AUDIO))
            },
            PaletteAction("Video Adjustments & Filters", "Panels", null, Icons.Outlined.Videocam) {
                onIntent(PlayerIntent.ShowPanel(PlayerPanel.VIDEO))
            },
            PaletteAction("Subtitles & Styling", "Panels", null, Icons.Outlined.Subtitles) {
                onIntent(PlayerIntent.ShowPanel(PlayerPanel.SUBTITLES))
            },
            PaletteAction("Audio & Subtitle Sync", "Panels", null, Icons.Outlined.Settings) {
                onIntent(PlayerIntent.ShowPanel(PlayerPanel.SYNC))
            },
            PaletteAction("Bookmarks Panel", "Panels", "B", Icons.Outlined.Bookmark) {
                onIntent(PlayerIntent.ShowPanel(PlayerPanel.BOOKMARKS))
            },

            // Tools & Network
            PaletteAction("Open Network Stream", "Tools", "Cmd+N", Icons.Outlined.Link) {
                onOpenStream()
            },
            PaletteAction("Open Optical Disc / ISO", "Tools", "Cmd+D", Icons.Outlined.Dns) {
                onOpenDisc()
            },
            PaletteAction("Wi-Fi File Sharing", "Tools", null, Icons.Outlined.Share) {
                onIntent(PlayerIntent.ToggleWifiSharing)
            },
            PaletteAction("Convert / Transcode Media", "Tools", null, Icons.Outlined.Transform) {
                onIntent(PlayerIntent.ToggleTranscodeDialog)
            },
            PaletteAction("Search Subtitles Online", "Subtitles", null, Icons.Outlined.Subtitles) {
                onIntent(PlayerIntent.ToggleSubtitleSearch)
            },
            PaletteAction("Internet Radio & Podcasts", "Tools", null, Icons.Outlined.Radio) {
                onIntent(PlayerIntent.ToggleRadioPodcast)
            },
            PaletteAction("Web Remote Control", "Tools", null, Icons.Outlined.PhoneIphone) {
                onIntent(PlayerIntent.ToggleWebRemote)
            },
            PaletteAction("User Scripts Folder", "Tools", null, Icons.Outlined.Code) {
                onIntent(PlayerIntent.ToggleScripts)
            },
            PaletteAction(
                title = if (state.isRecording) "Stop Recording Stream" else "Record Stream to File",
                category = "Tools",
                shortcut = null,
                icon = Icons.Outlined.FiberManualRecord,
            ) {
                onIntent(PlayerIntent.ToggleRecording)
            },
            PaletteAction("Open Capture Device (Webcam / Screen)", "Tools", null, Icons.Outlined.CameraAlt) {
                onIntent(PlayerIntent.ToggleCaptureDevice)
            },
            PaletteAction("Connect to Network Share (SMB / SFTP / FTP)", "Tools", null, Icons.Outlined.Dns) {
                onIntent(PlayerIntent.ToggleNetworkShare)
            },
            PaletteAction("Cast to Screen / DLNA", "Tools", null, Icons.Outlined.Cast) {
                onIntent(PlayerIntent.ToggleCasting)
            },
            PaletteAction("Customize Control Bar", "View", null, Icons.Outlined.DashboardCustomize) {
                onIntent(PlayerIntent.ToggleToolbarCustomize)
            },
            PaletteAction("S/PDIF / HDMI Bitstream Passthrough", "Audio", null, Icons.Outlined.Equalizer) {
                onIntent(PlayerIntent.ToggleAudioPassthrough)
            },
            PaletteAction("Sleep Timer", "Playback", null, Icons.Outlined.Bedtime) {
                onIntent(PlayerIntent.ToggleSleepTimer)
            },
            PaletteAction("Stats for Nerds (Live Playback HUD)", "View", "Shift+I", Icons.Outlined.Analytics) {
                onIntent(PlayerIntent.ToggleStatsHud)
            },
            PaletteAction("Dialogue & Speech Clarity Boost", "Audio", null, Icons.Outlined.RecordVoiceOver) {
                onIntent(PlayerIntent.ToggleDialogueClarity)
            },
            PaletteAction("Skip Intro (+85s)", "Playback", null, Icons.Outlined.FastForward) {
                onIntent(PlayerIntent.SkipIntro)
            },
            PaletteAction("Show Media Library", "Navigation", "L", Icons.AutoMirrored.Outlined.QueueMusic) {
                onIntent(PlayerIntent.ShowLibrary)
            },
        )
    }

    val filtered = remember(query, allActions) {
        if (query.isBlank()) allActions
        else {
            val q = query.trim().lowercase()
            allActions.filter { it.title.lowercase().contains(q) || it.category.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(560.dp),
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search actions or commands… (Cmd+K)") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { "${it.category}-${it.title}" }) { item ->
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                item.action()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    item.category,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.shortcut != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        item.shortcut,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}
