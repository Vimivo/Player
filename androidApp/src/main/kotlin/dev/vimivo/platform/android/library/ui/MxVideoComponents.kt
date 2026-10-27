package dev.vimivo.platform.android.library.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import dev.vimivo.platform.android.library.AndroidWifiTransferServer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.MediaItem
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.ui.components.BottomSheet
import dev.vimivo.platform.android.ui.components.MediaArtwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Calculates whether a video should display the signature MX "NEW" badge.
 * A video is considered "NEW" if it was added within the last 7 days and has not been completed.
 */
fun isVideoNew(item: MediaItem): Boolean {
    val addedSeconds = item.dateAddedEpochSeconds ?: return false
    val nowSeconds = System.currentTimeMillis() / 1000L
    val isRecent = (nowSeconds - addedSeconds) < (7L * 24L * 3600L)
    val notFinished = item.lastPlaybackPositionMillis < (item.durationMillis ?: Long.MAX_VALUE) * 0.95
    return isRecent && notFinished
}

fun formatResolutionBadge(width: Int?, height: Int?): String? {
    if (width == null || height == null) return null
    val minDim = minOf(width, height)
    return when {
        minDim >= 2000 -> "4K"
        minDim >= 1400 -> "1440p"
        minDim >= 1000 -> "1080p"
        minDim >= 700 -> "720p"
        minDim >= 400 -> "480p"
        else -> "${minDim}p"
    }
}

fun formatMediaDuration(milliseconds: Long?): String {
    if (milliseconds == null || milliseconds <= 0) return "--:--"
    val seconds = milliseconds / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatByteSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        else -> "${bytes / 1024} KB"
    }
}

/**
 * MX Player-style Video list row with duration chip, resolution tag, NEW badge, and watch progress.
 */
@Composable
fun MxVideoRow(
    media: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isLastPlayed: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val progress = remember(media.lastPlaybackPositionMillis, media.durationMillis) {
        val duration = media.durationMillis ?: 0L
        if (duration > 0 && media.lastPlaybackPositionMillis > 0) {
            (media.lastPlaybackPositionMillis.toFloat() / duration).coerceIn(0f, 1f)
        } else 0f
    }
    val resBadge = remember(media.width, media.height) { formatResolutionBadge(media.width, media.height) }
    val isNew = remember(media) { isVideoNew(media) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        // Thumbnail with overlaid duration, resolution, NEW tag, and progress bar
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
        ) {
            MediaArtwork(
                media = media,
                thumbnails = thumbnails,
                modifier = Modifier.matchParentSize(),
            )

            // NEW badge at top-left
            if (isNew) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        "NEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            // Resolution badge at top-right
            if (resBadge != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                ) {
                    Text(
                        resBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }

            // Duration badge at bottom-right
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
            ) {
                Text(
                    formatMediaDuration(media.durationMillis),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }

            // Watched progress bar at the bottom edge
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    drawStopIndicator = {},
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title and meta info
        Column(Modifier.weight(1f)) {
            Text(
                media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isLastPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            val detailParts = buildList {
                media.sizeBytes?.let { add(formatByteSize(it)) }
                resBadge?.let { add(it) }
                media.folderName?.let { add(it) }
            }
            Text(
                detailParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!isSelectionMode) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, "More actions for ${media.title}")
            }
        }
    }
}

/**
 * MX Player-style Video Grid Card with duration, resolution, NEW tag, and progress.
 */
@Composable
fun MxVideoCard(
    media: MediaItem,
    thumbnails: AndroidThumbnailRepository,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = remember(media.lastPlaybackPositionMillis, media.durationMillis) {
        val duration = media.durationMillis ?: 0L
        if (duration > 0 && media.lastPlaybackPositionMillis > 0) {
            (media.lastPlaybackPositionMillis.toFloat() / duration).coerceIn(0f, 1f)
        } else 0f
    }
    val resBadge = remember(media.width, media.height) { formatResolutionBadge(media.width, media.height) }
    val isNew = remember(media) { isVideoNew(media) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                MediaArtwork(media = media, thumbnails = thumbnails, modifier = Modifier.matchParentSize())

                if (isNew) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            "NEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                if (resBadge != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Text(
                            resBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                ) {
                    Text(
                        formatMediaDuration(media.durationMillis),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }

                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        drawStopIndicator = {},
                    )
                }

                if (isSelectionMode) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(10.dp)) {
                Text(
                    media.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatByteSize(media.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * MX Player-style folder list item with "N NEW" count badge.
 */
@Composable
fun MxFolderRow(
    name: String,
    videoCount: Int,
    newCount: Int,
    onClick: () -> Unit,
    onHide: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        headlineContent = {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "$videoCount videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (newCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            "$newCount NEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                IconButton(onClick = onHide) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide folder")
                }
            }
        },
    )
}

/**
 * Floating Action Button for MX Player "Play Last" signature feature.
 */
@Composable
fun MxPlayLastFab(
    lastPlayedItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Resume Video",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    lastPlayedItem.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

/**
 * Bottom action bar shown in multi-select mode.
 */
@Composable
fun MxMultiSelectBottomBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onProperties: () -> Unit,
    onExtractAudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Cancel selection")
            }
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, "Share")
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to Playlist")
            }
            if (onExtractAudio != null) {
                IconButton(onClick = onExtractAudio) {
                    Icon(Icons.Outlined.MusicNote, "Extract audio")
                }
            }
            IconButton(onClick = onRename, enabled = selectedCount == 1) {
                Icon(Icons.Rounded.Edit, "Rename")
            }
            IconButton(onClick = onProperties, enabled = selectedCount == 1) {
                Icon(Icons.Rounded.Info, "Properties")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Dialog displaying full media properties (MX Player signature file properties).
 */
@Composable
fun MxMediaPropertiesDialog(
    media: MediaItem,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File Properties", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyLine("File Name", media.fileName ?: media.title)
                PropertyLine("Location", media.folderPath ?: media.uri)
                PropertyLine("File Size", formatByteSize(media.sizeBytes))
                if (media.width != null && media.height != null) {
                    PropertyLine("Resolution", "${media.width} × ${media.height} (${formatResolutionBadge(media.width, media.height)})")
                }
                PropertyLine("Duration", formatMediaDuration(media.durationMillis))
                if (media.frameRate != null) {
                    PropertyLine("Frame Rate", "%.2f fps".format(media.frameRate))
                }
                media.mimeType?.let { mime ->
                    PropertyLine("Format", mime)
                }
                media.dateAddedEpochSeconds?.let { added ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    PropertyLine("Date Added", sdf.format(Date(added * 1000L)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Dialog to unlock or set a PIN for the Private Vault folder.
 */
@Composable
fun MxPrivateFolderDialog(
    hasPinSet: Boolean,
    onUnlock: (String) -> Boolean,
    onSetPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pinText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPinSet) "Enter Vault PIN" else "Set Private Vault PIN") },
        text = {
            Column {
                Text(
                    if (hasPinSet) "Enter your 4-digit PIN to access private files."
                    else "Create a 4-digit PIN to protect your private media folder.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinText = it },
                    label = { Text("4-digit PIN") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.length != 4) {
                        errorText = "PIN must be 4 digits"
                        return@Button
                    }
                    if (hasPinSet) {
                        val ok = onUnlock(pinText)
                        if (!ok) errorText = "Incorrect PIN"
                        else onDismiss()
                    } else {
                        onSetPin(pinText)
                        onDismiss()
                    }
                },
            ) {
                Text(if (hasPinSet) "Unlock" else "Set PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Sort options sheet for videos (Name, Date, Size, Duration with Ascending / Descending).
 */
@Composable
fun MxSortSheet(
    currentSort: LibrarySort,
    isAscending: Boolean,
    onSelectSort: (LibrarySort, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Sort By", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            val sorts = listOf(
                LibrarySort.TITLE to "Title / Name",
                LibrarySort.DATE_ADDED to "Date Added",
                LibrarySort.FILE_SIZE to "File Size",
                LibrarySort.DURATION to "Duration",
            )

            sorts.forEach { (sort, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSort(sort, isAscending); onDismiss() }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = currentSort == sort, onClick = { onSelectSort(sort, isAscending); onDismiss() })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Order", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSort(currentSort, true); onDismiss() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isAscending, onClick = { onSelectSort(currentSort, true); onDismiss() })
                Spacer(Modifier.width(12.dp))
                Text("Ascending (A–Z, oldest/smallest first)", style = MaterialTheme.typography.bodyLarge)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSort(currentSort, false); onDismiss() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = !isAscending, onClick = { onSelectSort(currentSort, false); onDismiss() })
                Spacer(Modifier.width(12.dp))
                Text("Descending (Z–A, newest/largest first)", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Dialog displaying Wi-Fi file transfer status and instructions.
 */
@Composable
fun MxWifiTransferDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val server = remember { AndroidWifiTransferServer(context) }
    var serverUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        server.start()
        serverUrl = server.serverUrl
        onDispose {
            server.stop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Wi-Fi File Sharing", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Transfer media files between your computer or other devices and Vimivo without a cable or internet connection.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Open this address in your web browser:", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            serverUrl,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ensure both devices are on the same Wi-Fi network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
