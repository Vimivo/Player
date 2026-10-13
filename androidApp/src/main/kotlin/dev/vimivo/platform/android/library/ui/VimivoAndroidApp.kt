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
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Scaffold
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
import dev.vimivo.features.home.HomeViewModel
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.LibraryUiState
import dev.vimivo.platform.android.library.LibraryViewModel
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.library.PlaylistViewModel
import dev.vimivo.platform.android.network.NetworkViewModel
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
    settingsViewModel: SettingsViewModel = koinViewModel(),
    playbackViewModel: PlaybackViewModel = koinViewModel(),
) {
    val settings by settingsViewModel.state.collectAsState()
    val player by playbackViewModel.state.collectAsState()
    VimivoAndroidTheme(settings.themeMode, settings.dynamicColor) {
        AppShell(
            playback,
            thumbnails,
            permissions,
            settings.homeSections,
            player,
            playbackViewModel,
            settingsViewModel,
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
    player: PlaybackUiState,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
) {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var searchVisible by remember { mutableStateOf(false) }
    var nowPlayingVisible by remember { mutableStateOf(false) }
    var queueVisible by remember { mutableStateOf(false) }

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
                                onOpen = { nowPlayingVisible = true },
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
                            AppDestination.VIDEOS -> VideoScreen(thumbnails, playback, permissions)
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
    if (nowPlayingVisible) NowPlayingSheet(player, thumbnails, playback, playbackViewModel, { nowPlayingVisible = false }) {
        queueVisible = true
    }
    if (queueVisible) QueueSheet(player, playbackViewModel) { queueVisible = false }
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
            selectedPlaylist?.id == "__list__" -> PlaylistList(playlists) { playlist ->
                selectedPlaylist = playlist
                playlistViewModel.load(playlist)
            }
            selectedPlaylist != null -> PlaylistDetail(selectedPlaylist!!, playlistItems, thumbnails, playback) {
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
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.selectKind(MediaKind.VIDEO) }
    LibraryChrome(state, viewModel, permissions, MediaKind.VIDEO) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip({ viewModel.selectKind(MediaKind.VIDEO) }, { Text("All videos") })
            AssistChip({ viewModel.showCategories(LibraryCategoryField.FOLDER) }, { Text("Folders") }, leadingIcon = { Icon(Icons.Outlined.Folder, null) })
            AssistChip(viewModel::recentlyAdded, { Text("Recently added") })
            AssistChip(viewModel::recentlyWatched, { Text("Recently watched") })
        }
        if (state.categories != null) CategoryOrMediaList(state, viewModel, thumbnails, playback) else VideoGrid(state, thumbnails, playback, viewModel)
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
private fun VideoGrid(state: LibraryUiState, thumbnails: AndroidThumbnailRepository, playback: PlaybackController, viewModel: LibraryViewModel) {
    when {
        state.isLoading && state.items.isEmpty() -> LoadingIndicator("Loading videos")
        state.items.isEmpty() -> EmptyState(Icons.Outlined.VideoLibrary, "No videos found", "Add a folder, open a video, or grant library access.", "Rescan", viewModel::rescan)
        else -> LazyVerticalGrid(GridCells.Adaptive(240.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.items, key = { it.id.value }) { item -> VideoCard(item, thumbnails) { playback.play(item) } }
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
private fun PlaylistList(playlists: List<Playlist>, onSelect: (Playlist) -> Unit) {
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

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    media: List<MediaItem>,
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    onMore: (MediaItem) -> Unit,
) {
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
        LazyColumn {
            items(media, key = { it.id.value }) { item ->
                SongRow(item, thumbnails, { playback.playQueue(media, media.indexOf(item)) }, onMore = { onMore(item) })
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
) {
    val context = LocalContext.current
    BottomSheet(onDismiss) {
        Text(item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        SettingsRow(Icons.Outlined.SkipNext, "Play next", onClick = { playback.playNext(listOf(item)); onDismiss() })
        SettingsRow(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to queue", onClick = { playback.addToQueue(listOf(item)); onDismiss() })
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
                        item.artist ?: "Unknown artist",
                        item.album ?: "Unknown album",
                        item.mimeType,
                        item.durationMillis?.let(::formatDuration),
                        item.sizeBytes?.let(::formatBytes),
                    ).joinToString(" · "),
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Open URL", style = MaterialTheme.typography.headlineMedium)
            SearchBar(state.url, viewModel::setUrl)
            Button({ viewModel.open() }, enabled = state.url.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("Play stream") }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.favorites.isNotEmpty()) {
            item { SectionTitle("Favorite streams") }
            items(state.favorites.toList()) { url -> StreamRow(url, true, viewModel::open) { viewModel.toggleFavorite(url) } }
        }
        if (state.recent.isNotEmpty()) {
            item { SectionTitle("Recent streams") }
            items(state.recent) { url -> StreamRow(url, url in state.favorites, viewModel::open) { viewModel.toggleFavorite(url) } }
        } else item { EmptyState(Icons.Outlined.Link, "No recent streams", "Authorized HTTP and HTTPS streams you open will appear here.") }
    }
}

@Composable
private fun StreamRow(url: String, favorite: Boolean, play: (String) -> Unit, toggleFavorite: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { play(url) },
        headlineContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Outlined.Link, null) },
        trailingContent = { IconButton(toggleFavorite) { Icon(Icons.Outlined.Favorite, if (favorite) "Remove favorite" else "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

@Composable
private fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.state.collectAsState()
    var appearance by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("Appearance") }
        item { SettingsRow(Icons.Outlined.Palette, "Theme", settings.themeMode.name.lowercase().replaceFirstChar(Char::uppercase), onClick = { appearance = true }) }
        item { SettingsRow(Icons.Outlined.DarkMode, "Dynamic color", "Use colors from your Android wallpaper", settings.dynamicColor) { viewModel.setDynamicColor(!settings.dynamicColor) } }
        item { SectionTitle("Playback") }
        item { SettingsRow(Icons.Outlined.Repeat, "Resume playback", "Remember the last position", settings.resumePlayback) { viewModel.setResume(!settings.resumePlayback) } }
        item { SectionTitle("Home sections") }
        HomeSection.entries.forEach { section ->
            item {
                val visible = section in settings.homeSections
                ListItem(
                    headlineContent = { Text(section.label()) },
                    leadingContent = { androidx.compose.material3.Checkbox(visible, { viewModel.setSectionVisible(section, it) }) },
                    trailingContent = {
                        Row {
                            IconButton({ viewModel.moveSection(section, -1) }, enabled = visible) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                            IconButton({ viewModel.moveSection(section, 1) }, enabled = visible) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
                        }
                    },
                )
            }
        }
        item { SectionTitle("Privacy") }
        item { Text("Vimivo has no account, advertising, or analytics. Library metadata stays on this device.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (appearance) BottomSheet({ appearance = false }) {
        Text("Choose theme", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        ThemeMode.entries.forEach { mode ->
            SettingsRow(
                when (mode) { ThemeMode.LIGHT -> Icons.Outlined.LightMode; ThemeMode.DARK, ThemeMode.AMOLED -> Icons.Outlined.DarkMode; else -> Icons.Outlined.Palette },
                mode.name.lowercase().replaceFirstChar(Char::uppercase),
                checked = settings.themeMode == mode,
            ) { viewModel.setTheme(mode); appearance = false }
        }
    }
}

@Composable
private fun GlobalSearch(
    thumbnails: AndroidThumbnailRepository,
    playback: PlaybackController,
    onClose: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SearchBar(state.query.text, viewModel::search, onClose)
        if (state.query.text.isNotBlank()) {
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(state.items.take(20), key = { it.id.value }) { SongRow(it, thumbnails, { playback.play(it); onClose() }) }
                if (!state.isLoading && state.items.isEmpty()) item { EmptyState(title = "No results", detail = "Try a different title, artist, album, or video name.") }
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
    onDismiss: () -> Unit,
    openQueue: () -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    val controller by playback.controller.collectAsState()
    val currentDomain = player.current?.toDomain()
    val artworkAccent by produceState<Int?>(null, currentDomain?.uri, currentDomain?.artworkUri) {
        value = currentDomain?.let { thumbnails.dominantColor(it.uri, it.artworkUri) }
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
    else BottomSheet({ settingsVisible = false }) {
        Text("Playback settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(20.dp))
        Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                AssistChip({ viewModel.setSpeed(speed) }, { Text("${speed}×") })
            }
        }
        SettingsRow(Icons.Outlined.Shuffle, "Shuffle", checked = player.shuffleEnabled, onClick = viewModel::toggleShuffle)
        SettingsRow(Icons.Outlined.Repeat, "Repeat", detail = when (player.repeatMode) { Player.REPEAT_MODE_ONE -> "One"; Player.REPEAT_MODE_ALL -> "All"; else -> "Off" }, onClick = viewModel::cycleRepeat)
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

private fun androidx.media3.common.MediaItem.toDomain() = MediaItem(
    id = dev.vimivo.core.model.MediaId(mediaId),
    uri = localConfiguration?.uri?.toString().orEmpty(),
    title = mediaMetadata.title?.toString() ?: mediaId,
    kind = if (localConfiguration?.mimeType?.startsWith("video/") == true) MediaKind.VIDEO else MediaKind.AUDIO,
    artist = mediaMetadata.artist?.toString(),
    album = mediaMetadata.albumTitle?.toString(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
)

private fun HomeSection.label() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
