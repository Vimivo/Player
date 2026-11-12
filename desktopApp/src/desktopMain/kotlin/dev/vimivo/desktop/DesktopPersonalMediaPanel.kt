@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "LongMethod", "LongParameterList", "MaximumLineLength", "MaxLineLength",
    "NoConsecutiveBlankLines", "ParameterListWrapping", "TooManyFunctions", "Wrapping",
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.Playlist
import dev.vimivo.desktop.library.DesktopHistoryItem
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.playback.PlaybackQueueState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt

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
    onShufflePlay: ((String) -> Unit)? = null,
    onPlayNext: ((String) -> Unit)? = null,
    onAddToQueue: ((String) -> Unit)? = null,
    onPlayMedia: ((List<MediaItem>, Int) -> Unit)? = null,
    onPlayMediaNext: ((MediaItem) -> Unit)? = null,
    onAddMediaToQueue: ((MediaItem) -> Unit)? = null,
    onExportM3u: ((String, java.io.File) -> Unit)? = null,
    onImportM3u: ((java.io.File) -> Unit)? = null,
) {
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val mediaById = state.personalMedia
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(newName, { newName = it }, label = { Text("Playlist name") }, singleLine = true)
        Button(
            enabled = newName.isNotBlank(),
            onClick = { onCreate(newName); newName = "" },
        ) { Text("Create") }
        if (onImportM3u != null) {
            OutlinedButton(onClick = { chooseM3uFile()?.let(onImportM3u) }) { Text("Import M3U") }
        }
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
                        Button(onClick = { onPlay(playlist.id) }, enabled = playlist.items.isNotEmpty()) { Text("Play All") }
                        if (onShufflePlay != null) {
                            OutlinedButton(onClick = { onShufflePlay(playlist.id) }, enabled = playlist.items.isNotEmpty()) { Text("Shuffle") }
                        }
                        if (onPlayNext != null) {
                            OutlinedButton(onClick = { onPlayNext(playlist.id) }, enabled = playlist.items.isNotEmpty()) { Text("Play Next") }
                        }
                        if (onAddToQueue != null) {
                            OutlinedButton(onClick = { onAddToQueue(playlist.id) }, enabled = playlist.items.isNotEmpty()) { Text("+ Queue") }
                        }
                        if (onExportM3u != null) {
                            OutlinedButton(onClick = { saveM3uFile(playlist.name)?.let { file -> onExportM3u(playlist.id, file) } }) {
                                Text("Export…")
                            }
                        }
                        OutlinedButton(onClick = { onDuplicate(playlist.id) }) { Text("Duplicate") }
                        OutlinedButton(onClick = { pendingDelete = playlist.id }) { Text("Delete") }
                    }
                    playlist.items.forEachIndexed { index, mediaId ->
                        val media = mediaById[mediaId]
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(media?.title ?: "Unavailable media", modifier = Modifier.weight(1f))
                            if (media != null && onPlayMedia != null) {
                                OutlinedButton(onClick = {
                                    val playlistMedia = playlist.items.mapNotNull(mediaById::get)
                                    onPlayMedia(playlistMedia, playlistMedia.indexOf(media))
                                }) { Text("Play") }
                            }
                            if (media != null && onPlayMediaNext != null) {
                                OutlinedButton(onClick = { onPlayMediaNext(media) }) { Text("Next") }
                            }
                            if (media != null && onAddMediaToQueue != null) {
                                OutlinedButton(onClick = { onAddMediaToQueue(media) }) { Text("+ Queue") }
                            }
                            OutlinedButton(
                                onClick = { onMove(playlist.id, mediaId, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.semantics { contentDescription = "Move ${media?.title ?: "track"} up in playlist" },
                            ) { Text("↑") }
                            OutlinedButton(
                                onClick = { onMove(playlist.id, mediaId, index + 1) },
                                enabled = index < playlist.items.lastIndex,
                                modifier = Modifier.semantics { contentDescription = "Move ${media?.title ?: "track"} down in playlist" },
                            ) { Text("↓") }
                            OutlinedButton(
                                onClick = { onRemove(playlist.id, mediaId) },
                                modifier = Modifier.semantics { contentDescription = "Remove ${media?.title ?: "track"} from playlist" },
                            ) { Text("Remove") }
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
internal fun DesktopFavoritesPanel(
    items: List<MediaItem>,
    onPlay: (List<MediaItem>, Int) -> Unit,
    onPlayNext: ((MediaItem) -> Unit)? = null,
    onAddToQueue: ((MediaItem) -> Unit)? = null,
    onToggle: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) Text("No favorites yet. Mark media from the Music or Videos library.")
    MediaActionList(items, onPlay, onPlayNext, onAddToQueue) { media -> OutlinedButton(onClick = { onToggle(media) }) { Text("Unfavorite") } }
}

@Composable
internal fun DesktopHistoryPanel(
    items: List<DesktopHistoryItem>,
    onPlay: (List<MediaItem>, Int) -> Unit,
    onPlayNext: ((MediaItem) -> Unit)? = null,
    onAddToQueue: ((MediaItem) -> Unit)? = null,
    onRemove: (MediaId) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (items.isNotEmpty()) OutlinedButton(onClick = { confirmClear = true }) { Text("Clear history") }
    if (items.isEmpty()) Text("Playback history is empty.")
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.media.id.value }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopArtwork(item.media.artworkUri, item.media.title)
                    Column(Modifier.weight(1f)) {
                        Text(item.media.title, style = MaterialTheme.typography.titleMedium)
                        Text("Played ${item.history.playCount} time(s) · ${formatPersonalTime(item.history.positionMillis)}")
                    }
                    Button(onClick = { onPlay(items.map { it.media }, items.indexOf(item)) }) {
                        Text(if (item.history.completed) "Play" else "Resume")
                    }
                    if (onPlayNext != null) OutlinedButton(onClick = { onPlayNext(item.media) }) { Text("Next") }
                    if (onAddToQueue != null) OutlinedButton(onClick = { onAddToQueue(item.media) }) { Text("+ Queue") }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DesktopArtwork(media.artworkUri, bookmark.name, size = 36.dp)
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
    onPlayAt: ((Int) -> Unit)? = null,
) {
    if (state.items.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Queue · ${state.items.size} item(s)", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            state.sourceLabel?.let {
                Text("Source: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onClear) { Text("Clear") }
    }
    Text(
        "Press and hold a song, then drag it to reorder.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(state.items.size, key = { state.items[it].queueId }) { index ->
            QueueRow(state, index, onRemove, onMove, onPlayAt)
        }
    }
}

/** One queue entry: the title takes the free width and shrinks with an ellipsis, so a narrow panel never breaks it letter by letter. */
@Composable
private fun QueueRow(
    state: PlaybackQueueState,
    index: Int,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayAt: ((Int) -> Unit)?,
) {
    val item = state.items[index]
    val isCurrent = index == state.currentIndex
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(1f) }
    val dragging = dragOffset != 0f
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth()
            .onGloballyPositioned { rowHeight = it.size.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationY = dragOffset; shadowElevation = if (dragging) 12f else 0f }
            .pointerInput(index, state.items.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffset = 0.01f },
                    onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                    onDragEnd = {
                        val target = (index + (dragOffset / rowHeight).roundToInt()).coerceIn(0, state.items.lastIndex)
                        dragOffset = 0f
                        if (target != index) onMove(index, target)
                    },
                    onDragCancel = { dragOffset = 0f },
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopArtwork(item.media.artworkUri, item.media.title, 36.dp)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    item.media.title,
                    style = if (isCurrent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.media.artist, item.media.durationMillis?.let(::formatPersonalTime)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = "Playing now", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            } else if (onPlayAt != null) {
                QueueIconButton(Icons.Outlined.PlayArrow, "Play ${item.media.title}") { onPlayAt(index) }
            }
            QueueIconButton(Icons.Outlined.ArrowUpward, "Move ${item.media.title} up in queue", enabled = index > 0) { onMove(index, index - 1) }
            QueueIconButton(Icons.Outlined.ArrowDownward, "Move ${item.media.title} down in queue", enabled = index < state.items.lastIndex) { onMove(index, index + 1) }
            QueueIconButton(Icons.Outlined.Close, "Remove ${item.media.title} from queue", enabled = !isCurrent) { onRemove(index) }
        }
    }
}

@Composable
private fun QueueIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MediaActionList(
    items: List<MediaItem>,
    onPlay: (List<MediaItem>, Int) -> Unit,
    onPlayNext: ((MediaItem) -> Unit)? = null,
    onAddToQueue: ((MediaItem) -> Unit)? = null,
    action: @Composable (MediaItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.id.value }) { media ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopArtwork(media.artworkUri, media.title)
                    Column(Modifier.weight(1f)) {
                        Text(media.title, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(media.artist, media.album, media.fileName).joinToString(" · "))
                    }
                    Button(onClick = { onPlay(items, items.indexOf(media)) }) { Text("Play") }
                    if (onPlayNext != null) OutlinedButton(onClick = { onPlayNext(media) }) { Text("Next") }
                    if (onAddToQueue != null) OutlinedButton(onClick = { onAddToQueue(media) }) { Text("+ Queue") }
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

private fun chooseM3uFile(): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Import Playlist (M3U, PLS, XSPF)", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> listOf(".m3u", ".m3u8", ".pls", ".xspf").any { name.endsWith(it, ignoreCase = true) } }
    dialog.isVisible = true
    return dialog.files?.firstOrNull() ?: dialog.file?.let { java.io.File(dialog.directory, it) }
}

private fun saveM3uFile(defaultName: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Export Playlist (.m3u8 or .xspf)", java.awt.FileDialog.SAVE)
    dialog.file = "$defaultName.m3u8"
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    val supported = listOf(".m3u8", ".m3u", ".xspf").any { fileName.endsWith(it, true) }
    return java.io.File(dir, if (supported) fileName else "$fileName.m3u8")
}
