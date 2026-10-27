package dev.vimivo.desktop.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vimivo.playback.PlaybackSleepTimer

@Composable
internal fun DesktopSleepTimerDialog(
    sleepTimer: PlaybackSleepTimer,
    onDismiss: () -> Unit,
) {
    val state by sleepTimer.state.collectAsState()
    var customMinutes by remember { mutableIntStateOf(30) }

    val presets = listOf(15, 30, 45, 60, 90, 120)

    val isActive = state.deadlineEpochMillis != null || state.stopAfterCurrent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isActive) {
                    val remainingSeconds = ((state.remainingMillis ?: 0L) / 1000).coerceAtLeast(0L)
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (state.stopAfterCurrent) "Stopping After Current Track" else "Timer Active",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            if (!state.stopAfterCurrent) {
                                Text(
                                    "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!state.stopAfterCurrent) {
                                    OutlinedButton(onClick = {
                                        val newMinutes = (minutes + 10).toInt().coerceIn(1, 1440)
                                        sleepTimer.start(newMinutes)
                                    }) {
                                        Text("+10 Min")
                                    }
                                }
                                Button(onClick = { sleepTimer.cancel() }) {
                                    Text("Turn Off")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Automatically pause playback and put player to sleep after selected duration.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Preset Durations", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.take(3).forEach { mins ->
                            FilterChip(
                                selected = customMinutes == mins,
                                onClick = {
                                    customMinutes = mins
                                    sleepTimer.start(mins)
                                },
                                label = { Text("${mins}m") },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.drop(3).forEach { mins ->
                            FilterChip(
                                selected = customMinutes == mins,
                                onClick = {
                                    customMinutes = mins
                                    sleepTimer.start(mins)
                                },
                                label = { Text("${mins}m") },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { sleepTimer.afterCurrent() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Timer, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop at End of Current Track")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
