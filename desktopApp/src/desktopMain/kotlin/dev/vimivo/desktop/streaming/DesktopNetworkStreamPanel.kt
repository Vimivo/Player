@file:Suppress(
    "ArgumentListWrapping",
    "FunctionNaming",
    "Indentation",
    "ImportOrdering",
    "LongMethod",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "NoConsecutiveBlankLines",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.desktop.streaming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.streaming.StreamValidationResult
import dev.vimivo.core.streaming.StreamValidator

@Composable
fun DesktopNetworkStreamPanel(
    onOpenStreamDialog: () -> Unit,
    onPlayStream: (MediaItem) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    historyRepository: DesktopStreamHistoryRepository,
) {
    var recentStreams by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshHistory() {
        recentStreams = historyRepository.loadRecentStreams()
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    Column(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Network stream playback panel" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Direct Network Audio & Video Playback",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Stream user-provided media addresses directly without accounts, catalogs, or telemetry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onOpenStreamDialog,
                        modifier = Modifier.semantics { contentDescription = "Open network stream dialog" },
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Stream…")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProtocolChip("HTTP/HTTPS Audio")
                    ProtocolChip("HTTP/HTTPS Video")
                    ProtocolChip("HLS (.m3u8)")
                    ProtocolChip("DASH (.mpd)")
                    ProtocolChip("Internet Radio")
                    ProtocolChip("RTSP")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text(
                        text = "Privacy & Media Boundaries",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Vimivo does not bundle or query online media catalogs, YouTube, Spotify, or cloud services. Stream URLs are validated locally and sent strictly to the target media host.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (recentStreams.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Recent Streams",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                historyRepository.clearRecentStreams()
                                refreshHistory()
                            },
                        ) {
                            Text("Clear")
                        }
                    }

                    recentStreams.forEach { streamUrl ->
                        val validation = remember(streamUrl) { StreamValidator.validate(streamUrl) }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    val title = (validation as? StreamValidationResult.Valid)?.displayTitle ?: streamUrl
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = streamUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (validation is StreamValidationResult.Valid) {
                                        val mediaItem = StreamValidator.toMediaItem(validation)
                                        OutlinedButton(
                                            onClick = { onAddToQueue(mediaItem) },
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.PlaylistAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Queue")
                                        }
                                        Button(
                                            onClick = { onPlayStream(mediaItem) },
                                        ) {
                                            Icon(
                                                Icons.Outlined.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Play")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
