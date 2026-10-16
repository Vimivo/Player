@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "Indentation", "LongMethod",
    "LongParameterList", "MagicNumber", "MaximumLineLength", "MaxLineLength", "NoConsecutiveBlankLines",
    "ParameterListWrapping", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.DuplicateConfidence
import dev.vimivo.core.model.DuplicateGroup
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.RecommendationItem
import dev.vimivo.core.model.SmartRuleCondition
import dev.vimivo.core.model.SmartRuleField
import dev.vimivo.core.model.SmartRuleOperator
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.desktop.library.DesktopSmartPlaylistRepository

@Composable
internal fun DesktopPersonalizedHomePanel(
    state: DesktopLibraryUiState,
    onPlay: (MediaItem) -> Unit,
    onDismissRecommendation: (String, String) -> Unit,
    onTogglePersonalization: (Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Offline Personalization", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "100% private, local suggestions based strictly on your library and history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.personalizationEnabled) "Enabled" else "Disabled")
                    Switch(
                        checked = state.personalizationEnabled,
                        onCheckedChange = onTogglePersonalization,
                    )
                }
            }
        }

        if (state.personalizationEnabled && state.recommendations.isNotEmpty()) {
            item {
                Text("Recommended for You", style = MaterialTheme.typography.titleLarge)
            }
            items(state.recommendations) { rec ->
                RecommendationCard(rec, onPlay = { onPlay(rec.media) }, onDismiss = {
                    onDismissRecommendation(rec.category.name, rec.media.id.value)
                })
            }
        } else if (state.personalizationEnabled) {
            item {
                Text(
                    "No active recommendations. Listen to music or watch videos to see local suggestions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.listeningInsights?.let { insights ->
            item {
                Text("Listening Insights", style = MaterialTheme.typography.titleLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Total Listening: ${formatTime(insights.totalListeningDurationMillis)}")
                        Text("Tracks Played: ${insights.totalTracksPlayed}")
                        Text("Unplayed Tracks in Library: ${insights.unplayedTracksCount}")
                        if (insights.topArtists.isNotEmpty()) {
                            Text("Top Artists: " + insights.topArtists.joinToString { "${it.first} (${it.second})" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: RecommendationItem,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(rec.media.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    rec.media.artist ?: "Unknown Artist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    rec.explanation,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay) { Text("Play") }
                OutlinedButton(onClick = onDismiss) { Text("Hide") }
            }
        }
    }
}

@Composable
internal fun DesktopSmartPlaylistsPanel(
    state: DesktopLibraryUiState,
    controller: DesktopLibraryController,
    onPlay: (MediaItem) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedField by remember { mutableStateOf(SmartRuleField.ARTIST) }
    var selectedOp by remember { mutableStateOf(SmartRuleOperator.CONTAINS) }
    var ruleValue by remember { mutableStateOf("") }

    val allSmart = remember(state.smartPlaylists) {
        DesktopSmartPlaylistRepository.builtInCollections() + state.smartPlaylists
    }

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create Smart Playlist", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Field: ${selectedField.name}")
                        OutlinedButton(onClick = {
                            val values = SmartRuleField.entries
                            val nextIdx = (values.indexOf(selectedField) + 1) % values.size
                            selectedField = values[nextIdx]
                        }) { Text("Cycle Field") }

                        Text("Op: ${selectedOp.name}")
                        OutlinedButton(onClick = {
                            val values = SmartRuleOperator.entries
                            val nextIdx = (values.indexOf(selectedOp) + 1) % values.size
                            selectedOp = values[nextIdx]
                        }) { Text("Cycle Operator") }
                    }
                    OutlinedTextField(
                        value = ruleValue,
                        onValueChange = { ruleValue = it },
                        label = { Text("Value (e.g. Rock, 2024, etc.)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                controller.createSmartPlaylist(
                                    name = newPlaylistName,
                                    conditions = listOf(SmartRuleCondition(selectedField, selectedOp, ruleValue)),
                                )
                                newPlaylistName = ""
                                ruleValue = ""
                            }
                        },
                        enabled = newPlaylistName.isNotBlank(),
                    ) {
                        Text("Save Smart Playlist")
                    }
                }
            }
        }

        items(allSmart) { definition ->
            val matches = remember(definition, state.audio, state.video) {
                controller.evaluateSmartPlaylist(definition)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(definition.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${matches.size} tracks matched",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (matches.isNotEmpty()) {
                                Button(onClick = { onPlay(matches.first()) }) { Text("Play All") }
                            }
                            if (!definition.id.startsWith("builtin-")) {
                                OutlinedButton(onClick = { controller.deleteSmartPlaylist(definition.id) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                    if (definition.conditions.isNotEmpty()) {
                        Text(
                            "Rules: " + definition.conditions.joinToString { "${it.field} ${it.operator} '${it.value}'" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopDuplicatesPanel(
    state: DesktopLibraryUiState,
    controller: DesktopLibraryController,
    onPlay: (MediaItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Duplicate Detection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Safe, non-destructive discovery using exact content hashing. No files are auto-deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { controller.scanDuplicates() }) {
                    Text("Scan for Duplicates")
                }
            }
        }

        if (state.duplicateGroups.isEmpty()) {
            item {
                Text(
                    "No duplicate groups found. Click 'Scan for Duplicates' to evaluate candidates.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.duplicateGroups) { group ->
                DuplicateGroupCard(group, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onPlay: (MediaItem) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (group.confidence == DuplicateConfidence.EXACT_CONTENT_MATCH) "Exact Content Match" else "Probable Duplicate",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (group.confidence == DuplicateConfidence.EXACT_CONTENT_MATCH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Text("${group.items.size} copies", style = MaterialTheme.typography.labelSmall)
            }
            Text(group.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            group.items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(item.fileName ?: item.uri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onPlay(item) }) { Text("Play") }
                }
            }
        }
    }
}

@Composable
internal fun DesktopInsightsPanel(
    state: DesktopLibraryUiState,
) {
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Listening & Watching Insights", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Aggregated locally from playback history. Never transmitted or profiled externally.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.listeningInsights?.let { listening ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Audio Summary", style = MaterialTheme.typography.titleMedium)
                        Text("Total Listening Time: ${formatTime(listening.totalListeningDurationMillis)}")
                        Text("Total Tracks Played: ${listening.totalTracksPlayed}")
                        Text("Unplayed Tracks Remaining: ${listening.unplayedTracksCount}")

                        Spacer(Modifier.height(8.dp))
                        Text("Top Artists", style = MaterialTheme.typography.titleSmall)
                        listening.topArtists.forEach { (artist, plays) ->
                            Text("• $artist — $plays plays")
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Top Albums", style = MaterialTheme.typography.titleSmall)
                        listening.topAlbums.forEach { (album, plays) ->
                            Text("• $album — $plays plays")
                        }
                    }
                }
            }
        }

        state.watchingInsights?.let { watching ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Video Summary", style = MaterialTheme.typography.titleMedium)
                        Text("Total Watch Time: ${formatTime(watching.totalWatchingDurationMillis)}")
                        Text("Total Videos Played: ${watching.totalVideosPlayed}")
                        Text("Completed Videos: ${watching.completedVideosCount}")
                        Text("In-Progress / Continue Watching: ${watching.continueWatchingCount}")
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
