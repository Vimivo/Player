@file:Suppress(
    "ArgumentListWrapping",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "Indentation",
    "ImportOrdering",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "PropertyWrapping",
    "StringTemplate",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.desktop.streaming

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

@Composable
fun DesktopOpenStreamDialog(
    onDismissRequest: () -> Unit,
    onPlayStream: (MediaItem) -> Unit,
    onAddToQueue: ((MediaItem) -> Unit)? = null,
    historyRepository: DesktopStreamHistoryRepository = remember { DesktopStreamHistoryRepository() },
) {
    var urlInput by remember { mutableStateOf("") }
    var recentStreams by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshHistory() {
        recentStreams = historyRepository.loadRecentStreams()
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    val validationResult = remember(urlInput) {
        if (urlInput.isBlank()) null else StreamValidator.validate(urlInput)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(620.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open Network Stream", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close dialog")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Play direct audio or video streams, HLS (.m3u8), DASH (.mpd), internet radio, or RTSP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Note: Webpage links (e.g. YouTube or social sharing links) are not direct media streams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Stream URL (http://, https://, or rtsp://)") },
                    placeholder = { Text("https://example.com/live/index.m3u8") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Direct network media URL input field" },
                    trailingIcon = {
                        Row {
                            if (urlInput.isNotBlank()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear input")
                                }
                            }
                            IconButton(onClick = {
                                runCatching {
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                                        val clipText = clipboard.getData(DataFlavor.stringFlavor) as? String
                                        if (!clipText.isNullOrBlank()) {
                                            urlInput = clipText.trim()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste from clipboard")
                            }
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                when (val result = validationResult) {
                    null -> {
                        // No URL entered yet
                    }
                    is StreamValidationResult.Valid -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = result.displayTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StreamBadge(result.protocol.displayName)
                                    if (result.isLive) {
                                        StreamBadge("LIVE", isLive = true)
                                    } else {
                                        StreamBadge(result.type.displayName)
                                    }
                                }
                            }
                        }
                    }
                    is StreamValidationResult.Invalid -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = result.reason.name.replace('_', ' '),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                if (recentStreams.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Recent Streams",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                historyRepository.clearRecentStreams()
                                refreshHistory()
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    recentStreams.take(3).forEach { streamUrl ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { urlInput = streamUrl },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = streamUrl,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onAddToQueue != null) {
                    OutlinedButton(
                        onClick = {
                            val valid = validationResult as? StreamValidationResult.Valid ?: return@OutlinedButton
                            val mediaItem = StreamValidator.toMediaItem(valid)
                            historyRepository.recordStream(valid.normalizedUrl)
                            onAddToQueue(mediaItem)
                            onDismissRequest()
                        },
                        enabled = validationResult is StreamValidationResult.Valid,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add to Queue")
                    }
                }

                Button(
                    onClick = {
                        val valid = validationResult as? StreamValidationResult.Valid ?: return@Button
                        val mediaItem = StreamValidator.toMediaItem(valid)
                        historyRepository.recordStream(valid.normalizedUrl)
                        onPlayStream(mediaItem)
                        onDismissRequest()
                    },
                    enabled = validationResult is StreamValidationResult.Valid,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play Now")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun StreamBadge(label: String, isLive: Boolean = false) {
    val bgColor = if (isLive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isLive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}
