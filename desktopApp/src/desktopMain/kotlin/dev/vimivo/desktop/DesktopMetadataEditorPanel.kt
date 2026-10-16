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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.FieldAction
import dev.vimivo.core.model.MetadataChangeset
import dev.vimivo.core.model.MetadataWriteMode
import dev.vimivo.core.model.RenamePlanItem
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopLibraryUiState
import java.io.File
import java.net.URI

@Composable
internal fun DesktopMetadataEditorPanel(
    state: DesktopLibraryUiState,
    controller: DesktopLibraryController,
) {
    val selectedItems = remember(state.selectedMediaIds, state.audio, state.video) {
        val all = state.audio + state.video
        all.filter { it.id in state.selectedMediaIds }
    }

    var writeMode by remember { mutableStateOf(MetadataWriteMode.DATABASE_OVERRIDE) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Metadata Editor", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${selectedItems.size} media items selected for editing. Choose between non-destructive database overrides or embedded file tag modification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mode: ${if (writeMode == MetadataWriteMode.DATABASE_OVERRIDE) "Database Override (Safe)" else "Embedded File Tag Write"}")
                        OutlinedButton(onClick = {
                            writeMode = if (writeMode == MetadataWriteMode.DATABASE_OVERRIDE) {
                                MetadataWriteMode.EMBEDDED_FILE_TAG
                            } else {
                                MetadataWriteMode.DATABASE_OVERRIDE
                            }
                        }) {
                            Text("Switch Mode")
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (leave blank to keep unchanged)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist (leave blank to keep unchanged)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text("Album (leave blank to keep unchanged)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it },
                        label = { Text("Album Artist (leave blank to keep unchanged)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre (leave blank to keep unchanged)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Year (leave blank to keep unchanged)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val releaseYearInt = year.trim().toIntOrNull()
                                selectedItems.forEach { item ->
                                    if (writeMode == MetadataWriteMode.DATABASE_OVERRIDE) {
                                        controller.saveMetadataOverride(
                                            mediaId = item.id,
                                            title = title.takeIf(String::isNotBlank) ?: item.title,
                                            artist = artist.takeIf(String::isNotBlank) ?: item.artist,
                                            album = album.takeIf(String::isNotBlank) ?: item.album,
                                            albumArtist = albumArtist.takeIf(String::isNotBlank) ?: item.albumArtist,
                                            genre = genre.takeIf(String::isNotBlank) ?: item.genre,
                                            releaseYear = releaseYearInt ?: item.releaseYear,
                                            trackNumber = item.trackNumber,
                                            discNumber = item.discNumber,
                                        )
                                    } else {
                                        val file = runCatching { File(URI(item.uri)) }.getOrNull()
                                        if (file != null && file.isFile) {
                                            val changeset = MetadataChangeset(
                                                title = if (title.isNotBlank()) FieldAction.Set(title) else FieldAction.Unchanged,
                                                artist = if (artist.isNotBlank()) FieldAction.Set(artist) else FieldAction.Unchanged,
                                                album = if (album.isNotBlank()) FieldAction.Set(album) else FieldAction.Unchanged,
                                                albumArtist = if (albumArtist.isNotBlank()) FieldAction.Set(albumArtist) else FieldAction.Unchanged,
                                                genre = if (genre.isNotBlank()) FieldAction.Set(genre) else FieldAction.Unchanged,
                                                releaseYear = if (releaseYearInt != null) FieldAction.Set(releaseYearInt) else FieldAction.Unchanged,
                                            )
                                            controller.applyEmbeddedTagWrite(file, changeset)
                                        }
                                    }
                                }
                                statusMessage = "Saved updates for ${selectedItems.size} items."
                            },
                            enabled = selectedItems.isNotEmpty(),
                        ) {
                            Text("Apply Changes")
                        }

                        OutlinedButton(
                            onClick = {
                                selectedItems.forEach { controller.clearMetadataOverride(it.id) }
                                statusMessage = "Cleared overrides for ${selectedItems.size} items."
                            },
                            enabled = selectedItems.isNotEmpty(),
                        ) {
                            Text("Clear Database Overrides")
                        }

                        OutlinedButton(onClick = { controller.clearSelection() }) {
                            Text("Deselect All")
                        }
                    }

                    statusMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Select items from library below to edit:", style = MaterialTheme.typography.titleMedium)
        }

        val allItems = state.audio + state.video
        items(allItems) { item ->
            val isSelected = item.id in state.selectedMediaIds
            Card(
                Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${item.artist ?: "Unknown Artist"} • ${item.album ?: "Unknown Album"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { controller.toggleMediaSelection(item.id) }) {
                        Text(if (isSelected) "Deselect" else "Select")
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopBatchRenamePanel(
    state: DesktopLibraryUiState,
    controller: DesktopLibraryController,
) {
    var template by remember { mutableStateOf("{track} - {title}") }
    val allItems = remember(state.audio, state.video) { state.audio + state.video }
    var plans by remember { mutableStateOf<List<RenamePlanItem>>(emptyList()) }
    var renameMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Batch File Rename Planner", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Safely preview and rename physical files based on metadata tokens. All library references, playlists, and history are preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Pattern Template") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        "Supported tokens: {artist}, {albumArtist}, {album}, {title}, {track}, {disc}, {year}, {genre}, {originalName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            plans = controller.evaluateRename(allItems, template)
                            renameMessage = null
                        }) {
                            Text("Preview Renames")
                        }
                        Button(
                            onClick = {
                                val res = controller.executeRename(plans)
                                renameMessage = "Successfully renamed ${res.succeeded.size} files (${res.failed.size} failed)."
                                plans = emptyList()
                            },
                            enabled = plans.isNotEmpty() && plans.none { it.conflict },
                        ) {
                            Text("Execute Renames (${plans.count { !it.conflict }})")
                        }
                    }

                    renameMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (plans.isNotEmpty()) {
            item {
                Text("Rename Previews:", style = MaterialTheme.typography.titleMedium)
            }
            items(plans) { plan ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Original: ${plan.originalFileName}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Proposed: ${plan.proposedFileName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (plan.conflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        if (plan.conflict) {
                            Text("Conflict: ${plan.conflictReason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopMissingMediaPanel(
    state: DesktopLibraryUiState,
    controller: DesktopLibraryController,
) {
    val allItems = remember(state.audio, state.video) { state.audio + state.video }
    val missingItems = remember(allItems) { controller.findMissingMedia() }
    var relinkMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Missing Media Reconciliation", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Detects moved, disconnected, or renamed media files and relinks them safely without losing personal data.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Found ${missingItems.size} missing media files.")
            relinkMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        items(missingItems) { item ->
            val roots = state.roots.map { it.path.toFile() }
            val candidates = remember(item, roots) { controller.suggestRelinkCandidates(item, roots) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text("Original URI: ${item.uri}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (candidates.isEmpty()) {
                        Text("No matching candidate files found in library folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Suggested Candidates:", style = MaterialTheme.typography.labelSmall)
                        candidates.forEach { cand ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cand.candidateMedia.fileName ?: cand.candidateMedia.uri, style = MaterialTheme.typography.bodySmall)
                                    Text(cand.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Button(onClick = {
                                    val candidateFile = File(URI(cand.candidateMedia.uri))
                                    controller.relinkMedia(item.id, candidateFile).onSuccess {
                                        relinkMessage = "Relinked ${item.title} successfully."
                                    }
                                }) {
                                    Text("Relink")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
