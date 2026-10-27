@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("ArgumentListWrapping", "ImportOrdering", "MagicNumber", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.platform.android.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.playback.PlaybackUiState
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.ui.components.MediaArtwork

/** The play queue as a "Next up" list: a thumbnail, title and length per item, the current one highlighted. */
@Composable
internal fun NextUpList(
    player: PlaybackUiState,
    viewModel: PlaybackViewModel,
    thumbnails: AndroidThumbnailRepository,
    onPicked: () -> Unit,
) {
    Column {
        Text("Next up", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        val state = rememberLazyListState()
        LaunchedEffect(player.currentIndex) { state.scrollToItem(player.currentIndex.coerceIn(0, (player.queue.size - 1).coerceAtLeast(0))) }
        LazyColumn(Modifier.fillMaxWidth().height(420.dp), state = state) {
            itemsIndexed(player.queue, key = { index, item -> "${item.mediaId}#$index" }) { index, item ->
                val current = index == player.currentIndex
                val media = item.toDomain()
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(onClickLabel = "Play ${media.title}") { viewModel.playQueueIndex(index); onPicked() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaArtwork(media, thumbnails, Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(8.dp)), contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            media.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        media.durationMillis?.takeIf { it > 0 }?.let {
                            Text(formatClock(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (current) Text("Playing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
