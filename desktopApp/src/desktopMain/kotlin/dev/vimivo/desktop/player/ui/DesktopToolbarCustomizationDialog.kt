package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class DesktopToolbarConfig(
    val showAudioOnly: Boolean = true,
    val showPip: Boolean = true,
    val showMiniPlayer: Boolean = true,
    val showRecord: Boolean = true,
    val showSettings: Boolean = true,
    val showFullscreen: Boolean = true,
)

@Composable
internal fun DesktopToolbarCustomizationDialog(
    currentConfig: DesktopToolbarConfig,
    onSave: (DesktopToolbarConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var config by remember { mutableStateOf(currentConfig) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Customize Player Controls", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose which quick action buttons appear on the bottom control bar.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = config.showAudioOnly, onCheckedChange = { config = config.copy(showAudioOnly = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Audio-only mode (Save GPU / battery)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = config.showPip, onCheckedChange = { config = config.copy(showPip = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Picture-in-Picture window (PiP)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = config.showMiniPlayer, onCheckedChange = { config = config.copy(showMiniPlayer = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Compact Mini Player", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = config.showRecord, onCheckedChange = { config = config.copy(showRecord = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Record / Dump stream button", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = config.showSettings, onCheckedChange = { config = config.copy(showSettings = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Settings panel button", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = config.showFullscreen, onCheckedChange = { config = config.copy(showFullscreen = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Fullscreen toggle button", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(config)
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
