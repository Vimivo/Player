@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "LongMethod",
    "LongParameterList", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.PlaybackProfile
import dev.vimivo.playback.ReplayGainMode
import dev.vimivo.settings.ThemeMode

@Composable
internal fun DesktopSettingsPanel(
    state: DesktopLibraryUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    onAddFolder: () -> Unit,
    onRefreshAvailability: () -> Unit,
    onPlaybackProfileSelected: ((PlaybackProfile) -> Unit)? = null,
    onEqualizerPresetSelected: ((EqualizerPreset) -> Unit)? = null,
    onReplayGainModeSelected: ((ReplayGainMode) -> Unit)? = null,
    onSleepTimerSet: ((Int?) -> Unit)? = null,
    onExportDiagnostics: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    onOpenFeedback: (() -> Unit)? = null,
    activeProfile: PlaybackProfile = PlaybackProfile.DEFAULT,
    activePreset: EqualizerPreset = EqualizerPreset.FLAT,
    activeReplayGain: ReplayGainMode = ReplayGainMode.OFF,
    sleepTimerMinutesRemaining: Int? = null,
    diagnosticsStatus: String? = null,
    updateStatus: String? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Theme appearance settings"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose your preferred interface theme. AMOLED provides pure black background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val isSelected = state.themeMode == mode
                            if (isSelected) {
                                Button(
                                    onClick = { onThemeSelected(mode) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "${mode.name.lowercase()} theme, currently active"
                                    },
                                ) {
                                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onThemeSelected(mode) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Switch to ${mode.name.lowercase()} theme"
                                    },
                                ) {
                                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (onPlaybackProfileSelected != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = "Playback profiles"
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Playback Profiles", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Instant configuration presets optimizing speed, equalizer curves, ReplayGain, and aspect ratios.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PlaybackProfile.BUILTIN_PROFILES.forEach { profile ->
                                val isSelected = activeProfile == profile
                                if (isSelected) {
                                    Button(onClick = { onPlaybackProfileSelected(profile) }) {
                                        Text(profile.name)
                                    }
                                } else {
                                    OutlinedButton(onClick = { onPlaybackProfileSelected(profile) }) {
                                        Text(profile.name)
                                    }
                                }
                            }
                        }
                        Text(activeProfile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (onEqualizerPresetSelected != null || onReplayGainModeSelected != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = "Audio Equalizer and ReplayGain settings"
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Audio DSP: Equalizer & ReplayGain", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "10-band graphic equalizer with auto-attenuating preamp safety headroom (0 to -12 dB) preventing digital clipping.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onEqualizerPresetSelected != null) {
                            Text("Equalizer Preset:", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                EqualizerPreset.entries.take(5).forEach { preset ->
                                    val isSelected = activePreset == preset
                                    val label = preset.name.lowercase().replaceFirstChar { it.uppercase() }
                                    if (isSelected) {
                                        Button(onClick = { onEqualizerPresetSelected(preset) }) { Text(label) }
                                    } else {
                                        OutlinedButton(onClick = { onEqualizerPresetSelected(preset) }) { Text(label) }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                EqualizerPreset.entries.drop(5).forEach { preset ->
                                    val isSelected = activePreset == preset
                                    val label = preset.name.lowercase().replaceFirstChar { it.uppercase() }
                                    if (isSelected) {
                                        Button(onClick = { onEqualizerPresetSelected(preset) }) { Text(label) }
                                    } else {
                                        OutlinedButton(onClick = { onEqualizerPresetSelected(preset) }) { Text(label) }
                                    }
                                }
                            }
                        }

                        if (onReplayGainModeSelected != null) {
                            Text("ReplayGain Loudness Normalization:", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ReplayGainMode.entries.forEach { mode ->
                                    val isSelected = activeReplayGain == mode
                                    val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                                    if (isSelected) {
                                        Button(onClick = { onReplayGainModeSelected(mode) }) { Text(label) }
                                    } else {
                                        OutlinedButton(onClick = { onReplayGainModeSelected(mode) }) { Text(label) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (onSleepTimerSet != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = "Sleep timer settings"
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sleep Timer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (sleepTimerMinutesRemaining != null && sleepTimerMinutesRemaining > 0) {
                                "Active: playback will gently stop in $sleepTimerMinutesRemaining minute(s)."
                            } else {
                                "Set a timer to automatically pause playback."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sleepTimerMinutesRemaining != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                OutlinedButton(onClick = { onSleepTimerSet(mins) }) {
                                    Text("${mins}m")
                                }
                            }
                            if (sleepTimerMinutesRemaining != null) {
                                Button(onClick = { onSleepTimerSet(null) }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Library overview and storage management"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Library & Local Storage", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatCard("Music Tracks", state.audio.size.toString(), Modifier.weight(1f))
                        StatCard("Videos", state.video.size.toString(), Modifier.weight(1f))
                        StatCard("Playlists", state.playlists.size.toString(), Modifier.weight(1f))
                        StatCard("Favorites", state.favorites.size.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onAddFolder,
                            modifier = Modifier.semantics { contentDescription = "Add a new media folder to library" },
                        ) {
                            Text("Add Media Folder")
                        }
                        OutlinedButton(
                            onClick = onRefreshAvailability,
                            modifier = Modifier.semantics { contentDescription = "Refresh library availability" },
                        ) {
                            Text("Refresh Availability")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Diagnostics and software updates"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Diagnostics & Updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Export a privacy-safe diagnostic report (user paths and credentials are fully redacted) or check for verified updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (onExportDiagnostics != null) {
                            OutlinedButton(onClick = onExportDiagnostics) {
                                Text("Export Diagnostics")
                            }
                        }
                        if (onCheckForUpdates != null) {
                            OutlinedButton(onClick = onCheckForUpdates) {
                                Text("Check for Updates")
                            }
                        }
                        if (onOpenFeedback != null) {
                            Button(onClick = onOpenFeedback) {
                                Text("Help & Feedback")
                            }
                        }
                    }
                    diagnosticsStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    updateStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Privacy and philosophy pledge"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy & Philosophy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Vimivo is an ad-free, account-free, privacy-first multimedia player.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Zero analytics, zero tracking SDKs, zero telemetry. Your media files, playlists, tags, and playback history never leave your device. Network connections are initiated only when you explicitly stream or authorize remote media.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Application version information"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("About Vimivo", style = MaterialTheme.typography.titleMedium)
                    Text("Version 1.0.0-rc.1 (Release Candidate 1)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Tagline: Every Sound. Every Frame.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Built with Kotlin Multiplatform, Compose, and native multimedia engines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Software Updates: Vimivo never performs silent background code execution. Updates and cryptographic digests are published via official release manifests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
