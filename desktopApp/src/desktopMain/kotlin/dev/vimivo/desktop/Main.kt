@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "ImportOrdering", "Indentation",
    "LongMethod", "LongParameterList", "MagicNumber", "MaximumLineLength", "MaxLineLength", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import dev.vimivo.ui.theme.VimivoTheme
import dev.vimivo.core.model.AlbumItem
import dev.vimivo.core.model.ArtistItem
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.platform.MacOpenFileHandler
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.desktop.library.LibraryScanState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlaybackQueueController
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main(args: Array<String>) = application {
    val engine = remember { MpvPlayerEngine() }
    val openFileHandler = remember { MacOpenFileHandler().also { it.install() } }
    val applicationScope = rememberCoroutineScope()
    val library = remember { DesktopLibraryController() }
    val queue = remember(engine, applicationScope) {
        PlaybackQueueController(engine, applicationScope, isAvailable = { media ->
            media.isAvailable && (!media.uri.startsWith("file:") || runCatching { File(java.net.URI(media.uri)).isFile }.getOrDefault(false))
        })
    }
    val mpris = remember(engine, queue) {
        dev.vimivo.desktop.integration.mpris.MprisServiceFactory.create(
            engine,
            queue,
            { togglePlayback(engine, queue, library) },
        )
    }
    Window(
        onCloseRequest = {
            mpris.close()
            queue.close()
            engine.close()
            openFileHandler.close()
            exitApplication()
        },
        title = "Vimivo",
    ) {
        LaunchedEffect(window) {
            window.dropTarget = java.awt.dnd.DropTarget(window, object : java.awt.dnd.DropTargetAdapter() {
                override fun drop(dtde: java.awt.dnd.DropTargetDropEvent) {
                    runCatching {
                        dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                        val transferable = dtde.transferable
                        if (transferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                            val dropped = transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as? List<*>
                            val files = dropped?.filterIsInstance<File>() ?: emptyList()
                            if (files.isNotEmpty()) {
                                openFileHandler.submit(files)
                            }
                        }
                    }
                }
            })
        }
        MenuBar {
            Menu("File") {
                Item(
                    "Open File…",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.O, ctrl = true),
                    onClick = { chooseMediaFiles().let(openFileHandler::submit) },
                )
                Item(
                    "Add Folder…",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(
                        androidx.compose.ui.input.key.Key.O,
                        ctrl = true,
                        shift = true,
                    ),
                    onClick = { library.addFolder() },
                )
            }
            Menu("Playback") {
                Item(
                    "Play / Pause",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.Spacebar),
                    onClick = { applicationScope.launch { togglePlayback(engine, queue, library) } },
                )
                Item("Stop", onClick = { engine.stop() })
                Item("Next", onClick = { applicationScope.launch { queue.next() } })
                Item("Previous", onClick = { applicationScope.launch { queue.previous() } })
                Separator()
                Item(
                    "Seek Backward",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.DirectionLeft),
                    onClick = { engine.seekTo((engine.state.value.positionMillis - 10_000).coerceAtLeast(0)) },
                )
                Item(
                    "Seek Forward",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.DirectionRight),
                    onClick = { engine.seekTo(engine.state.value.positionMillis + 10_000) },
                )
            }
        }
        DesktopPlayer(engine, queue, library, args.map(::File), openFileHandler, mpris)
    }
}

/** Pauses active playback, resumes loaded media, or opens a restored queue entry only after a user action. */
internal suspend fun togglePlayback(
    engine: MpvPlayerEngine,
    queue: PlaybackQueueController,
    library: DesktopLibraryController,
) {
    val playback = engine.state.value
    when {
        playback.status == PlaybackStatus.Playing -> engine.pause()
        playback.media != null -> engine.play()
        else -> queue.state.value.currentMedia?.let { media ->
            val start = library.preparePlayback(media).getOrNull()?.startPositionMillis ?: 0
            queue.playCurrent(start)
        }
    }
}

@Composable
private fun DesktopPlayer(
    engine: MpvPlayerEngine,
    queue: PlaybackQueueController,
    library: DesktopLibraryController,
    initialFiles: List<File>,
    openFileHandler: MacOpenFileHandler,
    mpris: dev.vimivo.desktop.integration.mpris.MprisService,
) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val queueState by queue.state.collectAsState()
    val libraryState by library.state.collectAsState()
    var networkUrl by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(DesktopDestination.Home) }
    var selectedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistItem?>(null) }
    val videoCanvas = remember { Canvas() }
    val playMedia: (MediaItem) -> Unit = { media ->
        scope.launch {
            library.preparePlayback(media).onSuccess { request ->
                queue.play(listOf(request.media), startPositionMillis = request.startPositionMillis)
            }
        }
    }
    DisposableEffect(engine) { onDispose(engine::close) }
    DisposableEffect(queue) { onDispose(queue::close) }
    DisposableEffect(library) { onDispose(library::close) }
    DisposableEffect(videoCanvas) {
        engine.attachVideoSurface(videoCanvas)
        onDispose { }
    }
    LaunchedEffect(initialFiles, openFileHandler, library, queue) {
        // Restore first so explicitly opened files replace, rather than race with, the saved paused queue.
        library.restoredQueue()?.let { snapshot -> runCatching { queue.restore(snapshot) } }
        launch {
            queue.state.drop(1).collect { library.saveQueue(queue.snapshot()) }
        }
        initialFiles.filter(File::isFile).takeIf(List<File>::isNotEmpty)?.let { files ->
            queue.play(files.map(File::toMediaItem))
        }
        openFileHandler.files.collect { files ->
            val valid = files.filter(File::isFile).map(File::toMediaItem)
            if (valid.isNotEmpty()) {
                queue.play(valid)
            }
        }
    }
    LaunchedEffect(engine, library) {
        library.observePlayback(engine.state)
    }
    LaunchedEffect(engine, mpris) {
        engine.state.collect { mpris.updatePlayback(it) }
    }
    LaunchedEffect(queue, mpris) {
        queue.state.collect { mpris.updateQueue(it) }
    }

    VimivoTheme(mode = libraryState.themeMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 820.dp
                val sidebarWidth = if (isCompact) 140.dp else 192.dp
                val contentPadding = if (isCompact) 16.dp else 28.dp
                Row(Modifier.fillMaxSize()) {
                    DesktopSidebar(
                        destination,
                        onDestinationSelected = {
                            destination = it
                            selectedAlbum = null
                            selectedArtist = null
                        },
                        modifier = Modifier.width(sidebarWidth),
                    )
                    Column(
                        Modifier.fillMaxSize().padding(contentPadding),
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
                    onPlayNext = { queue.playNext(listOf(it)) },
                    onAddToQueue = { queue.addToQueue(listOf(it)) },
                    onToggleFavorite = library::toggleFavorite,
                    onAddToPlaylist = library::addToPlaylist,
                )
            }
            if (destination == DesktopDestination.Albums) {
                if (selectedAlbum != null) {
                    val details = remember(selectedAlbum, libraryState) { library.albumDetails(selectedAlbum!!.id) }
                    if (details != null) {
                        DesktopAlbumDetailsPanel(
                            details = details,
                            playlists = libraryState.playlists,
                            onBack = { selectedAlbum = null },
                            onPlayAll = { tracks, title -> scope.launch { queue.play(tracks, sourceLabel = title) } },
                            onShufflePlay = { tracks, title -> scope.launch { queue.play(tracks, shuffle = true, sourceLabel = title) } },
                            onPlayNext = { tracks -> queue.playNext(tracks) },
                            onAddToQueue = { tracks -> queue.addToQueue(tracks) },
                            onPlayTrack = playMedia,
                            onToggleFavorite = library::toggleFavorite,
                            onAddToPlaylist = library::addToPlaylist,
                        )
                    } else {
                        selectedAlbum = null
                    }
                } else {
                    DesktopAlbumsPanel(
                        albums = libraryState.albums,
                        onSelectAlbum = { selectedAlbum = it },
                        onPlayAll = { tracks, title -> scope.launch { queue.play(tracks, sourceLabel = title) } },
                        onShufflePlay = { tracks, title -> scope.launch { queue.play(tracks, shuffle = true, sourceLabel = title) } },
                        onAddToQueue = { tracks -> queue.addToQueue(tracks) },
                        loadTracks = { id -> library.albumDetails(id)?.tracks.orEmpty() },
                    )
                }
            }
            if (destination == DesktopDestination.Artists) {
                if (selectedArtist != null) {
                    val details = remember(selectedArtist, libraryState) { library.artistDetails(selectedArtist!!.id) }
                    if (details != null) {
                        DesktopArtistDetailsPanel(
                            details = details,
                            playlists = libraryState.playlists,
                            onBack = { selectedArtist = null },
                            onSelectAlbum = { album ->
                                selectedAlbum = album
                                destination = DesktopDestination.Albums
                            },
                            onPlayAll = { tracks, title -> scope.launch { queue.play(tracks, sourceLabel = title) } },
                            onShufflePlay = { tracks, title -> scope.launch { queue.play(tracks, shuffle = true, sourceLabel = title) } },
                            onPlayNext = { tracks -> queue.playNext(tracks) },
                            onAddToQueue = { tracks -> queue.addToQueue(tracks) },
                            onPlayTrack = playMedia,
                            onToggleFavorite = library::toggleFavorite,
                            onAddToPlaylist = library::addToPlaylist,
                        )
                    } else {
                        selectedArtist = null
                    }
                } else {
                    DesktopArtistsPanel(
                        artists = libraryState.artists,
                        onSelectArtist = { selectedArtist = it },
                        onPlayAll = { tracks, title -> scope.launch { queue.play(tracks, sourceLabel = title) } },
                        onShufflePlay = { tracks, title -> scope.launch { queue.play(tracks, shuffle = true, sourceLabel = title) } },
                        onAddToQueue = { tracks -> queue.addToQueue(tracks) },
                        loadTracks = { id -> library.artistDetails(id)?.tracks.orEmpty() },
                    )
                }
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
                        scope.launch {
                            library.playlistMedia(playlistId).onSuccess { items ->
                                items.firstOrNull()?.let { first ->
                                    library.preparePlayback(first).onSuccess { request ->
                                        queue.play(items, startPositionMillis = request.startPositionMillis)
                                    }
                                }
                            }
                        }
                    },
                    onShufflePlay = { playlistId ->
                        scope.launch {
                            library.playlistMedia(playlistId).onSuccess { items ->
                                if (items.isNotEmpty()) queue.play(items, shuffle = true)
                            }
                        }
                    },
                    onPlayNext = { playlistId ->
                        scope.launch {
                            library.playlistMedia(playlistId).onSuccess { items ->
                                queue.playNext(items)
                            }
                        }
                    },
                    onAddToQueue = { playlistId ->
                        scope.launch {
                            library.playlistMedia(playlistId).onSuccess { items ->
                                queue.addToQueue(items)
                            }
                        }
                    },
                    onPlayMedia = playMedia,
                    onPlayMediaNext = { queue.playNext(listOf(it)) },
                    onAddMediaToQueue = { queue.addToQueue(listOf(it)) },
                )
            }
            if (destination == DesktopDestination.Favorites) {
                DesktopFavoritesPanel(
                    items = libraryState.favorites,
                    onPlay = playMedia,
                    onPlayNext = { queue.playNext(listOf(it)) },
                    onAddToQueue = { queue.addToQueue(listOf(it)) },
                    onToggle = library::toggleFavorite,
                )
            }
            if (destination == DesktopDestination.History) {
                DesktopHistoryPanel(
                    items = libraryState.history,
                    onPlay = playMedia,
                    onPlayNext = { queue.playNext(listOf(it)) },
                    onAddToQueue = { queue.addToQueue(listOf(it)) },
                    onRemove = library::removeHistory,
                    onClear = library::clearHistory,
                )
            }
            if (destination == DesktopDestination.Settings) {
                DesktopSettingsPanel(
                    state = libraryState,
                    onThemeSelected = library::setThemeMode,
                    onAddFolder = library::addFolder,
                    onRefreshAvailability = library::refreshAvailability,
                )
            }
            if (destination == DesktopDestination.Videos || state.media?.kind == MediaKind.VIDEO) {
                SwingPanel(
                    factory = { videoCanvas },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        chooseMediaFiles().firstOrNull()?.let { file ->
                            scope.launch { queue.play(listOf(file.toMediaItem())) }
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Open local media files" },
                ) { Text("Open media") }
                OutlinedButton(
                    onClick = { scope.launch { togglePlayback(engine, queue, library) } },
                    modifier = Modifier.semantics {
                        contentDescription = if (state.status == PlaybackStatus.Playing) "Pause playback" else "Start playback"
                    },
                ) {
                    Text(if (state.status == PlaybackStatus.Playing) "Pause" else "Play")
                }
                OutlinedButton(
                    onClick = { scope.launch { queue.previous() } },
                    enabled = queueState.items.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "Previous track in queue" },
                ) { Text("Previous") }
                OutlinedButton(
                    onClick = { engine.stop() },
                    modifier = Modifier.semantics { contentDescription = "Stop playback" },
                ) { Text("Stop") }
                OutlinedButton(
                    onClick = { scope.launch { queue.next() } },
                    enabled = queueState.items.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "Next track in queue" },
                ) { Text("Next") }
                OutlinedButton(
                    onClick = queue::toggleShuffle,
                    enabled = queueState.items.size > 1,
                    modifier = Modifier.semantics {
                        contentDescription = if (queueState.shuffleEnabled) "Shuffle enabled. Click to disable." else "Shuffle disabled. Click to enable."
                    },
                ) {
                    Text(if (queueState.shuffleEnabled) "Shuffle on" else "Shuffle off")
                }
                OutlinedButton(
                    onClick = queue::cycleRepeat,
                    enabled = queueState.items.isNotEmpty(),
                    modifier = Modifier.semantics {
                        contentDescription = "Cycle repeat mode: currently ${queueState.repeatMode.name.lowercase()}"
                    },
                ) {
                    Text("Repeat ${queueState.repeatMode.name.lowercase()}")
                }
            }
            OutlinedTextField(
                value = networkUrl,
                onValueChange = { networkUrl = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Authorized HTTP, HTTPS, or RTSP streaming address" },
                label = { Text("Authorized HTTP, HTTPS, or RTSP URL") },
                singleLine = true,
            )
            Button(
                enabled = networkUrl.isNotBlank(),
                onClick = {
                    val url = networkUrl.trim()
                    scope.launch { queue.play(listOf(url.toNetworkItem())) }
                },
                modifier = Modifier.semantics { contentDescription = "Open stream URL" },
            ) { Text("Open stream") }
            if (state.media == null && destination != DesktopDestination.Settings) {
                DesktopEmptyState(destination)
            }
            Spacer(Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Now playing card for ${state.media?.title ?: "Nothing playing"}"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.media?.let { DesktopArtwork(it.artworkUri, it.title, 96.dp) }
                    Text(state.media?.title ?: "Nothing playing", style = MaterialTheme.typography.titleLarge)
                    Text(state.status.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = state.positionMillis.toFloat().coerceAtMost((state.durationMillis ?: 1).toFloat()),
                        onValueChange = { engine.seekTo(it.toLong()) },
                        valueRange = 0f..(state.durationMillis ?: 1).coerceAtLeast(1).toFloat(),
                        enabled = state.isSeekable,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Playback seekbar: ${formatTime(state.positionMillis)} of ${formatTime(state.durationMillis ?: 0)}"
                        },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(state.positionMillis))
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(state.durationMillis ?: 0))
                    }
                }
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
            DesktopQueuePanel(
                state = queueState,
                onRemove = queue::remove,
                onMove = queue::move,
                onClear = queue::clear,
                onPlayAt = { index -> scope.launch { queue.playAt(index) } },
            )
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
    onPlayNext: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
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
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopArtwork(item.artworkUri, item.title, if (destination == DesktopDestination.Videos) 80.dp else 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    item.artist,
                                    item.album,
                                    if (item.width != null && item.height != null) "${item.width}x${item.height}" else null,
                                    item.durationMillis?.let(::formatTime),
                                    item.fileName,
                                    item.sizeBytes?.let(::formatBytes),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onPlay(item) }) { Text("Play") }
                        OutlinedButton(onClick = { onPlayNext(item) }) { Text("Next") }
                        OutlinedButton(onClick = { onAddToQueue(item) }) { Text("+ Queue") }
                        OutlinedButton(onClick = { onToggleFavorite(item) }) { Text(if (item.isFavorite) "★" else "☆") }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Vimivo", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        DesktopDestination.entries.forEach { destination ->
            val isSelected = selected == destination
            if (isSelected) {
                Button(
                    onClick = { onDestinationSelected(destination) },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "${destination.title} navigation tab, active"
                    },
                ) { Text(destination.title) }
            } else {
                OutlinedButton(
                    onClick = { onDestinationSelected(destination) },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Navigate to ${destination.title}"
                    },
                ) { Text(destination.title) }
            }
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
    Music("Music", "Songs and tracks in your library", "Your music library is empty", "Add and scan a library folder from Home."),
    Albums("Albums", "Browse albums and complete discographies", "No albums yet", "Add and scan music folders with album metadata."),
    Artists("Artists", "Musicians, bands, and composers", "No artists yet", "Add and scan music folders with artist metadata."),
    Videos("Videos", "Local videos and resume points", "Your video library is empty", "Add and scan a library folder from Home, or open a video directly."),
    Playlists("Playlists", "Your local collections", "No playlists yet", "Create a playlist to organize your media."),
    Favorites("Favorites", "Media you marked for quick access", "No favorites yet", "Favorite controls are available on media cards."),
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
