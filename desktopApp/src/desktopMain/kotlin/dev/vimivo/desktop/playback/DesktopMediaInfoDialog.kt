@file:Suppress("ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.desktop.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import dev.vimivo.playback.MediaInfoFormatter
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.TrackInfo
import dev.vimivo.ui.theme.VimivoTheme

/**
 * Media information / codec details. Uses a real dialog window because the libmpv video canvas is
 * a heavyweight AWT component that would paint over an in-window Compose popup.
 */
@Composable
internal fun DesktopMediaInfoDialog(
    state: PlaybackState,
    containerInfo: DesktopContainerInfo?,
    onDismiss: () -> Unit,
) {
    val media = state.media ?: return
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Media Information — ${media.title}",
        state = rememberDialogState(size = DpSize(620.dp, 560.dp)),
        onKeyEvent = { event ->
            (event.type == KeyEventType.KeyDown && event.key == Key.Escape).also { if (it) onDismiss() }
        },
    ) {
        VimivoTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GeneralSection(state, containerInfo)

                            TrackSection("Video", state.videoTracks)
                            TrackSection("Audio", state.audioTracks)
                            TrackSection("Subtitles", state.subtitleTracks)

                            val metadata = containerInfo?.metadata.orEmpty()
                            if (metadata.isNotEmpty()) {
                                HorizontalDivider()
                                Section("Metadata")
                                metadata.forEach { (key, value) -> InfoRow(key, value) }
                            }
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun GeneralSection(state: PlaybackState, containerInfo: DesktopContainerInfo?) {
    val media = state.media ?: return
    Section("General")
    InfoRow("Title", media.title)
    media.artist?.let { InfoRow("Artist", it) }
    media.album?.let { InfoRow("Album", it) }
    InfoRow("Location", media.uri)
    containerInfo?.format?.let { InfoRow("Container", it) }
    state.durationMillis?.let { InfoRow("Duration", MediaInfoFormatter.formatDuration(it)) }
    containerInfo?.fileSizeBytes?.let { InfoRow("Size", MediaInfoFormatter.formatFileSize(it)) }
    containerInfo?.videoParameters?.let { InfoRow("Video output", it) }
    containerInfo?.audioParameters?.let { InfoRow("Audio output", it) }
    containerInfo?.rendering?.let { InfoRow("Rendering", it) }
}

@Composable
private fun TrackSection(title: String, tracks: List<TrackInfo>) {
    if (tracks.isEmpty()) return
    HorizontalDivider()
    Section(title)
    tracks.forEach { track ->
        val name = if (track.isSelected) "#${track.id} (active)" else "#${track.id}"
        InfoRow(name, listOf(track.label, MediaInfoFormatter.describe(track)).filter(String::isNotBlank).joinToString(" — "))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
