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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.library.DesktopHistoryItem
import dev.vimivo.desktop.library.DesktopLibraryUiState

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
                        OutlinedButton(onClick = { onDelete(playlist.id) }) { Text("Delete") }
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
}

@Composable
internal fun DesktopFavoritesPanel(items: List<MediaItem>, onPlay: (MediaItem) -> Unit, onToggle: (MediaItem) -> Unit) {
    if (items.isEmpty()) Text("No favorites yet. Mark media from the Music or Videos library.")
    MediaActionList(items, onPlay) { media -> OutlinedButton(onClick = { onToggle(media) }) { Text("Unfavorite") } }
}

@Composable
internal fun DesktopHistoryPanel(items: List<DesktopHistoryItem>, onPlay: (MediaItem) -> Unit, onRemove: (MediaId) -> Unit, onClear: () -> Unit) {
    if (items.isNotEmpty()) OutlinedButton(onClick = onClear) { Text("Clear history") }
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
}

@Composable
internal fun DesktopBookmarksPanel(
    media: MediaItem?,
    positionMillis: Long,
    bookmarks: List<Bookmark>,
    onAdd: (MediaId, Long, String?) -> Unit,
    onSeek: (Long) -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSeek(bookmark.positionMillis) }) {
                Text("${bookmark.name.ifBlank { "Bookmark" }} · ${formatPersonalTime(bookmark.positionMillis)}")
            }
            OutlinedButton(onClick = { onDelete(bookmark.id) }) { Text("Remove") }
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
