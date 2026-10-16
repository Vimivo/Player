@file:Suppress(
    "ArgumentListWrapping",
    "ImportOrdering",
    "LongParameterList",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vimivo.platform.android.ui.theme.LocalLargeTouchTargets
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.ui.theme.LocalReducedMotion
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
import dev.vimivo.ui.theme.VimivoPlayer

@Composable
fun MediaArtwork(
    media: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Artwork for ${media.title}",
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, media.uri, media.artworkUri) {
        value = thumbnails.load(media.uri, media.artworkUri)
    }
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription, Modifier.matchParentSize())
        } else {
            Icon(
                if (media.kind == MediaKind.VIDEO) Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote,
                contentDescription,
                Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MediaCard(media: MediaItem, thumbnails: AndroidThumbnailRepository, onClick: () -> Unit) {
    Column(
        Modifier.width(164.dp).clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription = "Play ${media.title}"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaArtwork(media, thumbnails, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(media.artist ?: media.album ?: media.folderName.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SongRow(
    media: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { MediaArtwork(media, thumbnails, Modifier.size(54.dp)) },
        headlineContent = { Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(media.artist, media.album).joinToString(" · "), maxLines = 1) },
        trailingContent = if (onFavorite != null || onMore != null) ({
            Row {
                onFavorite?.let { action ->
                    IconButton(action) {
                        Icon(
                            if (media.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            if (media.isFavorite) "Remove ${media.title} from favorites" else "Add ${media.title} to favorites",
                        )
                    }
                }
                onMore?.let { action -> IconButton(action) { Icon(Icons.Outlined.MoreVert, "More actions for ${media.title}") } }
            }
        }) else null,
    )
}

@Composable
fun AlbumCard(media: MediaItem, thumbnails: AndroidThumbnailRepository, onClick: () -> Unit) =
    MediaCard(media, thumbnails, onClick)

@Composable
fun VideoCard(
    media: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        MediaArtwork(media, thumbnails, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                onFavorite?.let { action ->
                    IconButton(action) {
                        Icon(if (media.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite ${media.title}")
                    }
                }
            }
            Text(media.folderName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    Column {
        Slider(
            value = position.coerceAtMost(duration).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = duration > 0,
            modifier = Modifier.semantics {
                contentDescription = "Playback seekbar"
                stateDescription = "${formatTime(position)} of ${formatTime(duration)}"
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
            Text("-${formatTime((duration - position).coerceAtLeast(0))}", style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, Modifier.size(VimivoPlayer.transportButton)) { Icon(Icons.Outlined.SkipPrevious, "Previous track") }
            IconButton(onClick = onToggle, Modifier.size(VimivoPlayer.primaryTransportButton)) {
                if (reducedMotion) {
                    Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (isPlaying) "Pause" else "Play", Modifier.size(42.dp))
                } else AnimatedContent(isPlaying, label = "play pause") { playing ->
                    Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(42.dp))
                }
            }
            IconButton(onClick = onNext, Modifier.size(VimivoPlayer.transportButton)) { Icon(Icons.Outlined.SkipNext, "Next track") }
        }
    }
}

@Composable
fun MiniPlayer(
    media: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val animatedProgress by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(),
        label = "playback progress",
    )
    VimivoGlassSurface(
        variant = VimivoGlassVariant.FLOATING_PLAYER,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaArtwork(media, thumbnails, Modifier.size(VimivoPlayer.compactArtwork))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(media.artist ?: "Unknown artist", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (isPlaying) "Pause" else "Play") }
                IconButton(onClick = onNext) { Icon(Icons.Outlined.SkipNext, "Next track") }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(animatedProgress).height(2.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun EmptyState(icon: ImageVector = Icons.Outlined.Album, title: String, detail: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) OutlinedButton(onAction) { Text(action) }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) = EmptyState(Icons.Outlined.ErrorOutline, "Something went wrong", message, if (onRetry != null) "Try again" else null, onRetry)

@Composable
fun LoadingIndicator(label: String = "Loading media") {
    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit, onClose: (() -> Unit)? = null) {
    OutlinedTextField(
        value,
        onValueChange,
        Modifier.fillMaxWidth(),
        placeholder = { Text("Search songs, albums, artists, videos") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = onClose?.let { { IconButton(it) { Icon(Icons.Outlined.Close, "Close search") } } },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        content = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                content()
            }
        },
    )
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, detail: String? = null, checked: Boolean? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .heightIn(min = if (LocalLargeTouchTargets.current) 64.dp else 48.dp)
            .clickable(role = if (checked != null) Role.Switch else Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = if (checked != null) ({ Switch(checked, onCheckedChange = null) }) else ({ Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }),
    )
    HorizontalDivider(Modifier.padding(start = 56.dp))
}
