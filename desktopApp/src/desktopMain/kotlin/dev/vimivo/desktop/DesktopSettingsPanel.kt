@file:Suppress("ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.vimivo.settings.ThemeMode

@Composable
internal fun DesktopSettingsPanel(
    state: DesktopLibraryUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    onAddFolder: () -> Unit,
    onRefreshAvailability: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
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
