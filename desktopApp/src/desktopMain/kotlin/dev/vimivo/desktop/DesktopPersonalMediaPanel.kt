@file:Suppress(
    "ArgumentListWrapping", "LongMethod", "LongParameterList", "MaximumLineLength", "MaxLineLength",
    "ParameterListWrapping", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.Playlist
import dev.vimivo.desktop.library.DesktopHistoryItem
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.playback.PlaybackQueueState

@Composable
internal fun DesktopPlaylistsPanel(
    state: DesktopLibraryUiState,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onRemove: (String, MediaId) -> Unit,
    onMove: (String, MediaId, Int) -> Unit,
    onPlay: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val mediaById = state.personalMedia
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(newName, { newName = it }, label = { Text("Playlist name") }, singleLine = true)
        Button(
            enabled = newName.isNotBlank(),
            onClick = { onCreate(newName); newName = "" },
        ) { Text("Create") }
    }
    if (state.playlists.isEmpty()) Text("No playlists yet. Create one, then add media from Music or Videos.")
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.playlists, key = { it.id }) { playlist ->
            var editedName by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(editedName, { editedName = it }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedButton(onClick = { onRename(playlist.id, editedName) }, enabled = editedName.isNotBlank()) { Text("Rename") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlay(playlist.id) }, enabled = playlist.items.isNotEmpty()) { Text("Play") }
                        OutlinedButton(onClick = { onDuplicate(playlist.id) }) { Text("Duplicate") }
                        OutlinedButton(onClick = { pendingDelete = playlist.id }) { Text("Delete") }
                    }
                    playlist.items.forEachIndexed { index, mediaId ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(mediaById[mediaId]?.title ?: "Unavailable media", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { onMove(playlist.id, mediaId, index - 1) }, enabled = index > 0) { Text("↑") }
                            OutlinedButton(onClick = { onMove(playlist.id, mediaId, index + 1) }, enabled = index < playlist.items.lastIndex) { Text("↓") }
                            OutlinedButton(onClick = { onRemove(playlist.id, mediaId) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { playlistId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("The playlist will be removed. Your original media files will not be changed.") },
            confirmButton = {
                Button(onClick = { onDelete(playlistId); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun DesktopFavoritesPanel(items: List<MediaItem>, onPlay: (MediaItem) -> Unit, onToggle: (MediaItem) -> Unit) {
    if (items.isEmpty()) Text("No favorites yet. Mark media from the Music or Videos library.")
    MediaActionList(items, onPlay) { media -> OutlinedButton(onClick = { onToggle(media) }) { Text("Unfavorite") } }
}

@Composable
internal fun DesktopHistoryPanel(items: List<DesktopHistoryItem>, onPlay: (MediaItem) -> Unit, onRemove: (MediaId) -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    if (items.isNotEmpty()) OutlinedButton(onClick = { confirmClear = true }) { Text("Clear history") }
    if (items.isEmpty()) Text("Playback history is empty.")
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.media.id.value }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.media.title, style = MaterialTheme.typography.titleMedium)
                        Text("Played ${item.history.playCount} time(s) · ${formatPersonalTime(item.history.positionMillis)}")
                    }
                    Button(onClick = { onPlay(item.media) }) { Text(if (item.history.completed) "Play" else "Resume") }
                    OutlinedButton(onClick = { onRemove(item.media.id) }) { Text("Remove") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear playback history?") },
            text = { Text("This removes local history entries. It does not delete media files or playlists.") },
            confirmButton = { Button(onClick = { onClear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun DesktopBookmarksPanel(
    media: MediaItem?,
    positionMillis: Long,
    bookmarks: List<Bookmark>,
    onAdd: (MediaId, Long, String?) -> Unit,
    onSeek: (Long) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (media == null) return
    var label by remember(media.id) { mutableStateOf("") }
    val matching = bookmarks.filter { it.mediaId == media.id }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(label, { label = it }, label = { Text("Bookmark label") }, singleLine = true)
        OutlinedButton(onClick = { onAdd(media.id, positionMillis, label.ifBlank { null }); label = "" }) { Text("Bookmark") }
    }
    matching.forEach { bookmark ->
        var editedLabel by remember(bookmark.id, bookmark.name) { mutableStateOf(bookmark.name) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(editedLabel, { editedLabel = it }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedButton(onClick = { onRename(bookmark.id, editedLabel) }) { Text("Rename") }
            OutlinedButton(onClick = { onSeek(bookmark.positionMillis) }) {
                Text("Seek · ${formatPersonalTime(bookmark.positionMillis)}")
            }
            OutlinedButton(onClick = { onDelete(bookmark.id) }) { Text("Remove") }
        }
    }
}

@Composable
internal fun AddToPlaylistButton(playlists: List<Playlist>, media: MediaItem, onAdd: (String, MediaItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, enabled = playlists.isNotEmpty()) { Text("Add to playlist") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        playlists.forEach { playlist ->
            DropdownMenuItem(
                text = { Text(playlist.name) },
                onClick = { onAdd(playlist.id, media); expanded = false },
            )
        }
    }
}

@Composable
internal fun DesktopQueuePanel(
    state: PlaybackQueueState,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    if (state.items.isEmpty()) return
    Text("Queue · ${state.items.size} item(s)", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = onClear) { Text("Clear queue") }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(state.items.size, key = { state.items[it].queueId }) { index ->
            val item = state.items[index]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (index == state.currentIndex) "▶ ${item.media.title}" else item.media.title, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("↑") }
                OutlinedButton(onClick = { onMove(index, index + 1) }, enabled = index < state.items.lastIndex) { Text("↓") }
                OutlinedButton(onClick = { onRemove(index) }, enabled = index != state.currentIndex) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun MediaActionList(items: List<MediaItem>, onPlay: (MediaItem) -> Unit, action: @Composable (MediaItem) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.id.value }) { media ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(media.title, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(media.artist, media.album, media.fileName).joinToString(" · "))
                    }
                    Button(onClick = { onPlay(media) }) { Text("Play") }
                    action(media)
                }
            }
        }
    }
}

private fun formatPersonalTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
