@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "ImportOrdering", "Indentation",
    "LongMethod", "LongParameterList", "MagicNumber", "MaximumLineLength", "MaxLineLength", "NoConsecutiveBlankLines", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import dev.vimivo.ui.theme.VimivoTheme
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
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
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.PlaybackProfile
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.ReplayGainMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        var openStreamRequested by remember { mutableStateOf(false) }
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
                    "Open Network Stream…",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.U, ctrl = true),
                    onClick = { openStreamRequested = true },
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
                Separator()
                Item(
                    "Volume Up",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.DirectionUp, ctrl = true),
                    onClick = { engine.setVolume((engine.state.value.volume + 0.1f).coerceAtMost(1f)) },
                )
                Item(
                    "Volume Down",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.DirectionDown, ctrl = true),
                    onClick = { engine.setVolume((engine.state.value.volume - 0.1f).coerceAtLeast(0f)) },
                )
                Item(
                    "Mute / Unmute",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.M, ctrl = true),
                    onClick = {
                        val current = engine.state.value.volume
                        if (current > 0f) engine.setVolume(0f) else engine.setVolume(1f)
                    },
                )
                Separator()
                Item(
                    "Normal Speed (1.0x)",
                    onClick = { engine.setPlaybackSpeed(1.0f) },
                )
                Item(
                    "Faster Speed (+0.25x)",
                    onClick = { engine.setPlaybackSpeed((engine.state.value.speed + 0.25f).coerceAtMost(3.0f)) },
                )
                Item(
                    "Slower Speed (-0.25x)",
                    onClick = { engine.setPlaybackSpeed((engine.state.value.speed - 0.25f).coerceAtLeast(0.25f)) },
                )
            }
        }
        DesktopPlayer(
            engine,
            queue,
            library,
            args.map(::File),
            openFileHandler,
            mpris,
            openStreamRequested = openStreamRequested,
            onOpenStreamHandled = { openStreamRequested = false },
        )
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
    openStreamRequested: Boolean = false,
    onOpenStreamHandled: () -> Unit = {},
) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val queueState by queue.state.collectAsState()
    val libraryState by library.state.collectAsState()
    var networkUrl by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(DesktopDestination.Home) }
    var selectedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var activeProfile by remember { mutableStateOf(PlaybackProfile.DEFAULT) }
    var activePreset by remember { mutableStateOf(EqualizerPreset.FLAT) }
    var activeReplayGain by remember { mutableStateOf(ReplayGainMode.OFF) }
    var previousVolume by remember { mutableStateOf(1f) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerMinutesRemaining by remember { mutableStateOf<Int?>(null) }
    var diagnosticsStatus by remember { mutableStateOf<String?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showOpenStreamDialog by remember { mutableStateOf(false) }
    val streamHistoryRepository = remember { dev.vimivo.desktop.streaming.DesktopStreamHistoryRepository() }
    var lyricsDoc by remember(state.media?.uri) { mutableStateOf<dev.vimivo.core.lyrics.LyricsDocument?>(null) }

    LaunchedEffect(openStreamRequested) {
        if (openStreamRequested) {
            showOpenStreamDialog = true
            onOpenStreamHandled()
        }
    }

    LaunchedEffect(state.media?.uri) {
        val uri = state.media?.uri
        if (uri != null && uri.startsWith("file:")) {
            runCatching {
                val mediaFile = File(java.net.URI(uri))
                val base = mediaFile.nameWithoutExtension
                val candidate = mediaFile.parentFile?.listFiles()?.firstOrNull { file ->
                    file.isFile && file.extension.equals("lrc", true) &&
                        (file.nameWithoutExtension.equals(base, true) || file.nameWithoutExtension.startsWith("$base.", true))
                }
                if (candidate != null && candidate.canRead()) {
                    lyricsDoc = dev.vimivo.core.lyrics.LrcLyricsParser.parse(candidate.readText(Charsets.UTF_8), candidate.toURI().toString())
                } else {
                    lyricsDoc = null
                }
            }.onFailure { lyricsDoc = null }
        } else {
            lyricsDoc = null
        }
    }

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
                val isVeryCompact = maxWidth < 640.dp
                val isCompact = maxWidth < 840.dp
                val sidebarWidth = if (isVeryCompact) 110.dp else if (isCompact) 160.dp else 220.dp
                val contentPadding = if (isVeryCompact) 10.dp else if (isCompact) 16.dp else 24.dp
                val contentScrollState = rememberScrollState()
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
                        Modifier.fillMaxSize().verticalScroll(contentScrollState).padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(destination.title, style = MaterialTheme.typography.headlineLarge)
                        Text(destination.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (destination == DesktopDestination.Home) {
                DesktopPersonalizedHomePanel(
                    state = libraryState,
                    onPlay = playMedia,
                    onDismissRecommendation = library::dismissRecommendation,
                    onTogglePersonalization = library::setPersonalizationEnabled,
                )
            }
            if (destination in setOf(DesktopDestination.Music, DesktopDestination.Videos)) {
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
                    onExportM3u = { playlistId, file -> library.exportPlaylistM3u(playlistId, file) },
                    onImportM3u = { file -> library.importPlaylistM3u(file) },
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
            if (destination == DesktopDestination.SmartPlaylists) {
                DesktopSmartPlaylistsPanel(
                    state = libraryState,
                    controller = library,
                    onPlay = playMedia,
                )
            }
            if (destination == DesktopDestination.Duplicates) {
                DesktopDuplicatesPanel(
                    state = libraryState,
                    controller = library,
                    onPlay = playMedia,
                )
            }
            if (destination == DesktopDestination.Insights) {
                DesktopInsightsPanel(state = libraryState)
            }
            if (destination == DesktopDestination.MetadataEditor) {
                DesktopMetadataEditorPanel(state = libraryState, controller = library)
            }
            if (destination == DesktopDestination.BatchRename) {
                DesktopBatchRenamePanel(state = libraryState, controller = library)
            }
            if (destination == DesktopDestination.MissingMedia) {
                DesktopMissingMediaPanel(state = libraryState, controller = library)
            }
            if (destination == DesktopDestination.Automation) {
                DesktopAutomationHealthPanel(state = libraryState, controller = library)
            }
            if (destination == DesktopDestination.Network) {
                dev.vimivo.desktop.streaming.DesktopNetworkStreamPanel(
                    onOpenStreamDialog = { showOpenStreamDialog = true },
                    onPlayStream = { mediaItem ->
                        scope.launch { queue.play(listOf(mediaItem)) }
                    },
                    onAddToQueue = { mediaItem ->
                        queue.addToQueue(listOf(mediaItem))
                    },
                    historyRepository = streamHistoryRepository,
                )
            }
            if (destination == DesktopDestination.Settings) {
                DesktopSettingsPanel(
                    state = libraryState,
                    onThemeSelected = library::setThemeMode,
                    onAddFolder = library::addFolder,
                    onRefreshAvailability = library::refreshAvailability,
                    onPlaybackProfileSelected = { profile ->
                        activeProfile = profile
                        activePreset = profile.equalizerPreset
                        activeReplayGain = profile.replayGainMode
                        profile.applyTo(engine)
                    },
                    onEqualizerPresetSelected = { preset ->
                        activePreset = preset
                        engine.applyAudioEnhancements(
                            AudioEnhancementSettings(
                                enabled = preset != EqualizerPreset.FLAT || activeReplayGain != ReplayGainMode.OFF,
                                preset = preset,
                                replayGainMode = activeReplayGain,
                            ),
                        )
                    },
                    onReplayGainModeSelected = { mode ->
                        activeReplayGain = mode
                        engine.applyAudioEnhancements(
                            AudioEnhancementSettings(
                                enabled = activePreset != EqualizerPreset.FLAT || mode != ReplayGainMode.OFF,
                                preset = activePreset,
                                replayGainMode = mode,
                            ),
                        )
                    },
                    onSleepTimerSet = { mins ->
                        sleepTimerJob?.cancel()
                        if (mins == null) {
                            sleepTimerMinutesRemaining = null
                            sleepTimerJob = null
                        } else {
                            sleepTimerMinutesRemaining = mins
                            sleepTimerJob = scope.launch {
                                var rem = mins
                                while (rem > 0) {
                                    delay(60_000L)
                                    rem--
                                    sleepTimerMinutesRemaining = rem
                                }
                                engine.pause()
                                sleepTimerMinutesRemaining = null
                            }
                        }
                    },
                    onExportDiagnostics = {
                        val exporter = dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter()
                        val userHome = System.getProperty("user.home")
                        val desktop = File(userHome, "Desktop").takeIf { it.isDirectory } ?: File(userHome)
                        val target = File(desktop, "vimivo_diagnostics_${System.currentTimeMillis()}.json")
                        exporter.exportToPath(target.toPath()).onSuccess {
                            diagnosticsStatus = "Diagnostics exported to Desktop: ${target.name}"
                        }.onFailure {
                            diagnosticsStatus = "Export failed: ${it.message}"
                        }
                    },
                    onCheckForUpdates = {
                        val updateService = dev.vimivo.desktop.update.DesktopUpdateService()
                        scope.launch {
                            updateStatus = "Checking for updates..."
                            updateService.checkFromUrl().onSuccess { result ->
                                updateStatus = when (result) {
                                    is dev.vimivo.core.update.UpdateCheckResult.UpdateAvailable -> "Update available: v${result.newVersion}"
                                    is dev.vimivo.core.update.UpdateCheckResult.UpToDate -> "Vimivo is up to date (v${result.currentVersion})"
                                    is dev.vimivo.core.update.UpdateCheckResult.DowngradeBlocked -> "Version is newer than release manifest"
                                    is dev.vimivo.core.update.UpdateCheckResult.ArchitectureMismatch -> "No compatible package for ${result.currentPlatform} ${result.currentArchitecture}"
                                    is dev.vimivo.core.update.UpdateCheckResult.IncompatibleDatabaseVersion -> "Database migration required: ${result.reason}"
                                    is dev.vimivo.core.update.UpdateCheckResult.SignatureVerificationFailed -> "Update signature verification failed: ${result.reason}"
                                    is dev.vimivo.core.update.UpdateCheckResult.Error -> "Check failed: ${result.reason}"
                                }
                            }.onFailure {
                                updateStatus = "Could not reach update server (offline or local-only network)"
                            }
                        }
                    },
                    activeProfile = activeProfile,
                    activePreset = activePreset,
                    activeReplayGain = activeReplayGain,
                    sleepTimerMinutesRemaining = sleepTimerMinutesRemaining,
                    diagnosticsStatus = diagnosticsStatus,
                    updateStatus = updateStatus,
                    onOpenFeedback = { showFeedbackDialog = true },
                )
            }
            if (destination == DesktopDestination.Videos || state.media?.kind == MediaKind.VIDEO) {
                SwingPanel(
                    factory = { videoCanvas },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
                dev.vimivo.desktop.playback.DesktopVideoControlsPanel(
                    engine = engine,
                    state = state,
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val isMuted = state.volume == 0f
                OutlinedButton(
                    onClick = {
                        if (isMuted) {
                            engine.setVolume(if (previousVolume > 0f) previousVolume else 1f)
                        } else {
                            previousVolume = state.volume
                            engine.setVolume(0f)
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (isMuted) "Unmute audio" else "Mute audio"
                    },
                ) {
                    Text(if (isMuted) "🔇 Muted" else "🔊 ${(state.volume * 100).toInt()}%")
                }
                Slider(
                    value = state.volume,
                    onValueChange = { engine.setVolume(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(110.dp).semantics {
                        contentDescription = "Volume level"
                        stateDescription = "${(state.volume * 100).toInt()}%"
                    },
                )
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                OutlinedButton(
                    onClick = {
                        val nextIndex = (speeds.indexOfFirst { kotlin.math.abs(it - state.speed) < 0.05f } + 1) % speeds.size
                        engine.setPlaybackSpeed(speeds[nextIndex])
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Playback speed ${state.speed}x. Click to change."
                    },
                ) {
                    Text("${state.speed}x")
                }
                OutlinedButton(
                    onClick = { showDiagnostics = !showDiagnostics },
                    modifier = Modifier.semantics {
                        contentDescription = if (showDiagnostics) "Hide technical diagnostics" else "Show technical diagnostics"
                    },
                ) {
                    Text(if (showDiagnostics) "Hide Stats" else "Stats")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = networkUrl,
                    onValueChange = { networkUrl = it },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Authorized HTTP, HTTPS, or RTSP streaming address" },
                    label = { Text("Authorized HTTP, HTTPS, or RTSP URL") },
                    singleLine = true,
                )
                Button(
                    enabled = networkUrl.isNotBlank(),
                    onClick = {
                        val url = networkUrl.trim()
                        when (val validation = dev.vimivo.core.streaming.StreamValidator.validate(url)) {
                            is dev.vimivo.core.streaming.StreamValidationResult.Valid -> {
                                val item = dev.vimivo.core.streaming.StreamValidator.toMediaItem(validation)
                                streamHistoryRepository.recordStream(validation.normalizedUrl)
                                networkUrl = ""
                                scope.launch { queue.play(listOf(item)) }
                            }
                            is dev.vimivo.core.streaming.StreamValidationResult.Invalid -> {
                                showOpenStreamDialog = true
                            }
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Open stream URL" },
                ) { Text("Open stream") }
                OutlinedButton(
                    onClick = { showOpenStreamDialog = true },
                    modifier = Modifier.semantics { contentDescription = "Open network stream dialog" },
                ) { Text("Stream…") }
            }
            if (state.media == null && destination !in setOf(DesktopDestination.Settings, DesktopDestination.Network)) {
                DesktopEmptyState(destination)
            }
            Spacer(Modifier.height(4.dp))
            VimivoGlassSurface(
                variant = VimivoGlassVariant.FLOATING_PLAYER,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Now playing card for ${state.media?.title ?: "Nothing playing"}"
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.media?.let { DesktopArtwork(it.artworkUri, it.title, 96.dp) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.media?.title ?: "Nothing playing", style = MaterialTheme.typography.titleLarge)
                        if (state.media?.kind == MediaKind.STREAM && state.durationMillis == null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        if (state.status == PlaybackStatus.Buffering) "Buffering stream..." else state.status.label(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = state.positionMillis.toFloat().coerceAtMost((state.durationMillis ?: 1).toFloat()),
                        onValueChange = { engine.seekTo(it.toLong()) },
                        valueRange = 0f..(state.durationMillis ?: 1).coerceAtLeast(1).toFloat(),
                        enabled = state.isSeekable && state.durationMillis != null,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = if (state.durationMillis == null) {
                                "Live stream: ${formatTime(state.positionMillis)}"
                            } else {
                                "Playback seekbar: ${formatTime(state.positionMillis)} of ${formatTime(state.durationMillis ?: 0)}"
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(state.positionMillis))
                        Spacer(Modifier.weight(1f))
                        if (state.media?.kind == MediaKind.AUDIO) {
                            OutlinedButton(onClick = { showLyrics = !showLyrics }) {
                                Text(if (showLyrics) "Hide Lyrics" else "Lyrics")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.durationMillis == null && state.media?.kind == MediaKind.STREAM) "Live broadcast" else formatTime(state.durationMillis ?: 0))
                    }
                    if (showLyrics && state.media?.kind == MediaKind.AUDIO) {
                        DesktopLyricsCard(lyricsDoc = lyricsDoc, positionMillis = state.positionMillis)
                    }
                }
            }
            if (showDiagnostics) {
                val diag = engine.getDiagnosticsSnapshot()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Technical playback diagnostics" },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Technical Playback Diagnostics", style = MaterialTheme.typography.titleSmall)
                        if (diag != null) {
                            Text(
                                "Video: ${diag.activeVideoCodec ?: "none"} · Audio: ${diag.activeAudioCodec ?: "none"} · HWDEC: ${diag.activeDecoder ?: "none"} (${if (diag.hwdecActive) "Active" else "Inactive"})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "VO: ${diag.activeRenderer ?: "none"} · FPS: ${diag.estimatedFps} · Dropped Frames: ${diag.droppedFrames} / ${diag.totalFrames}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "A/V Sync Drift: ${diag.avDriftMillis.toLong()} ms · Cache: ${formatBytes(diag.cacheBufferBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Diagnostics available during active playback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                OutlinedButton(onClick = { showFeedbackDialog = true }) {
                    Text("Report Playback Issue")
                }
            }
                    }
                }
            }
        }
    }

    if (showFeedbackDialog) {
        dev.vimivo.desktop.feedback.DesktopFeedbackDialog(
            onDismissRequest = { showFeedbackDialog = false },
            diagnosticReport = remember { dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter().createReport() },
            initialPlaybackContext = (state.status as? PlaybackStatus.Error)?.let { err ->
                dev.vimivo.core.feedback.MediaPlaybackContext(
                    playbackEngine = state.engine?.name,
                    errorMessage = err.error.message,
                )
            },
        )
    }

    if (showOpenStreamDialog) {
        dev.vimivo.desktop.streaming.DesktopOpenStreamDialog(
            onDismissRequest = { showOpenStreamDialog = false },
            onPlayStream = { mediaItem ->
                scope.launch { queue.play(listOf(mediaItem)) }
            },
            onAddToQueue = { mediaItem ->
                queue.addToQueue(listOf(mediaItem))
            },
            onSetLiveLatencyMode = { mode ->
                engine.setLiveLatencyMode(mode)
            },
            historyRepository = streamHistoryRepository,
        )
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
                        Button(
                            onClick = { onPlay(item) },
                            modifier = Modifier.semantics { contentDescription = "Play ${item.title}" },
                        ) { Text("Play") }
                        OutlinedButton(
                            onClick = { onPlayNext(item) },
                            modifier = Modifier.semantics { contentDescription = "Play ${item.title} next" },
                        ) { Text("Next") }
                        OutlinedButton(
                            onClick = { onAddToQueue(item) },
                            modifier = Modifier.semantics { contentDescription = "Add ${item.title} to queue" },
                        ) { Text("+ Queue") }
                        OutlinedButton(
                            onClick = { onToggleFavorite(item) },
                            modifier = Modifier.semantics {
                                contentDescription = if (item.isFavorite) "Remove ${item.title} from favorites" else "Add ${item.title} to favorites"
                            },
                        ) { Text(if (item.isFavorite) "★" else "☆") }
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
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
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
    Home("Home", "Personalized offline dashboard", "Open media to begin", "Vimivo keeps your media on this device."),
    Music("Music", "Songs and tracks in your library", "Your music library is empty", "Add and scan a library folder from Home."),
    Albums("Albums", "Browse albums and complete discographies", "No albums yet", "Add and scan music folders with album metadata."),
    Artists("Artists", "Musicians, bands, and composers", "No artists yet", "Add and scan music folders with artist metadata."),
    Videos("Videos", "Local videos and resume points", "Your video library is empty", "Add and scan a library folder from Home, or open a video directly."),
    Playlists("Playlists", "Your local collections", "No playlists yet", "Create a playlist to organize your media."),
    SmartPlaylists("Smart Playlists", "Dynamic rule-based collections", "No smart playlists yet", "Create a rule-based smart collection."),
    Duplicates("Duplicates", "Safe duplicate review", "No duplicate groups found", "Scan your library to find duplicate content clusters."),
    Insights("Insights", "Private listening and watching statistics", "No playback history", "Insights appear as you play media offline."),
    MetadataEditor("Metadata", "Overrides and embedded tag editing", "No media selected", "Select items to view or edit metadata."),
    BatchRename("Rename", "Pattern-based file renaming", "No files ready for renaming", "Select a pattern to preview renames."),
    MissingMedia("Relink", "Reconcile moved or missing files", "All library media is available", "No missing files detected."),
    Automation("Automation", "Maintenance jobs and health report", "Library is healthy", "Run maintenance jobs or configure rules."),
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

@Composable
private fun DesktopLyricsCard(lyricsDoc: dev.vimivo.core.lyrics.LyricsDocument?, positionMillis: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Lyrics", style = MaterialTheme.typography.titleSmall)
            if (lyricsDoc == null || lyricsDoc.lines.isEmpty()) {
                Text(
                    "No synchronized .lrc lyrics found for this track. Place a .lrc file in the same directory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val activeIdx = lyricsDoc.findActiveLineIndex(positionMillis)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(lyricsDoc.lines.size) { idx ->
                        val line = lyricsDoc.lines[idx]
                        val isActive = idx == activeIdx
                        Text(
                            text = line.text,
                            style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
