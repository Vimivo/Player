package dev.vimivo.platform.android.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.ui.components.SettingsRow
import dev.vimivo.playback.SleepTimerOptions

/**
 * The sleep timer picker used by both the audio player and the video player: quick choices, a custom
 * hours-and-minutes time, "after this track", and cancel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SleepTimerContent(viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    val sleepTimer by viewModel.sleepTimerState.collectAsState()
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val custom = SleepTimerOptions.customMinutes(hours, minutes)

    Column(Modifier.padding(bottom = 16.dp)) {
        Text("Sleep timer", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        sleepTimer.remainingMillis?.let {
            Text(
                "Stops in ${formatClock(it)}",
                Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SleepTimerOptions.MINUTES.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.startSleepTimer(preset); onDismiss() },
                    label = { Text(SleepTimerOptions.label(preset)) },
                )
            }
        }
        Text(
            "Custom time",
            Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = hours,
                onValueChange = { hours = it.filter(Char::isDigit).take(2); attempted = false },
                label = { Text("Hours") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(4); attempted = false },
                label = { Text("Minutes") },
                singleLine = true,
                isError = attempted && custom == null,
                supportingText = if (attempted && custom == null) {
                    { Text("Enter 1 minute to 24 hours") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
            Button(
                onClick = {
                    if (custom != null) { viewModel.startSleepTimer(custom); onDismiss() } else attempted = true
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Start") }
        }
        SettingsRow(Icons.Outlined.MusicNote, "Stop after current track", onClick = { viewModel.stopAfterCurrent(); onDismiss() })
        if (sleepTimer.deadlineEpochMillis != null || sleepTimer.stopAfterCurrent) {
            SettingsRow(
                Icons.Outlined.Close, "Cancel sleep timer",
                detail = sleepTimer.remainingMillis?.let(::formatClock) ?: "After current track",
                onClick = { viewModel.cancelSleepTimer(); onDismiss() },
            )
        }
    }
}
