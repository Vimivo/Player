@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "LongParameterList",
    "MagicNumber", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.AlbumDetails
import dev.vimivo.core.model.AlbumItem
import dev.vimivo.core.model.ArtistDetails
import dev.vimivo.core.model.ArtistItem
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.Playlist

@Composable
internal fun DesktopAlbumsPanel(
    albums: List<AlbumItem>,
    onSelectAlbum: (AlbumItem) -> Unit,
    onPlayAll: (List<MediaItem>, String) -> Unit,
    onShufflePlay: (List<MediaItem>, String) -> Unit,
    onAddToQueue: (List<MediaItem>) -> Unit,
    loadTracks: (String) -> List<MediaItem>,
) {
    if (albums.isEmpty()) {
        Text("No albums found. Add and scan a folder with tagged music.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(albums, key = { it.id }) { album ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectAlbum(album) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopArtwork(album.artworkUri, album.title, 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(album.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                album.albumArtist ?: album.artist,
                                "${album.trackCount} track(s)",
                                formatDuration(album.totalDurationMillis),
                                album.releaseYear?.toString(),
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = {
                        val tracks = loadTracks(album.id)
                        if (tracks.isNotEmpty()) onPlayAll(tracks, album.title)
                    }) { Text("Play") }
                    OutlinedButton(onClick = {
                        val tracks = loadTracks(album.id)
                        if (tracks.isNotEmpty()) onShufflePlay(tracks, album.title)
                    }) { Text("Shuffle") }
                    OutlinedButton(onClick = {
                        val tracks = loadTracks(album.id)
                        if (tracks.isNotEmpty()) onAddToQueue(tracks)
                    }) { Text("+ Queue") }
                }
            }
        }
    }
}

@Composable
internal fun DesktopAlbumDetailsPanel(
    details: AlbumDetails,
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onPlayAll: (List<MediaItem>, String) -> Unit,
    onShufflePlay: (List<MediaItem>, String) -> Unit,
    onPlayNext: (List<MediaItem>) -> Unit,
    onAddToQueue: (List<MediaItem>) -> Unit,
    onPlayTrack: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onAddToPlaylist: (String, MediaItem) -> Unit,
) {
    val album = details.album
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to Albums") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopArtwork(album.artworkUri, album.title, 96.dp)
            Column(Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    album.albumArtist ?: album.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${album.trackCount} tracks · ${formatDuration(album.totalDurationMillis)}" +
                        (album.releaseYear?.let { " · $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlayAll(details.tracks, album.title) }, enabled = details.tracks.isNotEmpty()) {
                Text("Play All")
            }
            OutlinedButton(onClick = { onShufflePlay(details.tracks, album.title) }, enabled = details.tracks.isNotEmpty()) {
                Text("Shuffle")
            }
            OutlinedButton(onClick = { onPlayNext(details.tracks) }, enabled = details.tracks.isNotEmpty()) {
                Text("Play Next")
            }
            OutlinedButton(onClick = { onAddToQueue(details.tracks) }, enabled = details.tracks.isNotEmpty()) {
                Text("Add to Queue")
            }
        }
        Text("Track Listing", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(details.tracks, key = { it.id.value }) { track ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            track.trackNumber?.toString() ?: "•",
                            modifier = Modifier.width(28.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(track.title)
                            Text(
                                listOfNotNull(track.artist, track.durationMillis?.let(::formatDuration)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onPlayTrack(track) }) { Text("Play") }
                        OutlinedButton(onClick = { onPlayNext(listOf(track)) }) { Text("Next") }
                        OutlinedButton(onClick = { onAddToQueue(listOf(track)) }) { Text("+ Queue") }
                        OutlinedButton(onClick = { onToggleFavorite(track) }) {
                            Text(if (track.isFavorite) "★" else "☆")
                        }
                        AddToPlaylistButton(playlists, track, onAddToPlaylist)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopArtistsPanel(
    artists: List<ArtistItem>,
    onSelectArtist: (ArtistItem) -> Unit,
    onPlayAll: (List<MediaItem>, String) -> Unit,
    onShufflePlay: (List<MediaItem>, String) -> Unit,
    onAddToQueue: (List<MediaItem>) -> Unit,
    loadTracks: (String) -> List<MediaItem>,
) {
    if (artists.isEmpty()) {
        Text("No artists found. Add and scan a folder with tagged music.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(artists, key = { it.id }) { artist ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectArtist(artist) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopArtwork(artist.artworkUri, artist.name, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${artist.albumCount} album(s) · ${artist.trackCount} track(s)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = {
                        val tracks = loadTracks(artist.id)
                        if (tracks.isNotEmpty()) onPlayAll(tracks, artist.name)
                    }) { Text("Play") }
                    OutlinedButton(onClick = {
                        val tracks = loadTracks(artist.id)
                        if (tracks.isNotEmpty()) onShufflePlay(tracks, artist.name)
                    }) { Text("Shuffle") }
                    OutlinedButton(onClick = {
                        val tracks = loadTracks(artist.id)
                        if (tracks.isNotEmpty()) onAddToQueue(tracks)
                    }) { Text("+ Queue") }
                }
            }
        }
    }
}

@Composable
internal fun DesktopArtistDetailsPanel(
    details: ArtistDetails,
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onSelectAlbum: (AlbumItem) -> Unit,
    onPlayAll: (List<MediaItem>, String) -> Unit,
    onShufflePlay: (List<MediaItem>, String) -> Unit,
    onPlayNext: (List<MediaItem>) -> Unit,
    onAddToQueue: (List<MediaItem>) -> Unit,
    onPlayTrack: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onAddToPlaylist: (String, MediaItem) -> Unit,
) {
    val artist = details.artist
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to Artists") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopArtwork(artist.artworkUri, artist.name, 96.dp)
            Column(Modifier.weight(1f)) {
                Text(artist.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${details.albums.size} album(s) · ${details.tracks.size} song(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlayAll(details.tracks, artist.name) }, enabled = details.tracks.isNotEmpty()) {
                Text("Play All")
            }
            OutlinedButton(onClick = { onShufflePlay(details.tracks, artist.name) }, enabled = details.tracks.isNotEmpty()) {
                Text("Shuffle")
            }
            OutlinedButton(onClick = { onPlayNext(details.tracks) }, enabled = details.tracks.isNotEmpty()) {
                Text("Play Next")
            }
            OutlinedButton(onClick = { onAddToQueue(details.tracks) }, enabled = details.tracks.isNotEmpty()) {
                Text("Add to Queue")
            }
        }
        if (details.albums.isNotEmpty()) {
            Text("Albums", style = MaterialTheme.typography.titleMedium)
            details.albums.forEach { album ->
                Card(
                    Modifier.fillMaxWidth().clickable { onSelectAlbum(album) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopArtwork(album.artworkUri, album.title, 40.dp)
                        Text(album.title, modifier = Modifier.weight(1f))
                        Text("${album.trackCount} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Songs", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(details.tracks, key = { it.id.value }) { track ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopArtwork(track.artworkUri, track.title, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(track.title)
                            Text(
                                listOfNotNull(track.album, track.durationMillis?.let(::formatDuration)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onPlayTrack(track) }) { Text("Play") }
                        OutlinedButton(onClick = { onPlayNext(listOf(track)) }) { Text("Next") }
                        OutlinedButton(onClick = { onAddToQueue(listOf(track)) }) { Text("+ Queue") }
                        OutlinedButton(onClick = { onToggleFavorite(track) }) {
                            Text(if (track.isFavorite) "★" else "☆")
                        }
                        AddToPlaylistButton(playlists, track, onAddToPlaylist)
                    }
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
