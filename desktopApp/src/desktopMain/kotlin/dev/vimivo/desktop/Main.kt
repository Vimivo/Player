@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "ImportOrdering", "Indentation",
    "LongMethod", "LongParameterList", "MagicNumber", "MaximumLineLength", "MaxLineLength", "NoConsecutiveBlankLines", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.vimivo.desktop.diagnostics.DesktopSmokeResult
import dev.vimivo.desktop.diagnostics.DesktopSmokeTest
import dev.vimivo.desktop.diagnostics.DesktopSmokeTestRequest
import dev.vimivo.desktop.library.DesktopDatabase
import javax.imageio.ImageIO
import dev.vimivo.ui.theme.VimivoTheme
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
import dev.vimivo.desktop.ui.DesktopDestination
import dev.vimivo.desktop.ui.DesktopSidebarView
import dev.vimivo.desktop.ui.DesktopTopHeaderView
import dev.vimivo.desktop.ui.DesktopPlaybackErrorCard
import dev.vimivo.desktop.ui.DesktopHomeScreenView
import dev.vimivo.desktop.ui.DesktopPlaybackBarView
import dev.vimivo.ui.theme.LocalReducedMotion
import dev.vimivo.ui.theme.VimivoMotion
import dev.vimivo.ui.theme.VimivoShell
import dev.vimivo.core.model.AlbumItem
import dev.vimivo.core.model.ArtistItem
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.DesktopPlayerShortcuts
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.platform.DesktopPlatform
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
import kotlin.system.exitProcess

fun main(args: Array<String>) = application {
    val smokeTest = remember { DesktopSmokeTest.parse(args) }
    val engine = remember { MpvPlayerEngine() }
    val openFileHandler = remember { MacOpenFileHandler().also { it.install() } }
    val applicationScope = rememberCoroutineScope()
    val library = remember {
        if (smokeTest != null) DesktopLibraryController(DesktopDatabase.inMemory()) else DesktopLibraryController()
    }
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
    val playbackState by engine.state.collectAsState()
    val windowTitle = remember(playbackState.media?.title, playbackState.media?.artist) {
        val media = playbackState.media
        if (media != null && media.title.isNotBlank()) {
            if (!media.artist.isNullOrBlank()) {
                "${media.title} — ${media.artist} — Vimivo"
            } else {
                "${media.title} — Vimivo"
            }
        } else {
            "Vimivo"
        }
    }
    val windowIcon = remember {
        runCatching {
            DesktopDestination::class.java.classLoader.getResourceAsStream("icon.png")?.use { stream ->
                ImageIO.read(stream)?.toComposeImageBitmap()?.let(::BitmapPainter)
            }
        }.getOrNull()
    }
    val windowState = rememberWindowState()
    val recentMedia = remember {
        dev.vimivo.desktop.library.DesktopRecentMediaRepository(persistent = smokeTest == null)
    }
    val recentEntries by recentMedia.entries.collectAsState()
    val toggleFullscreen: () -> Unit = {
        windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }
    val commands = remember(engine, queue) {
        DesktopPlayerCommandExecutor(
            engine = engine,
            togglePlayback = { applicationScope.launch { togglePlayback(engine, queue, library) } },
            toggleShuffle = queue::toggleShuffle,
            cycleRepeat = queue::cycleRepeat,
            fullscreen = object : dev.vimivo.desktop.playback.DesktopFullscreenControl {
                override fun toggle() = toggleFullscreen()
                override fun exit(): Boolean = (windowState.placement == WindowPlacement.Fullscreen).also { wasFullscreen ->
                    if (wasFullscreen) windowState.placement = WindowPlacement.Floating
                }
            },
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
        state = windowState,
        title = windowTitle,
        icon = windowIcon,
        onKeyEvent = { event ->
            // Runs only for keys no focused component consumed, so text fields keep their typing keys.
            event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                DesktopPlayerShortcuts.resolve(
                    key = event.key,
                    shift = event.isShiftPressed,
                    ctrl = event.isCtrlPressed,
                    alt = event.isAltPressed,
                    meta = event.isMetaPressed,
                )?.let(commands::execute) == true
        },
    ) {
        var openStreamRequested by remember { mutableStateOf(false) }
        var requestedDestination by remember { mutableStateOf<DesktopDestination?>(null) }
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
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.O),
                    onClick = { chooseMediaFiles().let(openFileHandler::submit) },
                )
                Item(
                    "Open Network Stream…",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.U),
                    onClick = { openStreamRequested = true },
                )
                Item(
                    "Add Folder…",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.O, shift = true),
                    onClick = { library.addFolder() },
                )
                Item(
                    "Import Playlist…",
                    onClick = { choosePlaylistFile()?.let { library.importPlaylistM3u(it) } },
                )
                Menu("Open Recent") {
                    if (recentEntries.isEmpty()) {
                        Item("No Recent Media", enabled = false, onClick = {})
                    } else {
                        recentEntries.forEach { entry ->
                            Item(entry.title, onClick = {
                                runCatching { File(java.net.URI(entry.uri)) }.getOrNull()
                                    ?.takeIf(File::isFile)
                                    ?.let { openFileHandler.submit(listOf(it)) }
                            })
                        }
                        Separator()
                        Item("Clear Recent Media", onClick = { recentMedia.clear() })
                    }
                }
                Separator()
                Item(
                    "Media Information…",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.I),
                    onClick = { commands.execute(DesktopPlayerCommand.MEDIA_INFO) },
                )
            }
            Menu("Playback") {
                Item(
                    "Play / Pause",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(
                        androidx.compose.ui.input.key.Key.Spacebar,
                        ctrl = true,
                    ),
                    onClick = { applicationScope.launch { togglePlayback(engine, queue, library) } },
                )
                Item("Stop", onClick = { engine.stop() })
                Item(
                    "Next Track",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.DirectionRight),
                    onClick = { applicationScope.launch { queue.next() } },
                )
                Item(
                    "Previous Track",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.DirectionLeft),
                    onClick = { applicationScope.launch { queue.previous() } },
                )
                Separator()
                Item(
                    "Seek Backward",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.DirectionLeft, alt = true),
                    onClick = { engine.seekTo((engine.state.value.positionMillis - 10_000).coerceAtLeast(0)) },
                )
                Item(
                    "Seek Forward",
                    shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.DirectionRight, alt = true),
                    onClick = { engine.seekTo(engine.state.value.positionMillis + 10_000) },
                )
                Separator()
                Item(
                    "Volume Up",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.DirectionUp),
                    onClick = { engine.setVolume((engine.state.value.volume + 0.1f).coerceAtMost(1f)) },
                )
                Item(
                    "Volume Down",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.DirectionDown),
                    onClick = { engine.setVolume((engine.state.value.volume - 0.1f).coerceAtLeast(0f)) },
                )
                Item(
                    "Mute / Unmute",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.M),
                    onClick = { commands.execute(DesktopPlayerCommand.TOGGLE_MUTE) },
                )
                Separator()
                Item(
                    "Normal Speed  ⌫",
                    onClick = { engine.setPlaybackSpeed(1.0f) },
                )
                Item(
                    "Faster  ]",
                    onClick = { commands.execute(DesktopPlayerCommand.SPEED_UP) },
                )
                Item(
                    "Slower  [",
                    onClick = { commands.execute(DesktopPlayerCommand.SPEED_DOWN) },
                )
            }
            Menu("Video") {
                commandItems(
                    commands,
                    DesktopPlayerCommand.FRAME_STEP_FORWARD to "E",
                    DesktopPlayerCommand.FRAME_STEP_BACKWARD to "Shift+E",
                    DesktopPlayerCommand.SNAPSHOT to "Shift+S",
                    DesktopPlayerCommand.CYCLE_ASPECT_RATIO to "A",
                    DesktopPlayerCommand.TOGGLE_DEINTERLACE to "D",
                    DesktopPlayerCommand.AB_LOOP to "Shift+L",
                )
            }
            Menu("Audio") {
                commandItems(
                    commands,
                    DesktopPlayerCommand.CYCLE_AUDIO_TRACK to "B",
                    DesktopPlayerCommand.AUDIO_DELAY_DOWN to "Shift+G",
                    DesktopPlayerCommand.AUDIO_DELAY_UP to "Shift+H",
                    DesktopPlayerCommand.TOGGLE_MUTE to "M",
                )
            }
            Menu("Subtitle") {
                commandItems(
                    commands,
                    DesktopPlayerCommand.CYCLE_SUBTITLE_TRACK to "V",
                    DesktopPlayerCommand.SUBTITLE_DELAY_DOWN to "G",
                    DesktopPlayerCommand.SUBTITLE_DELAY_UP to "H",
                )
            }
            Menu("View") {
                Item(
                    if (windowState.placement == WindowPlacement.Fullscreen) "Exit Full Screen" else "Enter Full Screen",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.F, shift = true),
                    onClick = toggleFullscreen,
                )
            }
            Menu("Navigate") {
                Item(
                    "Home",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.One),
                    onClick = { requestedDestination = DesktopDestination.Home },
                )
                Item(
                    "Music",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.Two),
                    onClick = { requestedDestination = DesktopDestination.Music },
                )
                Item(
                    "Videos",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.Three),
                    onClick = { requestedDestination = DesktopDestination.Videos },
                )
                Item(
                    "Playlists",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.Four),
                    onClick = { requestedDestination = DesktopDestination.Playlists },
                )
                Item(
                    "Network Streams",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.Five),
                    onClick = { requestedDestination = DesktopDestination.Network },
                )
                Separator()
                Item(
                    "Settings",
                    shortcut = primaryShortcut(androidx.compose.ui.input.key.Key.Comma),
                    onClick = { requestedDestination = DesktopDestination.Settings },
                )
            }
            Menu("Help") {
                Item(
                    "About Vimivo",
                    onClick = { requestedDestination = DesktopDestination.Settings },
                )
            }
        }
        DesktopPlayer(
            engine,
            queue,
            library,
            if (smokeTest != null) emptyList() else args.map(::File),
            openFileHandler,
            mpris,
            smokeTest = smokeTest,
            onSmokeTestFinished = { passed ->
                mpris.close()
                queue.close()
                engine.close()
                openFileHandler.close()
                exitProcess(if (passed) 0 else 1)
            },
            openStreamRequested = openStreamRequested,
            onOpenStreamHandled = { openStreamRequested = false },
            requestedDestination = requestedDestination,
            onDestinationHandled = { requestedDestination = null },
            commands = commands,
            recentMedia = recentMedia,
            isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
            onToggleFullscreen = toggleFullscreen,
        )
    }
}

/** Menu shortcuts use Cmd on macOS and Ctrl elsewhere, as documented in KEYBOARD_SHORTCUTS.md. */
private fun primaryShortcut(key: androidx.compose.ui.input.key.Key, shift: Boolean = false) =
    if (DesktopPlatform.detect() == DesktopPlatform.MACOS) {
        androidx.compose.ui.input.key.KeyShortcut(key, meta = true, shift = shift)
    } else {
        androidx.compose.ui.input.key.KeyShortcut(key, ctrl = true, shift = shift)
    }

/** Single-key shortcuts are handled by the window, so menu items show them in the label only. */
@Composable
private fun androidx.compose.ui.window.MenuScope.commandItems(
    commands: DesktopPlayerCommandExecutor,
    vararg items: Pair<DesktopPlayerCommand, String>,
) {
    items.forEach { (command, keyHint) ->
        Item("${command.label}  ($keyHint)", onClick = { commands.execute(command) })
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
    requestedDestination: DesktopDestination? = null,
    onDestinationHandled: () -> Unit = {},
    commands: DesktopPlayerCommandExecutor,
    recentMedia: dev.vimivo.desktop.library.DesktopRecentMediaRepository,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    smokeTest: DesktopSmokeTestRequest? = null,
    onSmokeTestFinished: (Boolean) -> Unit = {},
) {
    val state by engine.state.collectAsState()
    val commandStatus by commands.status.collectAsState()
    val mediaInfoVisible by commands.mediaInfoVisible.collectAsState()
    val videoFullscreen = isFullscreen && state.media?.kind == MediaKind.VIDEO
    val currentToggleFullscreen by androidx.compose.runtime.rememberUpdatedState(onToggleFullscreen)
    val scope = rememberCoroutineScope()
    val queueState by queue.state.collectAsState()
    val libraryState by library.state.collectAsState()
    var destination by remember { mutableStateOf(DesktopDestination.Home) }
    var selectedAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var activeProfile by remember { mutableStateOf(PlaybackProfile.DEFAULT) }
    var activePreset by remember { mutableStateOf(EqualizerPreset.FLAT) }
    var activeReplayGain by remember { mutableStateOf(ReplayGainMode.OFF) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var audioSettings by remember { mutableStateOf(AudioEnhancementSettings()) }
    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerMinutesRemaining by remember { mutableStateOf<Int?>(null) }
    var diagnosticsStatus by remember { mutableStateOf<String?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showOpenStreamDialog by remember { mutableStateOf(false) }
    var sidebarCollapsed by remember { mutableStateOf(false) }
    var queuePanelVisible by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(library.uiSetting("library_view_mode") == "GRID") }
    var descendingSort by remember { mutableStateOf(false) }
    val streamHistoryRepository = remember { dev.vimivo.desktop.streaming.DesktopStreamHistoryRepository() }
    var lyricsDoc by remember(state.media?.uri) { mutableStateOf<dev.vimivo.core.lyrics.LyricsDocument?>(null) }

    LaunchedEffect(requestedDestination) {
        requestedDestination?.let {
            destination = it
            selectedAlbum = null
            selectedArtist = null
            onDestinationHandled()
        }
    }

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
        val doubleClickListener = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(event: java.awt.event.MouseEvent) {
                if (event.clickCount == 2) currentToggleFullscreen()
            }
        }
        videoCanvas.addMouseListener(doubleClickListener)
        onDispose { videoCanvas.removeMouseListener(doubleClickListener) }
    }
    if (smokeTest != null) {
        // Declared after the surface is attached so video smoke runs exercise the real renderer.
        LaunchedEffect(smokeTest) {
            val results = DesktopSmokeTest.run(smokeTest.files, engine.state, File::toMediaItem, play = { media ->
                queue.play(listOf(media))
            })
            DesktopSmokeTest.publish(results, System.getenv("VIMIVO_SMOKE_REPORT"))
            onSmokeTestFinished(results.isNotEmpty() && results.all { it is DesktopSmokeResult.Passed })
        }
    }
    LaunchedEffect(engine, recentMedia) {
        // Only local files the user opened are remembered; stream URLs stay in the redacted stream history.
        engine.state.collect { playback ->
            val media = playback.media ?: return@collect
            if (media.uri.startsWith("file:") && playback.status == PlaybackStatus.Playing) {
                recentMedia.record(dev.vimivo.playback.RecentMediaEntry(media.uri, media.title, media.kind == MediaKind.VIDEO))
            }
        }
    }
    LaunchedEffect(commandStatus) {
        if (commandStatus != null) {
            delay(2_000)
            commands.clearStatus()
        }
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

    VimivoTheme(
        mode = libraryState.themeMode,
        highContrast = libraryState.highContrast,
        reduceMotion = libraryState.reduceAnimations,
        reduceTransparency = libraryState.reduceTransparency,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isVeryCompact = maxWidth < 640.dp
                val isCompact = maxWidth < 840.dp
                val targetSidebarWidth = when {
                    sidebarCollapsed -> VimivoShell.collapsedSidebarWidth
                    isVeryCompact -> 110.dp
                    isCompact -> 160.dp
                    else -> VimivoShell.expandedSidebarWidth
                }
                val sidebarWidth by animateDpAsState(
                    targetValue = targetSidebarWidth,
                    animationSpec = if (LocalReducedMotion.current) snap() else tween(VimivoMotion.navigationMillis),
                    label = "desktop sidebar width",
                )
                val contentPadding = if (isVeryCompact) 10.dp else if (isCompact) 16.dp else 24.dp
                val contentScrollState = rememberScrollState()
                Column(Modifier.fillMaxSize()) {
                    // 1. Playback Error Card (Top Banner)
                    (state.status as? PlaybackStatus.Error)?.let { error ->
                        DesktopPlaybackErrorCard(
                            error = error,
                            onRetry = { scope.launch { queue.retryCurrent() } },
                            onOpenOtherMedia = {
                                val files = chooseMediaFiles()
                                if (files.isNotEmpty()) {
                                    scope.launch { queue.play(files.map { it.toMediaItem() }) }
                                }
                            },
                            onSkipNext = if (queueState.items.size > 1) { { scope.launch { queue.next() } } } else null,
                            onDismiss = { engine.stop() },
                            onReportIssue = { showFeedbackDialog = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    // 2. Main Work Area: Sidebar + Screen Canvas + Slide-out Queue Drawer
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (!videoFullscreen) {
                            DesktopSidebarView(
                                selected = destination,
                                onDestinationSelected = {
                                    destination = it
                                    selectedAlbum = null
                                    selectedArtist = null
                                },
                                collapsed = sidebarCollapsed,
                                onToggleCollapse = { sidebarCollapsed = !sidebarCollapsed },
                                modifier = Modifier.width(sidebarWidth),
                            )
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (!videoFullscreen) {
                                DesktopTopHeaderView(
                                    destination = destination,
                                    search = libraryState.search,
                                    showLibraryControls = destination in setOf(DesktopDestination.Music, DesktopDestination.Videos),
                                    gridView = gridView,
                                    descendingSort = descendingSort,
                                    queueVisible = queuePanelVisible,
                                    queueItemCount = queueState.items.size,
                                    onToggleSidebar = { sidebarCollapsed = !sidebarCollapsed },
                                    onSearch = library::setSearch,
                                    onToggleView = {
                                        val next = !gridView
                                        gridView = next
                                        library.setLibraryViewMode(if (next) dev.vimivo.settings.LibraryViewMode.GRID else dev.vimivo.settings.LibraryViewMode.LIST)
                                    },
                                    onToggleSort = { descendingSort = !descendingSort },
                                    onToggleQueue = { queuePanelVisible = !queuePanelVisible },
                                )
                            }
                            Column(
                                Modifier.fillMaxSize().then(
                                    if (videoFullscreen) Modifier else Modifier.verticalScroll(contentScrollState).padding(contentPadding),
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (!videoFullscreen && destination == DesktopDestination.Home) {
                                    DesktopHomeScreenView(
                                        state = libraryState,
                                        onPlay = playMedia,
                                        onOpenFile = {
                                            val files = chooseMediaFiles()
                                            if (files.isNotEmpty()) {
                                                scope.launch { queue.play(files.map { it.toMediaItem() }) }
                                            }
                                        },
                                        onAddFolder = library::addFolder,
                                        onOpenStreamDialog = { showOpenStreamDialog = true },
                                        onDismissRecommendation = library::dismissRecommendation,
                                        onTogglePersonalization = library::setPersonalizationEnabled,
                                        onScan = library::scan,
                                        onCancelScan = library::cancelScan,
                                        onRemoveRoot = library::removeRoot,
                                        onSelectAlbum = { album ->
                                            selectedAlbum = album
                                            destination = DesktopDestination.Albums
                                        },
                                        onSelectArtist = { artist ->
                                            selectedArtist = artist
                                            destination = DesktopDestination.Artists
                                        },
                                    )
                                }
            if (!videoFullscreen && destination in setOf(DesktopDestination.Music, DesktopDestination.Videos)) {
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
                    gridView = gridView,
                    descendingSort = descendingSort,
                    onOpenFile = { chooseMediaFiles().let(openFileHandler::submit) },
                    onImportPlaylist = { choosePlaylistFile()?.let { library.importPlaylistM3u(it) } },
                    onToggleSelect = library::toggleMediaSelection,
                    onSelectAll = library::selectAll,
                    onClearSelection = library::clearSelection,
                    onPlaySelected = { items -> scope.launch { queue.play(items) } },
                    onQueueSelected = { items -> queue.addToQueue(items) },
                    onEditSelectedMetadata = { destination = DesktopDestination.MetadataEditor },
                )
            }
            if (!videoFullscreen && destination == DesktopDestination.Albums) {
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
            if (!videoFullscreen && destination == DesktopDestination.Artists) {
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
            if (!videoFullscreen && destination == DesktopDestination.Playlists) {
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
            if (!videoFullscreen && destination == DesktopDestination.Favorites) {
                DesktopFavoritesPanel(
                    items = libraryState.favorites,
                    onPlay = playMedia,
                    onPlayNext = { queue.playNext(listOf(it)) },
                    onAddToQueue = { queue.addToQueue(listOf(it)) },
                    onToggle = library::toggleFavorite,
                )
            }
            if (!videoFullscreen && destination == DesktopDestination.History) {
                DesktopHistoryPanel(
                    items = libraryState.history,
                    onPlay = playMedia,
                    onPlayNext = { queue.playNext(listOf(it)) },
                    onAddToQueue = { queue.addToQueue(listOf(it)) },
                    onRemove = library::removeHistory,
                    onClear = library::clearHistory,
                )
            }
            if (!videoFullscreen && destination == DesktopDestination.SmartPlaylists) {
                DesktopSmartPlaylistsPanel(
                    state = libraryState,
                    controller = library,
                    onPlay = playMedia,
                )
            }
            if (!videoFullscreen && destination == DesktopDestination.Duplicates) {
                DesktopDuplicatesPanel(
                    state = libraryState,
                    controller = library,
                    onPlay = playMedia,
                )
            }
            if (!videoFullscreen && destination == DesktopDestination.Insights) {
                DesktopInsightsPanel(state = libraryState)
            }
            if (!videoFullscreen && destination == DesktopDestination.MetadataEditor) {
                DesktopMetadataEditorPanel(state = libraryState, controller = library)
            }
            if (!videoFullscreen && destination == DesktopDestination.BatchRename) {
                DesktopBatchRenamePanel(state = libraryState, controller = library)
            }
            if (!videoFullscreen && destination == DesktopDestination.MissingMedia) {
                DesktopMissingMediaPanel(state = libraryState, controller = library)
            }
            if (!videoFullscreen && destination == DesktopDestination.Automation) {
                DesktopAutomationHealthPanel(state = libraryState, controller = library)
            }
            if (!videoFullscreen && destination == DesktopDestination.Network) {
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
            if (!videoFullscreen && destination == DesktopDestination.Settings) {
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
                    onLibraryViewModeSelected = { mode ->
                        gridView = mode == dev.vimivo.settings.LibraryViewMode.GRID
                        library.setLibraryViewMode(mode)
                    },
                    onDefaultSortSelected = library::setDefaultSort,
                    onReduceAnimationsToggled = library::setReduceAnimations,
                    onHighContrastToggled = library::setHighContrast,
                    onReduceTransparencyToggled = library::setReduceTransparency,
                    onResumePlaybackToggled = library::setResumePlayback,
                    onDefaultSpeedSelected = { speed ->
                        library.setDefaultSpeed(speed)
                        engine.setPlaybackSpeed(speed)
                    },
                    onRepeatBehaviorSelected = library::setRepeatBehavior,
                    onShuffleBehaviorSelected = library::setShuffleBehavior,
                    onResetSettings = library::resetSettings,
                )
            }
            if (!videoFullscreen && destination == DesktopDestination.Videos || state.media?.kind == MediaKind.VIDEO) {
                SwingPanel(
                    factory = { videoCanvas },
                    modifier = if (videoFullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(360.dp),
                )
                if (!videoFullscreen) {
                    dev.vimivo.desktop.playback.DesktopVideoControlsPanel(
                        engine = engine,
                        state = state,
                    )
                }
            }
            if (showEqualizerPanel && !videoFullscreen) {
                dev.vimivo.desktop.playback.DesktopEqualizerPanel(
                    engine = engine,
                    currentSettings = audioSettings,
                    onSettingsChanged = { audioSettings = it },
                    onDismiss = { showEqualizerPanel = false },
                )
            }
                                if (showDiagnostics && !videoFullscreen) {
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

                                if (showLyrics && state.media?.kind == MediaKind.AUDIO) {
                                    DesktopLyricsCard(lyricsDoc = lyricsDoc, positionMillis = state.positionMillis)
                                }

                                if (!videoFullscreen) {
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
                        }

                        // Right: Slide-out Queue Drawer
                        if (queuePanelVisible && !videoFullscreen) {
                            Surface(
                                modifier = Modifier.width(320.dp).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Playback Queue",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        IconButton(
                                            onClick = { queuePanelVisible = false },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Close queue",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    if (queueState.items.isEmpty()) {
                                        Box(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "Queue is empty\nAdd tracks from your library",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    } else {
                                        DesktopQueuePanel(
                                            state = queueState,
                                            onRemove = queue::remove,
                                            onMove = queue::move,
                                            onClear = queue::clear,
                                            onPlayAt = { index -> scope.launch { queue.playAt(index) } },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    commandStatus?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Text(message, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }

                    // 3. Persistent Playback Bar (Docked at Bottom)
                    DesktopPlaybackBarView(
                        state = state,
                        queueState = queueState,
                        onTogglePlayback = { scope.launch { togglePlayback(engine, queue, library) } },
                        onPrevious = { scope.launch { queue.previous() } },
                        onNext = { scope.launch { queue.next() } },
                        onStop = { engine.stop() },
                        onSeek = { engine.seekTo(it) },
                        onSetVolume = { engine.setVolume(it) },
                        onSetSpeed = { engine.setPlaybackSpeed(it) },
                        onToggleShuffle = queue::toggleShuffle,
                        onCycleRepeat = queue::cycleRepeat,
                        onToggleFavorite = library::toggleFavorite,
                        onToggleEqualizer = { showEqualizerPanel = !showEqualizerPanel },
                        showEqualizer = showEqualizerPanel,
                        onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
                        showDiagnostics = showDiagnostics,
                        onToggleQueue = { queuePanelVisible = !queuePanelVisible },
                        queueVisible = queuePanelVisible,
                        onOpenMedia = {
                            val files = chooseMediaFiles()
                            if (files.isNotEmpty()) {
                                scope.launch { queue.play(files.map { it.toMediaItem() }) }
                            }
                        },
                        onToggleLyrics = { showLyrics = !showLyrics },
                        showLyrics = showLyrics,
                    )
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

    if (mediaInfoVisible) {
        dev.vimivo.desktop.playback.DesktopMediaInfoDialog(
            state = state,
            containerInfo = remember(state.media?.uri, state.audioTracks, state.videoTracks) { engine.containerInfo() },
            onDismiss = commands::dismissMediaInfo,
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
    gridView: Boolean,
    descendingSort: Boolean,
    onOpenFile: () -> Unit,
    onImportPlaylist: () -> Unit,
    onToggleSelect: (MediaId) -> Unit,
    onSelectAll: (List<MediaItem>) -> Unit,
    onClearSelection: () -> Unit,
    onPlaySelected: (List<MediaItem>) -> Unit,
    onQueueSelected: (List<MediaItem>) -> Unit,
    onEditSelectedMetadata: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onOpenFile) { Text("Open File(s)") }
        OutlinedButton(onClick = onAddFolder) { Text("Add Folder") }
        OutlinedButton(onClick = onImportPlaylist) { Text("Import M3U…") }
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
        val source = if (destination == DesktopDestination.Music) state.audio else state.video
        val media = remember(source, descendingSort) {
            if (descendingSort) source.sortedByDescending { it.title.lowercase() } else source.sortedBy { it.title.lowercase() }
        }
        val selectedItems = remember(media, state.selectedMediaIds) {
            media.filter { it.id in state.selectedMediaIds }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                label = { Text("Search library") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            if (media.isNotEmpty()) {
                if (state.selectedMediaIds.isEmpty()) {
                    OutlinedButton(onClick = { onSelectAll(media) }) {
                        Text("Select All")
                    }
                } else {
                    OutlinedButton(onClick = onClearSelection) {
                        Text("Deselect (${state.selectedMediaIds.size})")
                    }
                }
            }
        }

        if (state.selectedMediaIds.isNotEmpty() && selectedItems.isNotEmpty()) {
            VimivoGlassSurface(
                variant = VimivoGlassVariant.CONTROL,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Batch actions for ${selectedItems.size} selected items"
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedItems.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onPlaySelected(selectedItems) }) {
                        Text("Play Selected")
                    }
                    OutlinedButton(onClick = { onQueueSelected(selectedItems) }) {
                        Text("Queue")
                    }
                    OutlinedButton(onClick = onEditSelectedMetadata) {
                        Text("Edit Metadata")
                    }
                    OutlinedButton(onClick = onClearSelection) {
                        Text("Clear")
                    }
                }
            }
        }

        if (!state.loading && media.isEmpty()) Text(if (state.search.isBlank()) "No media found. Add and scan a folder." else "No matching media.")
        if (gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                modifier = Modifier.fillMaxWidth().height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(media, key = { it.id.value }) { item ->
                    val isSelected = item.id in state.selectedMediaIds
                    DesktopMediaGridCard(
                        item = item,
                        destination = destination,
                        state = state,
                        isSelected = isSelected,
                        onToggleSelect = onToggleSelect,
                        onPlay = onPlay,
                        onAddToQueue = onAddToQueue,
                        onToggleFavorite = onToggleFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(media, key = { it.id.value }) { item ->
                    val isSelected = item.id in state.selectedMediaIds
                    DesktopMediaListRow(
                        item = item,
                        destination = destination,
                        state = state,
                        isSelected = isSelected,
                        onToggleSelect = onToggleSelect,
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleFavorite = onToggleFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopMediaListRow(
    item: MediaItem,
    destination: DesktopDestination,
    state: DesktopLibraryUiState,
    isSelected: Boolean,
    onToggleSelect: (MediaId) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onPlayNext: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onAddToPlaylist: (String, MediaItem) -> Unit,
) {
    val subtitle = remember(item.artist, item.album, item.durationMillis) {
        listOfNotNull(item.artist, item.album, item.durationMillis?.let(::formatTime)).joinToString(" · ")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect(item.id) },
                modifier = Modifier.semantics { contentDescription = "Select ${item.title}" },
            )
            DesktopArtwork(item.artworkUri, item.title, if (destination == DesktopDestination.Videos) 80.dp else 48.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button({ onPlay(item) }, modifier = Modifier.semantics { contentDescription = "Play ${item.title}" }) { Text("Play") }
            OutlinedButton({ onPlayNext(item) }) { Text("Next") }
            OutlinedButton({ onAddToQueue(item) }) { Text("Queue") }
            OutlinedButton({ onToggleFavorite(item) }) { Text(if (item.isFavorite) "★" else "☆") }
            AddToPlaylistButton(state.playlists, item, onAddToPlaylist)
        }
    }
}

@Composable
private fun DesktopMediaGridCard(
    item: MediaItem,
    destination: DesktopDestination,
    state: DesktopLibraryUiState,
    isSelected: Boolean,
    onToggleSelect: (MediaId) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onAddToPlaylist: (String, MediaItem) -> Unit,
) {
    val subtitle = remember(item.artist, item.album) { item.artist ?: item.album ?: "Local media" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopArtwork(item.artworkUri, item.title, if (destination == DesktopDestination.Videos) 120.dp else 88.dp)
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect(item.id) },
                    modifier = Modifier.semantics { contentDescription = "Select ${item.title}" },
                )
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ onPlay(item) }) { Text("Play") }
                OutlinedButton({ onAddToQueue(item) }) { Text("Queue") }
                OutlinedButton({ onToggleFavorite(item) }) { Text(if (item.isFavorite) "★" else "☆") }
            }
            AddToPlaylistButton(state.playlists, item, onAddToPlaylist)
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



private fun chooseMediaFiles(): List<File> {
    val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    return dialog.files.orEmpty().toList()
}

private fun choosePlaylistFile(): File? {
    val dialog = FileDialog(null as Frame?, "Import Playlist (M3U, XSPF)", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".m3u", true) || name.endsWith(".m3u8", true) || name.endsWith(".xspf", true) }
    dialog.isVisible = true
    return dialog.files?.firstOrNull() ?: dialog.file?.let { File(dialog.directory, it) }
}

private fun File.toMediaItem() = MediaItem(
    id = MediaId(toPath().toUri().toString()),
    uri = toPath().toUri().toString(),
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
