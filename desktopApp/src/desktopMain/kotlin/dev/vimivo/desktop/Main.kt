@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "ImportOrdering", "Indentation",
    "LongMethod", "LongParameterList", "MagicNumber", "MaximumLineLength", "MaxLineLength", "TooManyFunctions",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.platform.MacOpenFileHandler
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.desktop.library.LibraryScanState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main(args: Array<String>) = application {
    val engine = remember { MpvPlayerEngine() }
    val openFileHandler = remember { MacOpenFileHandler().also { it.install() } }
    Window(
        onCloseRequest = {
            engine.close()
            openFileHandler.close()
            exitApplication()
        },
        title = "Vimivo",
    ) {
        MenuBar {
            Menu("File") {
                Item("Open File…", onClick = { chooseMediaFiles().let(openFileHandler::submit) })
            }
            Menu("Playback") {
                Item("Play / Pause", onClick = {
                    if (engine.state.value.status == PlaybackStatus.Playing) engine.pause() else engine.play()
                })
                Item("Stop", onClick = { engine.stop() })
                Separator()
                Item("Seek Backward", onClick = { engine.seekTo((engine.state.value.positionMillis - 10_000).coerceAtLeast(0)) })
                Item("Seek Forward", onClick = { engine.seekTo(engine.state.value.positionMillis + 10_000) })
            }
        }
        MaterialTheme { DesktopPlayer(engine, args.map(::File), openFileHandler) }
    }
}

@Composable
private fun DesktopPlayer(engine: MpvPlayerEngine, initialFiles: List<File>, openFileHandler: MacOpenFileHandler) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val library = remember { DesktopLibraryController() }
    val libraryState by library.state.collectAsState()
    var networkUrl by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(DesktopDestination.Home) }
    val videoCanvas = remember { Canvas() }
    val playMedia: (MediaItem) -> Unit = { media ->
        scope.launch {
            library.preparePlayback(media).onSuccess { request ->
                engine.open(request.media, request.startPositionMillis).onSuccess { engine.play() }
            }
        }
    }
    DisposableEffect(engine) { onDispose(engine::close) }
    DisposableEffect(library) { onDispose(library::close) }
    DisposableEffect(videoCanvas) {
        engine.attachVideoSurface(videoCanvas)
        onDispose { }
    }
    LaunchedEffect(initialFiles, openFileHandler) {
        initialFiles.filter(File::isFile).takeIf(List<File>::isNotEmpty)?.let { files ->
            engine.open(files.first().toMediaItem()).onSuccess { engine.play() }
        }
        openFileHandler.files.collect { files ->
            files.firstOrNull()?.let { file ->
                engine.open(file.toMediaItem()).onSuccess { engine.play() }
            }
        }
    }
    LaunchedEffect(engine, library) {
        library.observePlayback(engine.state)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize()) {
            DesktopSidebar(destination, onDestinationSelected = { destination = it })
            Column(
                Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(destination.title, style = MaterialTheme.typography.headlineLarge)
            Text(destination.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (destination in setOf(DesktopDestination.Home, DesktopDestination.Music, DesktopDestination.Videos)) {
                DesktopLibraryPanel(
                    destination = destination,
                    state = libraryState,
                    onAddFolder = library::addFolder,
                    onScan = library::scan,
                    onCancelScan = library::cancelScan,
                    onRemoveRoot = library::removeRoot,
                    onRefreshAvailability = library::refreshAvailability,
                    onSearch = library::setSearch,
                    onPlay = playMedia,
                    onToggleFavorite = library::toggleFavorite,
                    onAddToPlaylist = library::addToPlaylist,
                )
            }
            if (destination == DesktopDestination.Playlists) {
                DesktopPlaylistsPanel(
                    state = libraryState,
                    onCreate = library::createPlaylist,
                    onRename = library::renamePlaylist,
                    onDelete = library::deletePlaylist,
                    onDuplicate = library::duplicatePlaylist,
                    onRemove = library::removeFromPlaylist,
                    onMove = library::movePlaylistItem,
                    onPlay = { playlistId ->
                        scope.launch { library.playlistMedia(playlistId).onSuccess { it.firstOrNull()?.let(playMedia) } }
                    },
                )
            }
            if (destination == DesktopDestination.Favorites) {
                DesktopFavoritesPanel(libraryState.favorites, playMedia, library::toggleFavorite)
            }
            if (destination == DesktopDestination.History) {
                DesktopHistoryPanel(libraryState.history, playMedia, library::removeHistory, library::clearHistory)
            }
            if (destination == DesktopDestination.Videos || state.media?.kind == MediaKind.VIDEO) {
                SwingPanel(
                    factory = { videoCanvas },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    chooseMediaFiles().firstOrNull()?.let { file ->
                        scope.launch { engine.open(file.toMediaItem()).onSuccess { engine.play() } }
                    }
                }) { Text("Open media") }
                OutlinedButton(onClick = { if (state.status == PlaybackStatus.Playing) engine.pause() else engine.play() }) {
                    Text(if (state.status == PlaybackStatus.Playing) "Pause" else "Play")
                }
                OutlinedButton(onClick = { engine.stop() }) { Text("Stop") }
            }
            OutlinedTextField(
                value = networkUrl,
                onValueChange = { networkUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Authorized HTTP, HTTPS, or RTSP URL") },
                singleLine = true,
            )
            Button(
                enabled = networkUrl.isNotBlank(),
                onClick = {
                    val url = networkUrl.trim()
                    scope.launch { engine.open(url.toNetworkItem()).onSuccess { engine.play() } }
                },
            ) { Text("Open stream") }
            if (state.media == null) {
                DesktopEmptyState(destination)
            }
            Spacer(Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.media?.title ?: "Nothing playing", style = MaterialTheme.typography.titleLarge)
            Text(state.status.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = state.positionMillis.toFloat().coerceAtMost((state.durationMillis ?: 1).toFloat()),
                onValueChange = { engine.seekTo(it.toLong()) },
                valueRange = 0f..(state.durationMillis ?: 1).coerceAtLeast(1).toFloat(),
                enabled = state.isSeekable,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(state.positionMillis))
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.durationMillis ?: 0))
            }
            DesktopBookmarksPanel(
                media = state.media,
                positionMillis = state.positionMillis,
                bookmarks = libraryState.bookmarks,
                onAdd = library::addBookmark,
                onSeek = { engine.seekTo(it) },
                onRename = library::renameBookmark,
                onDelete = library::deleteBookmark,
            )
                }
            }
            (state.status as? PlaybackStatus.Error)?.let { error ->
                Text(error.error.message, color = MaterialTheme.colorScheme.error)
                Text(
                    "Install libmpv (for example with Homebrew) or set -Dvimivo.mpv.library=/absolute/path/to/libmpv.dylib.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}

@Composable
private fun DesktopLibraryPanel(
    destination: DesktopDestination,
    state: DesktopLibraryUiState,
    onAddFolder: () -> Unit,
    onScan: (String) -> Unit,
    onCancelScan: (String) -> Unit,
    onRemoveRoot: (String) -> Unit,
    onRefreshAvailability: () -> Unit,
    onSearch: (String) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onAddToPlaylist: (String, MediaItem) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAddFolder) { Text("Add Folder") }
        OutlinedButton(onClick = onRefreshAvailability) { Text("Refresh Availability") }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (destination == DesktopDestination.Home) {
        if (state.roots.isEmpty()) Text("No library folders configured.")
        state.roots.forEach { root ->
            val scan = state.scanStates[root.id] ?: LibraryScanState.Idle
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(root.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(root.path.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${root.mediaCount} indexed media items")
                        Text(if (root.available) scan.label() else "Unavailable")
                    }
                    if (scan is LibraryScanState.Scanning) {
                        OutlinedButton(onClick = { onCancelScan(root.id) }) { Text("Cancel") }
                    } else {
                        OutlinedButton(onClick = { onScan(root.id) }) { Text("Rescan") }
                    }
                    OutlinedButton(onClick = { onRemoveRoot(root.id) }) { Text("Remove") }
                }
            }
        }
    } else {
        OutlinedTextField(
            value = state.search,
            onValueChange = onSearch,
            label = { Text("Search library") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        val media = if (destination == DesktopDestination.Music) state.audio else state.video
        if (!state.loading && media.isEmpty()) Text(if (state.search.isBlank()) "No media found. Add and scan a folder." else "No matching media.")
        LazyColumn(Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(media, key = { it.id.value }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title)
                        Text(listOfNotNull(item.artist, item.album, item.fileName, item.sizeBytes?.let(::formatBytes)).joinToString(" · "))
                    }
                    Button(onClick = { onPlay(item) }) { Text("Play") }
                    OutlinedButton(onClick = { onToggleFavorite(item) }) { Text(if (item.isFavorite) "Unfavorite" else "Favorite") }
                    AddToPlaylistButton(state.playlists, item, onAddToPlaylist)
                    }
                }
            }
        }
    }
}

private fun LibraryScanState.label(): String = when (this) {
    LibraryScanState.Idle -> "Idle"
    is LibraryScanState.Scanning -> "Scanning · $discovered discovered"
    is LibraryScanState.Completed -> "Completed · ${result.indexed} indexed · ${result.errors} errors"
    is LibraryScanState.Failed -> "Failed · $message"
}

private fun formatBytes(bytes: Long): String = if (bytes < 1_048_576) "${bytes / 1024} KB" else "${bytes / 1_048_576} MB"

@Composable
private fun DesktopSidebar(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    Column(
        modifier = Modifier.width(184.dp).fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Vimivo", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        DesktopDestination.entries.forEach { destination ->
            OutlinedButton(
                onClick = { onDestinationSelected(destination) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (selected == destination) "• ${destination.title}" else destination.title) }
        }
        Spacer(Modifier.weight(1f))
        Text("Private, local-first playback", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DesktopEmptyState(destination: DesktopDestination) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(destination.emptyTitle, style = MaterialTheme.typography.titleLarge)
            Text(destination.emptyDetail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class DesktopDestination(
    val title: String,
    val subtitle: String,
    val emptyTitle: String,
    val emptyDetail: String,
) {
    Home("Home", "Your listening and viewing space", "Open media to begin", "Vimivo keeps your media on this device."),
    Music("Music", "Songs, albums, artists, and folders", "Your music library is empty", "Add and scan a library folder from Home."),
    Videos("Videos", "Local videos and resume points", "Your video library is empty", "Add and scan a library folder from Home, or open a video directly."),
    Playlists("Playlists", "Your local collections", "No playlists yet", "Playlist editing is not yet available in the desktop interface."),
    Favorites("Favorites", "Media you marked for quick access", "No favorites yet", "Favorite controls are not yet available in the desktop interface."),
    History("History", "Recently played and watched", "No playback history", "Played library media appears here."),
    Network("Network", "Authorized streams and remote media", "Open an authorized stream", "Paste an HTTP, HTTPS, or RTSP address above."),
    Settings("Settings", "Appearance and playback preferences", "Settings are being prepared", "Preferences will be stored locally in the desktop persistence layer."),
}

private fun chooseMediaFiles(): List<File> {
    val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    return dialog.files.orEmpty().toList()
}

private fun File.toMediaItem() = MediaItem(
    id = MediaId(toURI().toString()),
    uri = toURI().toString(),
    title = nameWithoutExtension,
    kind = if (extension.lowercase() in VIDEO_EXTENSIONS) MediaKind.VIDEO else MediaKind.AUDIO,
    fileName = name,
)

private fun String.toNetworkItem() = MediaItem(MediaId(this), this, this, MediaKind.STREAM)

private fun PlaybackStatus.label() = when (this) {
    PlaybackStatus.Idle -> "Idle"
    PlaybackStatus.Preparing -> "Preparing"
    PlaybackStatus.Buffering -> "Buffering"
    PlaybackStatus.Ready -> "Ready"
    PlaybackStatus.Playing -> "Playing"
    PlaybackStatus.Paused -> "Paused"
    PlaybackStatus.Completed -> "Completed"
    is PlaybackStatus.Error -> "Playback error"
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "m4v", "ts", "m2ts")
