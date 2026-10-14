@file:Suppress(
    "ArgumentListWrapping",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "Indentation",
    "ImportOrdering",
    "LongMethod",
    "LongParameterList",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "PropertyWrapping",
    "StringTemplate",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.library.ui

import android.content.Intent
import android.content.ClipboardManager
import android.app.ActivityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.Playlist
import dev.vimivo.core.model.Bookmark
import dev.vimivo.app.R
import dev.vimivo.features.home.HomeViewModel
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.LibraryUiState
import dev.vimivo.platform.android.library.LibraryViewModel
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.library.PlaylistViewModel
import dev.vimivo.platform.android.network.NetworkViewModel
import dev.vimivo.platform.android.network.StreamPlaybackStatus
import dev.vimivo.platform.android.playback.PlaybackController
import dev.vimivo.platform.android.playback.PlaybackUiState
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.settings.SettingsViewModel
import dev.vimivo.platform.android.ui.components.BottomSheet
import dev.vimivo.platform.android.ui.components.EmptyState
import dev.vimivo.platform.android.ui.components.ErrorState
import dev.vimivo.platform.android.ui.components.LoadingIndicator
import dev.vimivo.platform.android.ui.components.MediaArtwork
import dev.vimivo.platform.android.ui.components.MediaCard
import dev.vimivo.platform.android.ui.components.MiniPlayer
import dev.vimivo.platform.android.ui.components.PlayerControls
import dev.vimivo.platform.android.ui.components.SearchBar
import dev.vimivo.platform.android.ui.components.SettingsRow
import dev.vimivo.platform.android.ui.components.SongRow
import dev.vimivo.platform.android.ui.components.VideoCard
import dev.vimivo.platform.android.ui.theme.VimivoAndroidTheme
import dev.vimivo.platform.android.ui.theme.LocalReducedMotion
import dev.vimivo.settings.HomeSection
import dev.vimivo.settings.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

private enum class AppDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    MUSIC("Music", Icons.Outlined.MusicNote),
    VIDEOS("Videos", Icons.Outlined.VideoLibrary),
    NETWORK("Network", Icons.Outlined.Link),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
fun VimivoAndroidApp(
    playback: PlaybackController,
    thumbnails: AndroidThumbnailRepository,
    permissions: MediaPermissionPolicy,
    onVideoPlayerState: (Boolean, Boolean) -> Unit = { _, _ -> },
    enterPictureInPicture: () -> Unit = {},
    settingsViewModel: SettingsViewModel = koinViewModel(),
    playbackViewModel: PlaybackViewModel = koinViewModel(),
) {
    val settings by settingsViewModel.state.collectAsState()
    val player by playbackViewModel.state.collectAsState()
    VimivoAndroidTheme(settings) {
        AppShell(
            playback,
            thumbnails,
            permissions,
            settings.homeSections,
            settings.artworkBasedAccents,
            settings.audioVisualizerEnabled,
            player,
            playbackViewModel,
            settingsViewModel,
            onVideoPlayerState,
            enterPictureInPicture,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    playback: PlaybackController,
    thumbnails: AndroidThumbnailRepository,
    permissions: MediaPermissionPolicy,
    homeSections: List<HomeSection>,
    artworkBasedAccents: Boolean,
    audioVisualizerEnabled: Boolean,
    player: PlaybackUiState,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    onVideoPlayerState: (Boolean, Boolean) -> Unit,
    enterPictureInPicture: () -> Unit,
) {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var searchVisible by remember { mutableStateOf(false) }
    var nowPlayingVisible by remember { mutableStateOf(false) }
    var queueVisible by remember { mutableStateOf(false) }
    var videoPlayerVisible by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        val nav: @Composable () -> Unit = {
            if (useRail) AppRail(destination) { destination = it } else AppBottomBar(destination) { destination = it }
        }
        Row(Modifier.fillMaxSize()) {
            if (useRail) nav()
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(destination.label) },
                        actions = {
                            IconButton(onClick = { searchVisible = !searchVisible }) {
                                Icon(Icons.Outlined.Search, "Global search")
                            }
                        },
                    )
                },
                bottomBar = {
                    Column {
                        player.current?.let { current ->
                            MiniPlayer(
                                media = current.toDomain(),
                                thumbnails = thumbnails,
                                isPlaying = player.isPlaying,
                                progress = if (player.durationMillis > 0) player.positionMillis.toFloat() / player.durationMillis else 0f,
                                onOpen = {
                                    if (current.toDomain().kind == MediaKind.VIDEO) videoPlayerVisible = true
                                    else nowPlayingVisible = true
                                },
                                onToggle = playbackViewModel::togglePlayback,
                            )
                        }
                        if (!useRail) nav()
                    }
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    AnimatedVisibility(searchVisible, enter = fadeIn(), exit = fadeOut()) {
                        GlobalSearch(thumbnails, playback, onClose = { searchVisible = false })
                    }
                    val destinationContent: @Composable (AppDestination) -> Unit = { selected ->
                        when (selected) {
                            AppDestination.HOME -> HomeScreen(
                                visibleSections = homeSections,
                                thumbnails = thumbnails,
                                playback = playback,
                                openNetwork = { destination = AppDestination.NETWORK },
                            )
                            AppDestination.MUSIC -> MusicScreen(thumbnails, playback, permissions)
                            AppDestination.VIDEOS -> VideoScreen(
                                thumbnails,
                                playback,
                                permissions,
                                openPlayer = { videoPlayerVisible = true },
                            )
                            AppDestination.NETWORK -> NetworkScreen()
                            AppDestination.SETTINGS -> SettingsScreen(settingsViewModel)
                        }
                    }
                    if (LocalReducedMotion.current) destinationContent(destination) else {
                        AnimatedContent(destination, label = "primary destination") { selected ->
                            destinationContent(selected)
                        }
                    }
                }
            }
        }
    }
    if (nowPlayingVisible) NowPlayingSheet(
        player,
        thumbnails,
        playback,
        playbackViewModel,
        artworkBasedAccents,
        audioVisualizerEnabled,
        { nowPlayingVisible = false },
    ) {
        queueVisible = true
    }
    if (queueVisible) QueueSheet(player, playbackViewModel) { queueVisible = false }
    if (videoPlayerVisible) AdvancedVideoPlayer(
        player = player,
        controller = playback.controller.collectAsState().value,
        viewModel = playbackViewModel,
        settings = settingsViewModel.state.collectAsState().value,
        onGestureSensitivity = settingsViewModel::setVideoGestureSensitivity,
        onPreferredSubtitleLanguage = settingsViewModel::setPreferredSubtitleLanguage,
        onPreferredAudioLanguage = settingsViewModel::setPreferredAudioLanguage,
        onSubtitlesEnabled = settingsViewModel::setSubtitlesEnabled,
        onSubtitleDelay = settingsViewModel::setSubtitleDelay,
        onAudioDelay = settingsViewModel::setAudioDelay,
        onSubtitleAppearance = settingsViewModel::setSubtitleAppearance,
        onPlayerState = onVideoPlayerState,
        onEnterPictureInPicture = enterPictureInPicture,
        onDismiss = { videoPlayerVisible = false },
    )
}

@Composable
private fun AppBottomBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar(Modifier.navigationBarsPadding()) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected == destination,
                { onSelect(destination) },
                { Icon(destination.icon, destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun AppRail(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight()) {
        Spacer(Modifier.height(64.dp))
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected == destination,
                { onSelect(destination) },
                { Icon(destination.icon, destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    visibleSections: List<HomeSection>,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    openNetwork: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    playlistsViewModel: PlaylistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playlists by playlistsViewModel.playlists.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(20.dp)) {
                Text("Good to see you", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                Text("Everything stays on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        visibleSections.forEach { section ->
            when (section) {
                HomeSection.CONTINUE_WATCHING -> mediaSection("Continue watching", state.continueWatching, thumbnails, playback)
                HomeSection.RECENTLY_PLAYED -> mediaSection("Recently played", state.recentlyPlayed, thumbnails, playback)
                HomeSection.RECENT_MUSIC -> mediaSection("Recently added music", state.recentlyAddedMusic, thumbnails, playback)
                HomeSection.RECENT_VIDEOS -> mediaSection("Recently added videos", state.recentlyAddedVideos, thumbnails, playback)
                HomeSection.FAVORITES -> mediaSection("Favorites", state.favorites, thumbnails, playback)
                HomeSection.PLAYLISTS -> if (playlists.isNotEmpty()) item {
                    SectionTitle("Quick playlists")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(180.dp)) {
                                Column(Modifier.padding(18.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.QueueMusic, null)
                                    Spacer(Modifier.height(20.dp))
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${playlist.items.size} items", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                HomeSection.OPEN_STREAM -> item {
                    SectionTitle("Quick open stream")
                    Surface(
                        onClick = openNetwork,
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Link, null)
                            Spacer(Modifier.width(16.dp))
                            Column { Text("Play a network stream", style = MaterialTheme.typography.titleMedium); Text("HTTP and HTTPS sources") }
                        }
                    }
                }
            }
        }
        if (visibleSections.all { sectionItems(it, state).isEmpty() } && playlists.isEmpty()) item {
            EmptyState(title = "Your home is ready", detail = "Media and playback activity will appear here as you use Vimivo.")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mediaSection(
    title: String,
    media: List<MediaItem>,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
) {
    if (media.isEmpty()) return
    item { SectionTitle(title) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(media, key = { it.id.value }) { item -> MediaCard(item, thumbnails) { playback.play(item) } }
        }
    }
    item { Spacer(Modifier.height(24.dp)) }
}

private fun sectionItems(section: HomeSection, state: dev.vimivo.features.home.HomeUiState) = when (section) {
    HomeSection.CONTINUE_WATCHING -> state.continueWatching
    HomeSection.RECENTLY_PLAYED -> state.recentlyPlayed
    HomeSection.RECENT_MUSIC -> state.recentlyAddedMusic
    HomeSection.RECENT_VIDEOS -> state.recentlyAddedVideos
    HomeSection.FAVORITES -> state.favorites
    else -> emptyList()
}

@Composable
private fun SectionTitle(title: String) = Text(
    title,
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp).semantics { heading() },
)

@Composable
private fun MusicScreen(
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    permissions: MediaPermissionPolicy,
    viewModel: LibraryViewModel = koinViewModel(),
    playlistViewModel: PlaylistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val playlistItems by playlistViewModel.selectedItems.collectAsState()
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedAction by remember { mutableStateOf<MediaItem?>(null) }
    var sortVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.selectKind(MediaKind.AUDIO) }
    LibraryChrome(state, viewModel, permissions, MediaKind.AUDIO) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { AssistChip({ selectedPlaylist = null; viewModel.selectKind(MediaKind.AUDIO) }, { Text("All songs") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.showCategories(LibraryCategoryField.ALBUM) }, { Text("Albums") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.showCategories(LibraryCategoryField.ARTIST) }, { Text("Artists") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.showCategories(LibraryCategoryField.ALBUM_ARTIST) }, { Text("Album artists") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.showCategories(LibraryCategoryField.GENRE) }, { Text("Genres") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.showCategories(LibraryCategoryField.FOLDER) }, { Text("Folders") }) }
            item { AssistChip({ selectedPlaylist = Playlist("__list__", "Playlists", emptyList(), 0) }, { Text("Playlists") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.recentlyAddedMusic() }, { Text("Recently added") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.recentlyPlayedMusic() }, { Text("Recently played") }) }
            item { AssistChip({ selectedPlaylist = null; viewModel.favorites() }, { Text("Favorites") }, leadingIcon = { Icon(Icons.Outlined.Favorite, null) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton({ sortVisible = true }) { Icon(Icons.AutoMirrored.Outlined.List, null); Text(" Sort") }
        }
        when {
            selectedPlaylist?.id == "__list__" -> PlaylistList(playlists, playlistViewModel) { playlist ->
                selectedPlaylist = playlist
                playlistViewModel.load(playlist)
            }
            selectedPlaylist != null -> PlaylistDetail(selectedPlaylist!!, playlistItems, thumbnails, playback, playlistViewModel, { selectedPlaylist = Playlist("__list__", "Playlists", emptyList(), 0) }) {
                selectedAction = it
            }
            else -> {
                val detailTitle = state.query.album ?: state.query.artist ?: state.query.albumArtist ?: state.query.genre ?: state.query.folderPath
                if (detailTitle != null) MusicCollectionHeader(
                    title = detailTitle,
                    subtitle = collectionSubtitle(state),
                    items = state.items,
                    thumbnails = thumbnails,
                    onPlay = { viewModel.resolveCurrentItems { playback.playQueue(it) } },
                    onShuffle = { viewModel.resolveCurrentItems { playback.playQueue(it, shuffle = true) } },
                    onQueue = { viewModel.resolveCurrentItems(playback::addToQueue) },
                    onFavorite = if (state.query.album != null) ({ viewModel.setFavorite(state.items, state.items.any { !it.isFavorite }) }) else null,
                )
                CategoryOrMediaList(state, viewModel, thumbnails, playback) { selectedAction = it }
            }
        }
    }
    if (sortVisible) SortSheet(state.query.sort, { sort -> viewModel.sort(sort); sortVisible = false }) { sortVisible = false }
    selectedAction?.let { item -> MediaActionsSheet(item, playback, viewModel, { selectedAction = null }) }
}

@Composable
private fun VideoScreen(
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    permissions: MediaPermissionPolicy,
    openPlayer: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.selectKind(MediaKind.VIDEO) }
    LibraryChrome(state, viewModel, permissions, MediaKind.VIDEO) {
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { AssistChip({ viewModel.selectKind(MediaKind.VIDEO) }, { Text("All videos") }) }
            item { AssistChip({ viewModel.showCategories(LibraryCategoryField.FOLDER) }, { Text("Folders") }, leadingIcon = { Icon(Icons.Outlined.Folder, null) }) }
            item { AssistChip(viewModel::recentlyWatched, { Text("Recently watched") }) }
            item { AssistChip(viewModel::recentlyAdded, { Text("Recently added") }) }
            item { AssistChip(viewModel::favoriteVideos, { Text("Favorites") }) }
            item { AssistChip(viewModel::continueWatching, { Text("Continue watching") }) }
        }
        if (state.categories != null) CategoryOrMediaList(state, viewModel, thumbnails, playback)
        else VideoGrid(state, thumbnails, playback, viewModel, openPlayer)
    }
}

@Composable
private fun LibraryChrome(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    permissions: MediaPermissionPolicy,
    kind: MediaKind,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.permissionsChanged() }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.importFile(it.toString())
        }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.importFolder(it.toString())
        }
    }
    Column(Modifier.fillMaxSize()) {
        if ((kind == MediaKind.AUDIO && state.permissions.audio.name == "DENIED") || (kind == MediaKind.VIDEO && state.permissions.video.name == "DENIED")) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Library access is optional", style = MaterialTheme.typography.titleMedium)
                    Text("Allow access to discover shared media, or open individual files and folders with Android's picker.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ request.launch(if (kind == MediaKind.AUDIO) permissions.audioRequest() else permissions.videoRequest()) }) { Text("Allow access") }
                        OutlinedButton({ file.launch(arrayOf("audio/*", "video/*")) }) { Text("Open file") }
                        OutlinedButton({ folder.launch(null) }) { Text("Add folder") }
                    }
                }
            }
        }
        state.errorMessage?.let { ErrorState(it, viewModel::rescan) }
        content()
    }
}

@Composable
private fun CategoryOrMediaList(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    onMore: (MediaItem) -> Unit = {},
) {
    when {
        state.isLoading && state.items.isEmpty() && state.categories == null -> LoadingIndicator()
        state.categories != null -> LazyColumn {
            items(state.categories, key = { it.name }) { category ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.selectCategory(category) },
                    leadingContent = { Icon(Icons.Outlined.Folder, null) },
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text("${category.itemCount} items") },
                )
            }
        }
        state.items.isEmpty() -> EmptyState(title = "No music found", detail = "Add a folder, open a file, or grant library access.", action = "Rescan", onAction = viewModel::rescan)
        else -> LazyColumn {
            items(state.items, key = { it.id.value }) { item ->
                SongRow(
                    media = item,
                    thumbnails = thumbnails,
                    onClick = {
                        viewModel.resolveCurrentItems { all ->
                            playback.playQueue(all, all.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(item) },
                    onMore = { onMore(item) },
                )
                HorizontalDivider(Modifier.padding(start = 76.dp))
            }
            if (state.hasMore) item { OutlinedButton(viewModel::loadMore, Modifier.fillMaxWidth().padding(16.dp)) { Text("Load more") } }
        }
    }
}

@Composable
private fun VideoGrid(
    state: LibraryUiState,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    viewModel: LibraryViewModel,
    openPlayer: () -> Unit,
) {
    when {
        state.isLoading && state.items.isEmpty() -> LoadingIndicator("Loading videos")
        state.items.isEmpty() -> EmptyState(Icons.Outlined.VideoLibrary, "No videos found", "Add a folder, open a video, or grant library access.", "Rescan", viewModel::rescan)
        else -> LazyVerticalGrid(GridCells.Adaptive(240.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.items, key = { it.id.value }) { item ->
                VideoCard(
                    item,
                    thumbnails,
                    onClick = {
                        viewModel.resolveCurrentItems { videos ->
                            playback.playQueue(videos, videos.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
                            openPlayer()
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(item) },
                )
            }
        }
    }
}

@Composable
private fun MusicCollectionHeader(
    title: String,
    subtitle: String,
    items: List<MediaItem>,
    thumbnails: AndroidThumbnailRepository,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: (() -> Unit)? = null,
) {
    val representative = items.firstOrNull()
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        representative?.let { MediaArtwork(it, thumbnails, Modifier.size(180.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onPlay) { Text("Play all") }
            OutlinedButton(onShuffle) { Icon(Icons.Outlined.Shuffle, null); Text(" Shuffle") }
            IconButton(onQueue) { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add collection to queue") }
            onFavorite?.let { action -> IconButton(action) { Icon(Icons.Outlined.Favorite, "Favorite album") } }
        }
    }
}

private fun collectionSubtitle(state: LibraryUiState): String {
    val total = state.items.sumOf { it.durationMillis ?: 0 }
    val type = when {
        state.query.album != null -> listOfNotNull(
            state.items.firstOrNull()?.artist ?: "Unknown artist",
            state.items.mapNotNull(MediaItem::releaseYear).distinct().singleOrNull()?.toString(),
        ).joinToString(" · ")
        state.query.artist != null || state.query.albumArtist != null -> "${state.items.mapNotNull(MediaItem::album).distinct().size} albums"
        state.query.folderPath != null -> "Folder"
        state.query.genre != null -> "Genre"
        else -> "Collection"
    }
    return "$type · ${state.totalCount} tracks · ${formatDuration(total)}"
}

@Composable
private fun PlaylistList(playlists: List<Playlist>, viewModel: PlaylistViewModel, onSelect: (Playlist) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    Column {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ creating = true }) { Text("Create playlist") }
            OutlinedButton({ importer.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain")) }) { Text("Import M3U") }
        }
    if (playlists.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Outlined.QueueMusic, "No playlists", "Create playlists from your library to see them here.")
    } else {
        LazyColumn {
            items(playlists, key = Playlist::id) { playlist ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(playlist) },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) },
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("${playlist.items.size} tracks") },
                )
            }
        }
    }
    }
    if (creating) PlaylistNameDialog("Create playlist", "New playlist", { viewModel.create(it); creating = false }) { creating = false }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    media: List<MediaItem>,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    viewModel: PlaylistViewModel,
    onDeleted: () -> Unit,
    onMore: (MediaItem) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var duplicating by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        uri?.let { viewModel.export(it, playlist) }
    }
    Column(Modifier.fillMaxSize()) {
        MusicCollectionHeader(
            playlist.name,
            "${media.size} available tracks · ${formatDuration(media.sumOf { it.durationMillis ?: 0 })}",
            media,
            thumbnails,
            { playback.playQueue(media) },
            { playback.playQueue(media, shuffle = true) },
            { playback.addToQueue(media) },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ renaming = true }) { Text("Rename") }
            OutlinedButton({ duplicating = true }) { Text("Duplicate") }
            OutlinedButton({ exporter.launch("${playlist.name}.m3u8") }) { Text("Export") }
            OutlinedButton({ viewModel.delete(playlist); onDeleted() }) { Text("Delete") }
        }
        LazyColumn {
            items(media, key = { it.id.value }) { item ->
                val index = media.indexOf(item)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(item, thumbnails, { playback.playQueue(media, index) }, onMore = { onMore(item) })
                    }
                    IconButton({ if (index > 0) viewModel.move(playlist, index, index - 1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                    IconButton({ viewModel.remove(playlist, index) }) { Icon(Icons.Outlined.Delete, "Remove from playlist") }
                }
            }
        }
    }
    if (renaming) PlaylistNameDialog("Rename playlist", playlist.name, { viewModel.rename(playlist, it); renaming = false }) { renaming = false }
    if (duplicating) PlaylistNameDialog("Duplicate playlist", "${playlist.name} copy", { viewModel.duplicate(playlist, it); duplicating = false }) { duplicating = false }
}

@Composable
private fun PlaylistNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onDismiss) { Text("Cancel") }
                    Button({ onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SortSheet(selected: LibrarySort, onSelect: (LibrarySort) -> Unit, onDismiss: () -> Unit) {
    BottomSheet(onDismiss) {
        Text("Sort music", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        listOf(
            LibrarySort.TITLE to "Title",
            LibrarySort.ARTIST to "Artist",
            LibrarySort.ALBUM to "Album",
            LibrarySort.DURATION to "Duration",
            LibrarySort.DATE_ADDED to "Date added",
            LibrarySort.FILE_SIZE to "File size",
            LibrarySort.TRACK_NUMBER to "Track number",
        ).forEach { (sort, label) ->
            SettingsRow(Icons.AutoMirrored.Outlined.List, label, checked = selected == sort) { onSelect(sort) }
        }
    }
}

@Composable
private fun MediaActionsSheet(
    item: MediaItem,
    playback: PlaybackController,
    library: LibraryViewModel,
    onDismiss: () -> Unit,
    playlistsViewModel: PlaylistViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val playlists by playlistsViewModel.playlists.collectAsState()
    BottomSheet(onDismiss) {
        Text(item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        SettingsRow(Icons.Outlined.SkipNext, "Play next", onClick = { playback.playNext(listOf(item)); onDismiss() })
        SettingsRow(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to queue", onClick = { playback.addToQueue(listOf(item)); onDismiss() })
        if (playlists.isNotEmpty()) {
            Text("Add to playlist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            playlists.forEach { playlist ->
                SettingsRow(Icons.AutoMirrored.Outlined.QueueMusic, playlist.name, detail = "${playlist.items.size} items", onClick = {
                    playlistsViewModel.add(playlist, listOf(item))
                    onDismiss()
                })
            }
        }
        SettingsRow(Icons.Outlined.Favorite, if (item.isFavorite) "Remove favorite" else "Add favorite", onClick = { library.toggleFavorite(item); onDismiss() })
        if (item.uri.startsWith("content://") || item.uri.startsWith("http://") || item.uri.startsWith("https://")) {
            SettingsRow(Icons.Outlined.Share, "Share media reference", onClick = {
                val uri = android.net.Uri.parse(item.uri)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    if (uri.scheme == "content") {
                        type = item.mimeType ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri(item.title, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.uri)
                    }
                }
                context.startActivity(Intent.createChooser(intent, "Share ${item.title}"))
                onDismiss()
            })
        }
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Info, null) },
            headlineContent = { Text("Media information") },
            supportingContent = {
                Text(
                    listOfNotNull(
                        item.fileName?.let { "File: $it" },
                        item.artist ?: "Unknown artist",
                        item.album ?: "Unknown album",
                        item.mimeType,
                        item.durationMillis?.let(::formatDuration),
                        item.sizeBytes?.let(::formatBytes),
                        item.width?.let { width -> item.height?.let { height -> "${width}×$height" } },
                        item.frameRate?.let { "$it fps" },
                        item.bitrate?.let { "${it / 1_000} kbps" },
                        item.folderPath?.let { "Folder: $it" },
                        item.trackNumber?.let { "Track $it" },
                        item.discNumber?.let { "Disc $it" },
                        item.dateAddedEpochSeconds?.let { "Added: $it" },
                        item.dateModifiedEpochSeconds?.let { "Modified: $it" },
                    ).joinToString(" · ") +
                        "\nMetadata is read-only for this source. Vimivo never modifies the file without reliable write access and explicit confirmation.",
                )
            },
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return if (totalSeconds >= 3_600) "%d:%02d:%02d".format(totalSeconds / 3_600, totalSeconds / 60 % 60, totalSeconds % 60)
    else "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "${bytes / 1024} KB"
}

@Composable
private fun NetworkScreen(viewModel: NetworkViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var addingWebDav by remember { mutableStateOf(false) }
    if (addingWebDav) {
        WebDavLocationDialog(
            onDismiss = { addingWebDav = false },
            onSave = { name, endpoint, username, password ->
                viewModel.addWebDavLocation(name, endpoint, username, password)
                addingWebDav = false
            },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Network locations", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                OutlinedButton({ addingWebDav = true }) { Text("Add WebDAV") }
            }
            Text("WebDAV uses HTTPS. Passwords are encrypted with Android Keystore and are never exported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.locations.isNotEmpty()) {
            items(state.locations, key = { "location:${it.id}" }) { location ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.browse(location) },
                    headlineContent = { Text(location.name) },
                    supportingContent = { Text(location.endpoint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Outlined.Folder, null) },
                    trailingContent = { IconButton({ viewModel.removeLocation(location) }) { Icon(Icons.Outlined.Delete, "Remove location") } },
                )
            }
        }
        state.browsingLocation?.let { activeLocation ->
            item { SectionTitle("${activeLocation.name} files") }
            if (state.isBrowsing) item { LoadingIndicator("Browsing network folder") }
            state.browserMessage?.let { message -> item { ErrorState(message) } }
            items(state.directoryEntries, key = { it.uri }) { entry ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.openRemote(entry) },
                    headlineContent = { Text(entry.name) },
                    supportingContent = { Text(if (entry.isDirectory) "Folder" else "Media file") },
                    leadingContent = { Icon(if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.PlayArrow, null) },
                )
            }
        }
        item {
            Text("Open network stream", style = MaterialTheme.typography.headlineMedium)
            SearchBar(state.url, viewModel::setUrl)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ viewModel.open() }, enabled = state.url.isNotBlank()) { Text("Play stream") }
                OutlinedButton({
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(viewModel::setUrl)
                }) { Text("Paste") }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.currentUrl != null) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.currentUrl!!, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append(state.status.name.lowercase().replaceFirstChar(Char::uppercase))
                                if (state.isLive) append(" · Live")
                                else append(if (state.isSeekable) " · Seekable" else " · Not seekable")
                                if (state.retryAttempt > 0) append(" · Reconnect ${state.retryAttempt}/3")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.status in setOf(StreamPlaybackStatus.CONNECTING, StreamPlaybackStatus.BUFFERING)) {
                            LoadingIndicator(if (state.status == StreamPlaybackStatus.BUFFERING) "Buffering stream" else "Connecting")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(viewModel::togglePlayback) {
                                Text(if (state.status == StreamPlaybackStatus.PLAYING) "Pause" else "Resume")
                            }
                            if (state.status == StreamPlaybackStatus.ERROR) {
                                Button(viewModel::retry) { Icon(Icons.Outlined.Refresh, null); Text(" Retry") }
                            }
                        }
                    }
                }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item { SectionTitle("Favorite streams") }
            items(state.favorites.toList(), key = { "favorite:$it" }) { url ->
                StreamRow(url, true, viewModel::open, { viewModel.toggleFavorite(url) })
            }
        }
        if (state.recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Recent streams") }
                    OutlinedButton(viewModel::clearHistory) { Text("Clear history") }
                }
            }
            items(state.recent, key = { "recent:$it" }) { url ->
                StreamRow(url, url in state.favorites, viewModel::open, { viewModel.toggleFavorite(url) }) {
                    viewModel.removeRecent(url)
                }
            }
        } else item { EmptyState(Icons.Outlined.Link, "No recent streams", "Authorized HTTP and HTTPS streams you open will appear here.") }
    }
}

@Composable
private fun WebDavLocationDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?, CharArray?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add WebDAV location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS URL") }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Username (optional)") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
            }
        },
        confirmButton = { Button({ onSave(name, endpoint, username.takeIf { it.isNotBlank() }, password.takeIf { it.isNotEmpty() }?.toCharArray()) }, enabled = endpoint.isNotBlank()) { Text("Save") } },
        dismissButton = { OutlinedButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StreamRow(
    url: String,
    favorite: Boolean,
    play: (String) -> Unit,
    toggleFavorite: () -> Unit,
    remove: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.clickable { play(url) },
        headlineContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Outlined.Link, null) },
        trailingContent = {
            Row {
                IconButton(toggleFavorite) { Icon(Icons.Outlined.Favorite, if (favorite) "Remove favorite" else "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                remove?.let { action -> IconButton(action) { Icon(Icons.Outlined.Delete, "Remove from history") } }
            }
        },
    )
}

@Composable
private fun SettingsScreen(
    viewModel: SettingsViewModel,
    networkViewModel: NetworkViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsState()
    val operations by viewModel.operations.collectAsState()
    val audio by viewModel.audio.collectAsState()
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var confirmationText by remember { mutableStateOf("") }
    var exclusionDialog by remember { mutableStateOf(false) }
    var exclusionPath by remember { mutableStateOf("") }
    var noticesVisible by remember { mutableStateOf(false) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::exportBackup)
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::restoreBackup)
    }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { SectionTitle(stringResource(R.string.settings_appearance)) }
        item { SettingsRow(Icons.Outlined.Palette, stringResource(R.string.theme), settingLabel(settings.themeMode), onClick = { appearance = true }) }
        item { SettingsRow(Icons.Outlined.Palette, stringResource(R.string.accent_color), settingLabel(settings.accentColor), onClick = { viewModel.setAccentColor(settings.accentColor.next()) }) }
        item { SettingsRow(Icons.Outlined.DarkMode, stringResource(R.string.dynamic_color), checked = settings.dynamicColor) { viewModel.setDynamicColor(!settings.dynamicColor) } }
        item { SettingsRow(Icons.Outlined.Palette, stringResource(R.string.artwork_accents), checked = settings.artworkBasedAccents) { viewModel.setArtworkAccents(!settings.artworkBasedAccents) } }
        item { SettingsRow(Icons.Outlined.Palette, stringResource(R.string.reduce_animations), checked = settings.reduceAnimations) { viewModel.setReduceAnimations(!settings.reduceAnimations) } }
        item { SettingsRow(Icons.Outlined.Palette, stringResource(R.string.high_contrast), checked = settings.highContrast) { viewModel.setHighContrast(!settings.highContrast) } }
        item { SettingsRow(Icons.Outlined.Palette, stringResource(R.string.larger_targets), checked = settings.largerTouchTargets) { viewModel.setLargerTouchTargets(!settings.largerTouchTargets) } }

        item { SectionTitle(stringResource(R.string.settings_music)) }
        item { SettingsRow(Icons.Outlined.Shuffle, stringResource(R.string.shuffle_behavior), settingLabel(settings.shuffleBehavior), onClick = { viewModel.setShuffleBehavior(settings.shuffleBehavior.next()) }) }
        item { SettingsRow(Icons.Outlined.Repeat, stringResource(R.string.repeat_behavior), settingLabel(settings.repeatBehavior), onClick = { viewModel.setRepeatBehavior(settings.repeatBehavior.next()) }) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.resume_behavior), settingLabel(settings.musicResumeBehavior), onClick = { viewModel.setMusicResume(settings.musicResumeBehavior.next()) }) }
        item { SettingsRow(Icons.AutoMirrored.Outlined.List, stringResource(R.string.default_sort), settingLabel(settings.defaultLibrarySort), onClick = { viewModel.setDefaultSort(settings.defaultLibrarySort.next()) }) }
        item { SettingsRow(Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.queue_restoration), checked = settings.restoreQueue) { viewModel.setQueueRestoration(!settings.restoreQueue) } }

        item { SectionTitle(stringResource(R.string.settings_video)) }
        item { SettingsRow(Icons.Outlined.VideoLibrary, stringResource(R.string.default_speed), "${settings.defaultVideoSpeed}×", onClick = { viewModel.setDefaultVideoSpeed(nextSpeed(settings.defaultVideoSpeed)) }) }
        item { SettingsRow(Icons.Outlined.SkipNext, stringResource(R.string.double_tap_seek), "${settings.doubleTapSeekSeconds} s", onClick = { viewModel.setDoubleTapSeekSeconds(nextSeek(settings.doubleTapSeekSeconds)) }) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.auto_rotation), settingLabel(settings.videoRotationBehavior), onClick = { viewModel.setVideoRotation(settings.videoRotationBehavior.next()) }) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.resume_behavior), settingLabel(settings.videoResumeBehavior), onClick = { viewModel.setVideoResume(settings.videoResumeBehavior.next()) }) }
        item { SettingsRow(Icons.Outlined.VideoLibrary, stringResource(R.string.aspect_ratio), settingLabel(settings.videoAspectPreference), onClick = { viewModel.setVideoAspect(settings.videoAspectPreference.next()) }) }
        item { SettingsRow(Icons.Outlined.Settings, stringResource(R.string.decoder), settingLabel(settings.decoderPreference), onClick = { viewModel.setDecoder(settings.decoderPreference.next()) }) }
        item {
            Text(stringResource(R.string.gesture_sensitivity), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Slider(settings.videoGestureSensitivity, viewModel::setVideoGestureSensitivity, valueRange = 0.5f..2f, modifier = Modifier.padding(horizontal = 20.dp))
        }

        item { SectionTitle(stringResource(R.string.settings_audio)) }
        item { EditableLanguageRow(stringResource(R.string.preferred_audio_language), settings.preferredAudioLanguage, viewModel::setPreferredAudioLanguage) }
        item { SettingsRow(Icons.Outlined.MusicNote, stringResource(R.string.audio_delay), "${settings.audioDelayMillis} ms", onClick = { viewModel.setAudioDelay(nextDelay(settings.audioDelayMillis)) }) }
        if (audio.capabilities.graphicEqualizer) {
            item {
                SettingsRow(
                    Icons.Outlined.MusicNote,
                    stringResource(R.string.equalizer),
                    audio.route.name.lowercase().replace('_', ' '),
                    checked = settings.audioEnhancements.enabled,
                ) { viewModel.setAudioEnhancements(settings.audioEnhancements.copy(enabled = !settings.audioEnhancements.enabled)) }
            }
            item {
                SettingsRow(
                    Icons.Outlined.MusicNote,
                    stringResource(R.string.equalizer_preset),
                    settings.audioEnhancements.preset.name.lowercase().replace('_', ' '),
                    onClick = {
                        viewModel.setAudioEnhancements(
                            settings.audioEnhancements.copy(preset = settings.audioEnhancements.preset.next()),
                        )
                    },
                )
            }
            if (audio.capabilities.bassEnhancement) item {
                AudioSlider(stringResource(R.string.bass_enhancement), settings.audioEnhancements.bassStrength, 0f..1f) {
                    viewModel.setAudioEnhancements(settings.audioEnhancements.copy(bassStrength = it))
                }
            }
            if (audio.capabilities.trebleAdjustment) item {
                AudioSlider(stringResource(R.string.treble), settings.audioEnhancements.trebleGainDb, -6f..6f) {
                    viewModel.setAudioEnhancements(settings.audioEnhancements.copy(trebleGainDb = it))
                }
            }
            if (audio.capabilities.attenuatingPreamp) item {
                AudioSlider(stringResource(R.string.preamp), settings.audioEnhancements.preampDb, -12f..0f) {
                    viewModel.setAudioEnhancements(settings.audioEnhancements.copy(preampDb = it))
                }
            }
            if (settings.audioEnhancements.preset == dev.vimivo.playback.EqualizerPreset.CUSTOM) {
                audio.availableBandsHz.forEach { frequency ->
                    item {
                        val current = settings.audioEnhancements.bands.minByOrNull {
                            kotlin.math.abs(it.centerFrequencyHz - frequency)
                        }?.gainDb ?: 0f
                        AudioSlider("${frequency} Hz", current, audio.minimumBandGainDb..audio.maximumBandGainDb) { gain ->
                            val bands = settings.audioEnhancements.bands.filterNot { it.centerFrequencyHz == frequency } +
                                dev.vimivo.playback.EqualizerBand(frequency, gain)
                            viewModel.setAudioEnhancements(settings.audioEnhancements.copy(bands = bands))
                        }
                    }
                }
            }
            audio.message?.let { message -> item { Text(message, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            item { Text(audio.capabilities.limitation, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { SettingsRow(Icons.Outlined.MusicNote, stringResource(R.string.audio_visualizer), checked = settings.audioVisualizerEnabled) { viewModel.setAudioVisualizer(!settings.audioVisualizerEnabled) } }

        item { SectionTitle(stringResource(R.string.settings_subtitles)) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.subtitles_enabled), checked = settings.subtitlesEnabled) { viewModel.setSubtitlesEnabled(!settings.subtitlesEnabled) } }
        item { EditableLanguageRow(stringResource(R.string.preferred_subtitle_language), settings.preferredSubtitleLanguage, viewModel::setPreferredSubtitleLanguage) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.subtitle_size), "${settings.subtitleAppearance.textSizeSp.toInt()} sp", onClick = { viewModel.setSubtitleAppearance(settings.subtitleAppearance.copy(textSizeSp = nextSubtitleSize(settings.subtitleAppearance.textSizeSp))) }) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.subtitle_position), "${(settings.subtitleAppearance.positionFraction * 100).toInt()}%", onClick = { viewModel.setSubtitleAppearance(settings.subtitleAppearance.copy(positionFraction = nextSubtitlePosition(settings.subtitleAppearance.positionFraction))) }) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.subtitle_style), settingLabel(settings.subtitleAppearance.edgeStyle), onClick = { viewModel.setSubtitleAppearance(settings.subtitleAppearance.copy(edgeStyle = settings.subtitleAppearance.edgeStyle.next())) }) }

        item { SectionTitle(stringResource(R.string.settings_library)) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.rescan_library), onClick = viewModel::rescan) }
        item { SettingsRow(Icons.Outlined.Folder, stringResource(R.string.folder_exclusions), "${operations.exclusions.size}", onClick = { exclusionDialog = true }) }
        operations.exclusions.forEach { path -> item { SettingsRow(Icons.Outlined.Folder, path, stringResource(R.string.remove), onClick = { viewModel.includeFolder(path) }) } }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.hidden_media), settingLabel(settings.hiddenMediaBehavior), onClick = { viewModel.setHiddenMedia(settings.hiddenMediaBehavior.next()) }) }
        item { SettingsRow(Icons.Outlined.Delete, stringResource(R.string.clear_thumbnails), onClick = viewModel::clearArtworkCache) }
        item { SettingsRow(Icons.Outlined.Delete, stringResource(R.string.clean_missing), onClick = { confirmAction = "missing" }) }

        item { SectionTitle(stringResource(R.string.settings_network)) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.auto_reconnect), checked = settings.autoReconnectStreams) { viewModel.setAutoReconnect(!settings.autoReconnectStreams) } }

        item { SectionTitle(stringResource(R.string.settings_privacy)) }
        item { Text(stringResource(R.string.privacy_summary), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingsRow(Icons.Outlined.Delete, stringResource(R.string.clear_history), onClick = { confirmAction = "history" }) }
        item { SettingsRow(Icons.Outlined.Delete, stringResource(R.string.clear_streams), onClick = { confirmAction = "streams" }) }
        item { SettingsRow(Icons.Outlined.Delete, stringResource(R.string.clear_artwork), onClick = viewModel::clearArtworkCache) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.reset_settings), onClick = { confirmAction = "reset" }) }
        item { SettingsRow(Icons.Outlined.Delete, stringResource(R.string.clear_app_data), onClick = { confirmAction = "data" }) }

        item { SectionTitle(stringResource(R.string.settings_backup)) }
        item { Text(stringResource(R.string.backup_detail), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingsRow(Icons.Outlined.Share, stringResource(R.string.export_backup), onClick = { exportBackup.launch("vimivo-backup.json") }) }
        item { SettingsRow(Icons.Outlined.Refresh, stringResource(R.string.restore_backup), onClick = { restoreBackup.launch(arrayOf("application/json", "text/plain")) }) }

        item { SectionTitle(stringResource(R.string.settings_about)) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.app_version), version, onClick = {}) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.developer), stringResource(R.string.developer_value), onClick = {}) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.licenses), onClick = { noticesVisible = true }) }
        item { SettingsRow(Icons.Outlined.Info, stringResource(R.string.third_party_notices), onClick = { noticesVisible = true }) }
        item { SettingsRow(Icons.Outlined.Link, stringResource(R.string.privacy_policy), onClick = { context.openUrl("https://github.com/Vimivo/Player/blob/main/docs/PRIVACY.md") }) }
        item { SettingsRow(Icons.Outlined.Link, stringResource(R.string.project_website), "github.com/Vimivo/Player", onClick = { context.openUrl("https://github.com/Vimivo/Player") }) }
        operations.message?.let { message -> item { Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.primary) } }
    }
    if (appearance) BottomSheet({ appearance = false }) {
        Text("Choose theme", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        ThemeMode.entries.forEach { mode ->
            SettingsRow(
                when (mode) { ThemeMode.LIGHT -> Icons.Outlined.LightMode; ThemeMode.DARK, ThemeMode.AMOLED -> Icons.Outlined.DarkMode; else -> Icons.Outlined.Palette },
                settingLabel(mode),
                checked = settings.themeMode == mode,
            ) { viewModel.setTheme(mode); appearance = false }
        }
    }
    if (exclusionDialog) PlaylistNameDialog(stringResource(R.string.add_exclusion), exclusionPath, {
        exclusionPath = it
        viewModel.excludeFolder(it)
        exclusionDialog = false
    }) { exclusionDialog = false }
    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null; confirmationText = "" },
            title = { Text(stringResource(R.string.confirm)) },
            text = {
                Column {
                    Text(if (action == "data") stringResource(R.string.type_confirm) else stringResource(R.string.irreversible_warning))
                    if (action == "data") OutlinedTextField(confirmationText, { confirmationText = it })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            "history" -> viewModel.clearPlaybackHistory()
                            "streams" -> networkViewModel.clearHistory()
                            "reset" -> viewModel.reset()
                            "missing" -> viewModel.cleanMissing()
                            "data" -> context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
                        }
                        confirmAction = null
                        confirmationText = ""
                    },
                    enabled = action != "data" || confirmationText == "CONFIRM",
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { OutlinedButton({ confirmAction = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (noticesVisible) AlertDialog(
        onDismissRequest = { noticesVisible = false },
        title = { Text(stringResource(R.string.third_party_notices)) },
        text = { Text(stringResource(R.string.notices_text)) },
        confirmButton = { Button({ noticesVisible = false }) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun EditableLanguageRow(title: String, value: String, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    SettingsRow(Icons.Outlined.Info, title, value.ifBlank { stringResource(R.string.system_default) }, onClick = { editing = true })
    if (editing) PlaylistNameDialog(title, value, { onChange(it.trim()); editing = false }) { editing = false }
}

private inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}

@Composable
private fun settingLabel(value: Enum<*>): String = stringResource(
    when (value.name) {
        "SYSTEM" -> R.string.value_system
        "LIGHT" -> R.string.value_light
        "DARK" -> R.string.value_dark
        "AMOLED" -> R.string.value_amoled
        "VIOLET" -> R.string.value_violet
        "BLUE" -> R.string.value_blue
        "GREEN" -> R.string.value_green
        "ORANGE" -> R.string.value_orange
        "PINK" -> R.string.value_pink
        "OFF" -> R.string.value_off
        "ON" -> R.string.value_on
        "REMEMBER" -> R.string.value_remember
        "ALL" -> R.string.value_all
        "ONE" -> R.string.value_one
        "NEVER" -> R.string.value_never
        "ALWAYS" -> R.string.value_always
        "AUTOMATIC" -> R.string.value_automatic
        "HARDWARE" -> R.string.value_hardware
        "SOFTWARE" -> R.string.value_software
        "FOLLOW_SYSTEM" -> R.string.value_follow_system
        "SENSOR" -> R.string.value_sensor
        "LOCK_CURRENT" -> R.string.value_lock_current
        "FIT" -> R.string.value_fit
        "FILL" -> R.string.value_fill
        "ORIGINAL" -> R.string.value_original
        "RATIO_16_9" -> R.string.value_ratio_16_9
        "RATIO_4_3" -> R.string.value_ratio_4_3
        "CROP" -> R.string.value_crop
        "HIDE" -> R.string.value_hide
        "SHOW" -> R.string.value_show
        "OUTLINE" -> R.string.value_outline
        "DROP_SHADOW" -> R.string.value_drop_shadow
        "NONE" -> R.string.value_none
        "TITLE" -> R.string.value_title
        "ARTIST" -> R.string.value_artist
        "ALBUM" -> R.string.value_album
        "DURATION" -> R.string.value_duration
        "DATE_ADDED" -> R.string.value_date_added
        "DATE_MODIFIED" -> R.string.value_date_modified
        "FILE_SIZE" -> R.string.value_file_size
        "TRACK_NUMBER" -> R.string.value_track_number
        "LAST_PLAYED" -> R.string.value_last_played
        else -> R.string.value_automatic
    },
)
private fun nextSpeed(value: Float): Float = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).let { values ->
    val current = values.indexOf(value).coerceAtLeast(1)
    values[(current + 1) % values.size]
}
private fun nextSeek(value: Int) = listOf(5, 10, 15, 30, 60).let { it[(it.indexOf(value).coerceAtLeast(0) + 1) % it.size] }
private fun nextDelay(value: Long) = if (value >= 2_000) -2_000 else value + 500
private fun nextSubtitleSize(value: Float) = if (value >= 32f) 14f else value + 2f
private fun nextSubtitlePosition(value: Float) = if (value >= 0.24f) 0.04f else value + 0.04f

@Composable
private fun AudioSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text("$label · ${"%.1f".format(value)}")
        Slider(value, onValueChange, valueRange = range)
    }
}

/** Low-rate playback activity indicator; it intentionally avoids microphone/Visualizer permissions. */
@Composable
private fun PlaybackActivityVisualizer(positionMillis: Long, playing: Boolean) {
    Row(
        Modifier.height(36.dp).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(18) { index ->
            val phase = if (playing) positionMillis / 250.0 + index * 0.83 else index * 0.2
            val height = if (playing) 6 + (kotlin.math.abs(kotlin.math.sin(phase)) * 22).toInt() else 6
            Box(
                Modifier.width(3.dp).height(height.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}
private fun android.content.Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}

@Composable
private fun GlobalSearch(
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    onClose: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
    playlistViewModel: PlaylistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playlistResults by playlistViewModel.searchResults.collectAsState()
    var folderFilter by remember { mutableStateOf(state.query.folderPath.orEmpty()) }
    LaunchedEffect(state.query.text) { playlistViewModel.search(state.query.text) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SearchBar(state.query.text, viewModel::search, onClose)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { AssistChip({ viewModel.filterSearch(kind = null) }, { Text("All") }) }
            item { AssistChip({ viewModel.filterSearch(kind = MediaKind.AUDIO) }, { Text("Audio") }) }
            item { AssistChip({ viewModel.filterSearch(kind = MediaKind.VIDEO) }, { Text("Video") }) }
            item { AssistChip({ viewModel.filterSearch(favoritesOnly = !state.query.favoritesOnly) }, { Text("Favorites") }) }
            item { AssistChip({ viewModel.filterSearch(recentlyAdded = true) }, { Text("Recently added") }) }
            item { AssistChip({ viewModel.filterSearch(minimumDurationMillis = 30 * 60_000L) }, { Text("30+ min") }) }
            item { AssistChip({ viewModel.filterSearch(mimeType = "audio/mpeg") }, { Text("MP3") }) }
            item { AssistChip({ viewModel.filterSearch(mimeType = "video/mp4") }, { Text("MP4") }) }
            item {
                AssistChip(
                    { folderFilter = ""; viewModel.filterSearch(minimumDurationMillis = null, maximumDurationMillis = null, mimeType = null, folderPath = null) },
                    { Text("Clear filters") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = folderFilter,
                onValueChange = { folderFilter = it },
                label = { Text("Folder path") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton({ viewModel.filterSearch(folderPath = folderFilter.trim().ifEmpty { null }) }) { Text("Apply") }
        }
        if (state.query.text.isNotBlank()) {
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(playlistResults, key = { "playlist:${it.id}" }) { playlist ->
                    ListItem(
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) },
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("Playlist · ${playlist.items.size} items") },
                        modifier = Modifier.clickable {
                            playlistViewModel.load(playlist)
                            onClose()
                        },
                    )
                }
                items(state.items.take(20), key = { it.id.value }) { SongRow(it, thumbnails, { playback.play(it); onClose() }) }
                if (!state.isLoading && state.items.isEmpty() && playlistResults.isEmpty()) item { EmptyState(title = "No results", detail = "Try a different title, artist, album, folder, filename, or playlist.") }
            }
        }
    }
}

@Composable
private fun NowPlayingSheet(
    player: PlaybackUiState,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    viewModel: PlaybackViewModel,
    artworkAccents: Boolean,
    visualizerEnabled: Boolean,
    onDismiss: () -> Unit,
    openQueue: () -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    val sleepTimer by viewModel.sleepTimerState.collectAsState()
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::loadLyrics)
    }
    val controller by playback.controller.collectAsState()
    val currentDomain = player.current?.toDomain()
    val artworkAccent by produceState<Int?>(null, currentDomain?.uri, currentDomain?.artworkUri, artworkAccents) {
        value = if (artworkAccents) currentDomain?.let { thumbnails.dominantColor(it.uri, it.artworkUri) } else null
    }
    if (!settingsVisible) Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val current = player.current
        Column(
            Modifier.fillMaxWidth()
                .background(artworkAccent?.let { Color(it).copy(alpha = 0.16f) } ?: Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, "Close player") }
            }
            if (current != null) {
                val media = current.toDomain()
                if (media.kind == MediaKind.VIDEO && controller != null) {
                    AndroidView(
                        factory = { PlayerView(it).apply { this.player = controller } },
                        update = { it.player = controller },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(vertical = 12.dp),
                    )
                } else {
                    MediaArtwork(media, thumbnails, Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 20.dp))
                }
                Text(current.mediaMetadata.title?.toString() ?: current.mediaId, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                Text(current.mediaMetadata.artist?.toString() ?: "Unknown artist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                current.mediaMetadata.albumTitle?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (visualizerEnabled && media.kind == MediaKind.AUDIO) {
                    PlaybackActivityVisualizer(player.positionMillis, player.isPlaying)
                }
                player.lyrics?.let { lyrics ->
                    val center = player.currentLyricIndex.coerceAtLeast(0)
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        lyrics.lines.subList((center - 2).coerceAtLeast(0), (center + 3).coerceAtMost(lyrics.lines.size)).forEachIndexed { offset, line ->
                            val actual = (center - 2).coerceAtLeast(0) + offset
                            Text(
                                line.text,
                                color = if (actual == player.currentLyricIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = if (actual == player.currentLyricIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable(enabled = line.timeMillis != null) { line.timeMillis?.let(viewModel::seekTo) }.padding(4.dp),
                            )
                        }
                    }
                }
                player.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
                PlayerControls(player.isPlaying, player.positionMillis, player.durationMillis, viewModel::previous, viewModel::togglePlayback, viewModel::next, viewModel::seekTo)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    IconButton(viewModel::toggleShuffle) { Icon(Icons.Outlined.Shuffle, "Shuffle", tint = if (player.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    IconButton(viewModel::cycleRepeat) { Icon(Icons.Outlined.Repeat, "Repeat mode") }
                    IconButton(openQueue) { Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Open queue") }
                    IconButton(viewModel::toggleFavorite) { Icon(Icons.Outlined.Favorite, if (player.isFavorite) "Remove favorite" else "Favorite", tint = if (player.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    IconButton({ settingsVisible = true }) { Icon(Icons.Outlined.MoreVert, "Playback settings") }
                }
            }
        }
        }
    }
    else {
        var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
        BottomSheet({ settingsVisible = false }) {
        Text("Playback settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                AssistChip({ viewModel.setSpeed(speed) }, { Text("${speed}×") })
            }
        }
        SettingsRow(Icons.Outlined.Shuffle, "Shuffle", checked = player.shuffleEnabled, onClick = viewModel::toggleShuffle)
        SettingsRow(Icons.Outlined.Repeat, "Repeat", detail = when (player.repeatMode) { Player.REPEAT_MODE_ONE -> "One"; Player.REPEAT_MODE_ALL -> "All"; else -> "Off" }, onClick = viewModel::cycleRepeat)
        SettingsRow(Icons.Outlined.BookmarkAdd, "Save bookmark", detail = formatDuration(player.positionMillis), onClick = {
            viewModel.addBookmark("Bookmark at ${formatDuration(player.positionMillis)}")
        })
        SettingsRow(Icons.Outlined.MusicNote, "Load LRC lyrics", detail = "Local synchronized or plain lyrics", onClick = {
            lyricsPicker.launch(arrayOf("text/plain", "application/octet-stream"))
        })
        Text("Sleep timer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(15, 30, 60).forEach { minutes -> AssistChip({ viewModel.startSleepTimer(minutes, 10) }, { Text("$minutes min") }) }
        }
        SettingsRow(Icons.Outlined.MusicNote, "Stop after current track", onClick = { viewModel.stopAfterCurrent(10) })
        if (sleepTimer.deadlineEpochMillis != null || sleepTimer.stopAfterCurrent) {
            SettingsRow(Icons.Outlined.Close, "Cancel sleep timer", detail = sleepTimer.remainingMillis?.let(::formatDuration) ?: "After current track", onClick = viewModel::cancelSleepTimer)
        }
        if (player.bookmarks.isNotEmpty()) {
            Text("Bookmarks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
            player.bookmarks.forEach { bookmark ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.seekToBookmark(bookmark); settingsVisible = false },
                    headlineContent = { Text(bookmark.name) },
                    supportingContent = { Text(formatDuration(bookmark.positionMillis)) },
                    trailingContent = {
                        Row {
                            IconButton({ editingBookmark = bookmark }) {
                                Icon(Icons.Outlined.Edit, "Rename bookmark")
                            }
                            IconButton({ viewModel.deleteBookmark(bookmark) }) {
                                Icon(Icons.Outlined.Delete, "Delete bookmark")
                            }
                        }
                    },
                )
            }
        }
        }
        editingBookmark?.let { bookmark ->
            PlaylistNameDialog("Rename bookmark", bookmark.name, {
                viewModel.renameBookmark(bookmark, it)
                editingBookmark = null
            }) { editingBookmark = null }
        }
    }
}

@Composable
private fun QueueSheet(player: PlaybackUiState, viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    BottomSheet(onDismiss) {
        Text("Up next", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        player.queue.forEachIndexed { index, item ->
            ListItem(
                modifier = Modifier.clickable { viewModel.playQueueIndex(index) },
                headlineContent = { Text(item.mediaMetadata.title?.toString() ?: item.mediaId, maxLines = 1) },
                supportingContent = { if (index == player.currentIndex) Text("Playing now", color = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Row {
                        IconButton({ if (index > 0) viewModel.moveQueueItem(index, index - 1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                        IconButton({ if (index < player.queue.lastIndex) viewModel.moveQueueItem(index, index + 1) }, enabled = index < player.queue.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
                        IconButton({ viewModel.removeQueueItem(index) }) { Icon(Icons.Outlined.MoreVert, "Remove from queue") }
                    }
                },
            )
        }
    }
}

internal fun androidx.media3.common.MediaItem.toDomain() = MediaItem(
    id = dev.vimivo.core.model.MediaId(mediaId),
    uri = localConfiguration?.uri?.toString().orEmpty(),
    title = mediaMetadata.title?.toString() ?: mediaId,
    kind = if (localConfiguration?.mimeType?.startsWith("video/") == true) MediaKind.VIDEO else MediaKind.AUDIO,
    artist = mediaMetadata.artist?.toString(),
    album = mediaMetadata.albumTitle?.toString(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
)

private fun HomeSection.label() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
