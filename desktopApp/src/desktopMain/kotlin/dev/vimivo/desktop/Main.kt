@file:Suppress("ArgumentListWrapping", "FunctionNaming", "MagicNumber", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val engine = remember { MpvPlayerEngine() }
    Window(
        onCloseRequest = {
            engine.close()
            exitApplication()
        },
        title = "Vimivo",
    ) {
        MaterialTheme { DesktopPlayer(engine) }
    }
}

@Composable
private fun DesktopPlayer(engine: MpvPlayerEngine) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var networkUrl by remember { mutableStateOf("") }
    DisposableEffect(engine) { onDispose(engine::close) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Vimivo", style = MaterialTheme.typography.headlineLarge)
            Text("macOS desktop player · libmpv", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    chooseMediaFile()?.let { file ->
                        scope.launch { engine.open(file.toMediaItem()).onSuccess { engine.play() } }
                    }
                }) { Text("Open media") }
                OutlinedButton(onClick = { if (state.status == PlaybackStatus.Playing) engine.pause() else engine.play() }) {
                    Text(if (state.status == PlaybackStatus.Playing) "Pause" else "Play")
                }
                OutlinedButton(onClick = { engine.stop() }) { Text("Stop") }
            }
            OutlinedTextField(
                value = networkUrl,
                onValueChange = { networkUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Authorized HTTP, HTTPS, or RTSP URL") },
                singleLine = true,
            )
            Button(
                enabled = networkUrl.isNotBlank(),
                onClick = {
                    val url = networkUrl.trim()
                    scope.launch { engine.open(url.toNetworkItem()).onSuccess { engine.play() } }
                },
            ) { Text("Open stream") }
            Spacer(Modifier.height(12.dp))
            Text(state.media?.title ?: "Nothing playing", style = MaterialTheme.typography.titleLarge)
            Text(state.status.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = state.positionMillis.toFloat().coerceAtMost((state.durationMillis ?: 1).toFloat()),
                onValueChange = { engine.seekTo(it.toLong()) },
                valueRange = 0f..(state.durationMillis ?: 1).coerceAtLeast(1).toFloat(),
                enabled = state.isSeekable,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(state.positionMillis))
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.durationMillis ?: 0))
            }
            (state.status as? PlaybackStatus.Error)?.let { error ->
                Text(error.error.message, color = MaterialTheme.colorScheme.error)
                Text(
                    "Install libmpv (for example with Homebrew) or set -Dvimivo.mpv.library=/absolute/path/to/libmpv.dylib.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun chooseMediaFile(): File? {
    val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

private fun File.toMediaItem() = MediaItem(
    id = MediaId(toURI().toString()),
    uri = toURI().toString(),
    title = nameWithoutExtension,
    kind = if (extension.lowercase() in VIDEO_EXTENSIONS) MediaKind.VIDEO else MediaKind.AUDIO,
    fileName = name,
)

private fun String.toNetworkItem() = MediaItem(MediaId(this), this, this, MediaKind.STREAM)

private fun PlaybackStatus.label() = when (this) {
    PlaybackStatus.Idle -> "Idle"
    PlaybackStatus.Preparing -> "Preparing"
    PlaybackStatus.Buffering -> "Buffering"
    PlaybackStatus.Ready -> "Ready"
    PlaybackStatus.Playing -> "Playing"
    PlaybackStatus.Paused -> "Paused"
    PlaybackStatus.Completed -> "Completed"
    is PlaybackStatus.Error -> "Playback error"
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "m4v", "ts", "m2ts")
