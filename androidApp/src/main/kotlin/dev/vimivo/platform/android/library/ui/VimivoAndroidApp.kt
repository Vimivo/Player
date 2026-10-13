@file:Suppress("ArgumentListWrapping", "ImportOrdering", "LongParameterList", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.library.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.LibraryStatus
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaPermissionAccess
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.LibraryViewModel
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.playback.ui.PlaybackDevelopmentScreen
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.ui.theme.VimivoTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class DevelopmentDestination { LIBRARY, PLAYER }

@Composable
fun VimivoAndroidApp(
    playback: PlaybackEngineManager,
    thumbnails: AndroidThumbnailRepository,
    permissions: MediaPermissionPolicy,
) = VimivoTheme {
    var destination by remember { mutableStateOf(DevelopmentDestination.LIBRARY) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == DevelopmentDestination.LIBRARY,
                    onClick = { destination = DevelopmentDestination.LIBRARY },
                    icon = { Text("♫") },
                    label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = destination == DevelopmentDestination.PLAYER,
                    onClick = { destination = DevelopmentDestination.PLAYER },
                    icon = { Text("▶") },
                    label = { Text("Player") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (destination) {
                DevelopmentDestination.LIBRARY -> LibraryScreen(playback, thumbnails, permissions)
                DevelopmentDestination.PLAYER -> PlaybackDevelopmentScreen(playback)
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    playback: PlaybackEngineManager,
    thumbnails: AndroidThumbnailRepository,
    permissionPolicy: MediaPermissionPolicy,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.permissionsChanged()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importFile(uri.toString())
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importFolder(uri.toString())
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Local library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text("Vimivo indexes metadata only. Your media stays in shared storage.")
        PermissionPanel(
            audioDenied = state.permissions.audio == MediaPermissionAccess.DENIED,
            videoLimited = state.permissions.video != MediaPermissionAccess.FULL,
            partialVideo = state.permissions.video == MediaPermissionAccess.PARTIAL,
            onRequestAudio = { permissionLauncher.launch(permissionPolicy.audioRequest()) },
            onRequestVideo = { permissionLauncher.launch(permissionPolicy.videoRequest()) },
            onOpenFile = { filePicker.launch(arrayOf("audio/*", "video/*")) },
            onOpenFolder = { folderPicker.launch(null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.selectKind(MediaKind.AUDIO) }) { Text("Songs") }
            Button(onClick = { viewModel.selectKind(MediaKind.VIDEO) }) { Text("Videos") }
            OutlinedButton(onClick = viewModel::rescan) { Text("Rescan") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.showCategories(LibraryCategoryField.ALBUM) }) { Text("Albums") }
            OutlinedButton(onClick = { viewModel.showCategories(LibraryCategoryField.ARTIST) }) { Text("Artists") }
            OutlinedButton(onClick = { viewModel.showCategories(LibraryCategoryField.GENRE) }) { Text("Genres") }
            OutlinedButton(onClick = { viewModel.showCategories(LibraryCategoryField.FOLDER) }) { Text("Folders") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::recentlyAdded) { Text("Recent videos") }
            OutlinedButton(onClick = viewModel::recentlyWatched) { Text("Watched") }
        }
        OutlinedTextField(
            value = state.query.text,
            onValueChange = viewModel::search,
            label = { Text("Search title, artist, or album") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        StatusLine(state.status, state.totalCount)
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.fillMaxSize()) {
            state.categories?.let { categories ->
                items(categories, key = { it.name }) { category ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.selectCategory(category) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(category.name)
                        Text(category.itemCount.toString())
                    }
                    HorizontalDivider()
                }
            } ?: items(state.items, key = { it.id.value }) { item ->
                MediaRow(
                    item = item,
                    thumbnails = thumbnails,
                    onFavorite = { viewModel.toggleFavorite(item) },
                    onPlay = {
                        scope.launch {
                            playback.open(item, item.lastPlaybackPositionMillis).onSuccess { playback.play() }
                        }
                    },
                )
                HorizontalDivider()
            }
            if (state.hasMore) {
                item {
                    OutlinedButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isLoading) "Loading…" else "Load more")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    audioDenied: Boolean,
    videoLimited: Boolean,
    partialVideo: Boolean,
    onRequestAudio: () -> Unit,
    onRequestVideo: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    if (audioDenied || videoLimited) {
        Text(
            if (partialVideo) {
                "Only selected videos are visible. You can change the selection or use the system file picker."
            } else {
                "Library access lets Vimivo find shared music and videos. It is optional; system pickers work without it."
            },
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (audioDenied) OutlinedButton(onClick = onRequestAudio) { Text("Allow music") }
        if (videoLimited) OutlinedButton(onClick = onRequestVideo) { Text("Allow videos") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onOpenFile) { Text("Open file") }
        OutlinedButton(onClick = onOpenFolder) { Text("Add folder") }
    }
}

@Composable
private fun StatusLine(status: LibraryStatus, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        if (status is LibraryStatus.Scanning) CircularProgressIndicator(Modifier.size(18.dp))
        Text(
            when (status) {
                LibraryStatus.Idle -> "$total items"
                is LibraryStatus.Scanning -> "  Scanning ${status.source}: ${status.processed}"
                is LibraryStatus.Ready -> "${status.itemCount} indexed items"
                is LibraryStatus.PermissionRequired -> "Library permission is optional"
                is LibraryStatus.Failed -> status.message
            },
        )
    }
}

@Composable
private fun MediaRow(
    item: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.uri, item.artworkUri) {
        value = thumbnails.load(item.uri, item.artworkUri)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(56.dp))
        } else {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Text(if (item.kind == MediaKind.VIDEO) "▶" else "♫") }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.artist, item.album, item.folderName).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onFavorite) { Text(if (item.isFavorite) "★" else "☆") }
    }
}
