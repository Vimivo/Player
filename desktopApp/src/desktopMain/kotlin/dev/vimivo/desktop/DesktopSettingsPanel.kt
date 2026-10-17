@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "LongMethod",
    "LongParameterList", "MaximumLineLength", "MaxLineLength", "Wrapping",
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.PlaybackProfile
import dev.vimivo.playback.ReplayGainMode
import dev.vimivo.settings.LibraryViewMode
import dev.vimivo.settings.RepeatBehavior
import dev.vimivo.settings.ShuffleBehavior
import dev.vimivo.settings.ThemeMode
import dev.vimivo.ui.components.VimivoConfirmationDialog
import dev.vimivo.ui.components.VimivoSettingsRow
import dev.vimivo.ui.components.VimivoSettingsSectionHeader

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
    onLibraryViewModeSelected: ((LibraryViewMode) -> Unit)? = null,
    onDefaultSortSelected: ((LibrarySort) -> Unit)? = null,
    onReduceAnimationsToggled: ((Boolean) -> Unit)? = null,
    onHighContrastToggled: ((Boolean) -> Unit)? = null,
    onReduceTransparencyToggled: ((Boolean) -> Unit)? = null,
    onResumePlaybackToggled: ((Boolean) -> Unit)? = null,
    onDefaultSpeedSelected: ((Float) -> Unit)? = null,
    onRepeatBehaviorSelected: ((RepeatBehavior) -> Unit)? = null,
    onShuffleBehaviorSelected: ((ShuffleBehavior) -> Unit)? = null,
    onResetSettings: (() -> Unit)? = null,
    activeProfile: PlaybackProfile = PlaybackProfile.DEFAULT,
    activePreset: EqualizerPreset = EqualizerPreset.FLAT,
    activeReplayGain: ReplayGainMode = ReplayGainMode.OFF,
    sleepTimerMinutesRemaining: Int? = null,
    diagnosticsStatus: String? = null,
    updateStatus: String? = null,
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        VimivoConfirmationDialog(
            title = "Reset Application Preferences?",
            message = "This will restore your theme, layout, accessibility, and playback defaults. Your media files, playlists, favorites, and history will remain completely untouched.",
            confirmLabel = "Reset Preferences",
            cancelLabel = "Cancel",
            onConfirm = {
                showResetDialog = false
                onResetSettings?.invoke()
            },
            onDismiss = { showResetDialog = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Category 1: Appearance & Theme
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Appearance and theme settings"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VimivoSettingsSectionHeader(
                        title = "Appearance & Theme",
                        subtitle = "Select an interface theme matching your display. AMOLED provides pure black background.",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val isSelected = state.themeMode == mode
                            val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                            if (isSelected) {
                                Button(
                                    onClick = { onThemeSelected(mode) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "$label theme, currently active"
                                    },
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onThemeSelected(mode) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Switch to $label theme"
                                    },
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category 2: Library & Presentation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Library presentation preferences"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VimivoSettingsSectionHeader(
                        title = "Library & Layout",
                        subtitle = "Customize the default presentation mode and sorting for local media browsing.",
                    )
                    VimivoSettingsRow(
                        title = "Default View Mode",
                        subtitle = "Choose between standard compact list rows or rich artwork grid cards.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryViewMode.entries.forEach { mode ->
                                val isSelected = state.libraryViewMode == mode
                                val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                                if (isSelected) {
                                    Button(onClick = { onLibraryViewModeSelected?.invoke(mode) }) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { onLibraryViewModeSelected?.invoke(mode) }) { Text(label) }
                                }
                            }
                        }
                    }
                    VimivoSettingsRow(
                        title = "Default Library Sort",
                        subtitle = "Preferred order when navigating audio and video collections.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(LibrarySort.TITLE, LibrarySort.ARTIST, LibrarySort.ALBUM, LibrarySort.DURATION).forEach { sort ->
                                val isSelected = state.defaultSort == sort
                                val label = sort.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                                if (isSelected) {
                                    Button(onClick = { onDefaultSortSelected?.invoke(sort) }) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { onDefaultSortSelected?.invoke(sort) }) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category 3: Playback Profiles & Audio DSP
        if (onPlaybackProfileSelected != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = "Playback profiles"
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VimivoSettingsSectionHeader(
                            title = "Playback Profiles",
                            subtitle = "Instant configuration presets optimizing speed, equalizer curves, ReplayGain, and aspect ratios.",
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
                        VimivoSettingsSectionHeader(
                            title = "Audio DSP: Equalizer & ReplayGain",
                            subtitle = "10-band graphic equalizer with auto-attenuating preamp safety headroom (0 to -12 dB) preventing digital clipping.",
                        )
                        if (onEqualizerPresetSelected != null) {
                            Text("Equalizer Preset:", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                EqualizerPreset.entries.take(6).forEach { preset ->
                                    val isSelected = activePreset == preset
                                    val label = preset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
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
                                EqualizerPreset.entries.drop(6).forEach { preset ->
                                    val isSelected = activePreset == preset
                                    val label = preset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
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
                        VimivoSettingsSectionHeader(
                            title = "Sleep Timer",
                            subtitle = if (sleepTimerMinutesRemaining != null && sleepTimerMinutesRemaining > 0) {
                                "Active: playback will gently stop in $sleepTimerMinutesRemaining minute(s)."
                            } else {
                                "Set a timer to automatically pause playback."
                            },
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

        // Category 4: Playback Defaults
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Playback behavior defaults"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VimivoSettingsSectionHeader(
                        title = "Playback & Video Defaults",
                        subtitle = "Configure speed, resumption, and default queue loop modes.",
                    )
                    VimivoSettingsRow(
                        title = "Resume Playback on Launch",
                        subtitle = "Restore last active track and playback position on application restart.",
                    ) {
                        Switch(
                            checked = state.resumePlayback,
                            onCheckedChange = { onResumePlaybackToggled?.invoke(it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Toggle resume playback on launch"
                            },
                        )
                    }
                    VimivoSettingsRow(
                        title = "Default Playback Speed",
                        subtitle = "Base speed applied to new media sessions.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                val isSelected = kotlin.math.abs(state.defaultSpeed - speed) < 0.05f
                                if (isSelected) {
                                    Button(onClick = { onDefaultSpeedSelected?.invoke(speed) }) { Text("${speed}x") }
                                } else {
                                    OutlinedButton(onClick = { onDefaultSpeedSelected?.invoke(speed) }) { Text("${speed}x") }
                                }
                            }
                        }
                    }
                    VimivoSettingsRow(
                        title = "Default Repeat Behavior",
                        subtitle = "Queue loop policy on startup.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RepeatBehavior.entries.forEach { behavior ->
                                val isSelected = state.repeatBehavior == behavior
                                val label = behavior.name.lowercase().replaceFirstChar { it.uppercase() }
                                if (isSelected) {
                                    Button(onClick = { onRepeatBehaviorSelected?.invoke(behavior) }) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { onRepeatBehaviorSelected?.invoke(behavior) }) { Text(label) }
                                }
                            }
                        }
                    }
                    VimivoSettingsRow(
                        title = "Default Shuffle Behavior",
                        subtitle = "Queue randomization policy on startup.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShuffleBehavior.entries.forEach { behavior ->
                                val isSelected = state.shuffleBehavior == behavior
                                val label = behavior.name.lowercase().replaceFirstChar { it.uppercase() }
                                if (isSelected) {
                                    Button(onClick = { onShuffleBehaviorSelected?.invoke(behavior) }) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { onShuffleBehaviorSelected?.invoke(behavior) }) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category 5: Accessibility & Motion
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Accessibility and visual comfort settings"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VimivoSettingsSectionHeader(
                        title = "Accessibility & Motion",
                        subtitle = "Adjust animation speeds, transparency, and high contrast for optimal visual comfort.",
                    )
                    VimivoSettingsRow(
                        title = "Reduce Animations",
                        subtitle = "Disables fluid slide transitions and animation timers to prevent vestibular motion sickness.",
                    ) {
                        Switch(
                            checked = state.reduceAnimations,
                            onCheckedChange = { onReduceAnimationsToggled?.invoke(it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Toggle reduce animations"
                            },
                        )
                    }
                    VimivoSettingsRow(
                        title = "High Contrast Mode",
                        subtitle = "Increases border brightness and text contrast across all surfaces and controls.",
                    ) {
                        Switch(
                            checked = state.highContrast,
                            onCheckedChange = { onHighContrastToggled?.invoke(it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Toggle high contrast mode"
                            },
                        )
                    }
                    VimivoSettingsRow(
                        title = "Reduce Transparency",
                        subtitle = "Replaces translucent glass surfaces with solid opaque backgrounds.",
                    ) {
                        Switch(
                            checked = state.reduceTransparency,
                            onCheckedChange = { onReduceTransparencyToggled?.invoke(it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Toggle reduce transparency"
                            },
                        )
                    }
                }
            }
        }

        // Category 6: Library & Local Storage
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Library overview and storage management"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VimivoSettingsSectionHeader(
                        title = "Library & Local Storage",
                        subtitle = "Locally indexed multimedia files and active personal collections.",
                    )
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

        // Category 7: Diagnostics & Software Updates
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Diagnostics and software updates"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VimivoSettingsSectionHeader(
                        title = "Diagnostics & Updates",
                        subtitle = "Export privacy-safe diagnostics or check for cryptographically verified updates.",
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

        // Category 8: Privacy & Philosophy
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

        // Category 9: About Vimivo & Safe Preference Reset
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Application version information and settings reset"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.semantics {
                            contentDescription = "Reset application settings to defaults"
                        },
                    ) {
                        Text("Reset Preferences to Default")
                    }
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
