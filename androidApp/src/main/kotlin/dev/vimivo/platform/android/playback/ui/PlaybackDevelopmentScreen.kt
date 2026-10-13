package dev.vimivo.platform.android.playback.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.platform.android.playback.LibVlcPlayerEngine
import dev.vimivo.platform.android.playback.Media3PlayerEngine
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.ui.theme.VimivoTheme
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun PlaybackDevelopmentScreen(manager: PlaybackEngineManager) = VimivoTheme {
    val state by manager.state.collectAsState()
    val engine by manager.activeEngine.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            scope.launch {
                manager.open(uri.toMediaItem(context)).onFailure { localError = it.message }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Playback development console", style = MaterialTheme.typography.headlineSmall)
            Text("This screen exercises the engine foundation; final player UI will replace it.")

            key(engine) {
                when (val active = engine) {
                    is Media3PlayerEngine -> AndroidView(
                        factory = { PlayerView(it).apply { player = active.player } },
                        update = { it.player = active.player },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                    is LibVlcPlayerEngine -> LibVlcOutput(active)
                    else -> Spacer(Modifier.fillMaxWidth().height(120.dp))
                }
            }

            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }) {
                Text("Open local media")
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Authorized HTTP/HTTPS URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val uri = Uri.parse(url.trim())
                    if (uri.scheme !in setOf("http", "https")) {
                        localError = "Enter an HTTP or HTTPS URL"
                    } else {
                        scope.launch {
                            manager.open(uri.toMediaItem(context, MediaKind.STREAM)).onFailure {
                                localError = it.message
                            }
                        }
                    }
                },
                enabled = url.isNotBlank(),
            ) {
                Text("Open network media")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { manager.play().onFailure { localError = it.message } }) { Text("Play") }
                OutlinedButton(onClick = { manager.pause().onFailure { localError = it.message } }) { Text("Pause") }
                OutlinedButton(onClick = { manager.stop().onFailure { localError = it.message } }) { Text("Stop") }
            }

            val duration = state.durationMillis ?: 0L
            Slider(
                value = state.positionMillis.coerceAtMost(duration).toFloat(),
                onValueChange = { manager.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = state.isSeekable && duration > 0,
            )
            val positionLabel = formatTime(state.positionMillis)
            val durationLabel = formatTime(duration)
            val bufferedLabel = formatTime(state.bufferedPositionMillis)
            Text("$positionLabel / $durationLabel · buffered $bufferedLabel")
            Text("Volume ${(state.volume * 100).toInt()}%")
            Slider(
                value = state.volume,
                onValueChange = { manager.setVolume(it) },
                valueRange = 0f..1f,
                enabled = engine != null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                    OutlinedButton(onClick = { manager.setPlaybackSpeed(speed) }) {
                        Text("$speed×")
                    }
                }
            }
            Text("Engine: ${state.engine ?: "none"} · State: ${state.status.label()}")
            val trackSummary = "Audio ${state.audioTracks.size} · Video ${state.videoTracks.size} · " +
                "Subtitles ${state.subtitleTracks.size}"
            Text(trackSummary)
            val playbackError = (state.status as? PlaybackStatus.Error)?.error?.message
            (localError ?: playbackError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun LibVlcOutput(engine: LibVlcPlayerEngine) {
    var layout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    AndroidView(
        factory = { VLCVideoLayout(it).also { view -> layout = view } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
    DisposableEffect(engine, layout) {
        layout?.let(engine::attachVideo)
        onDispose { engine.detachVideo() }
    }
}

private fun Uri.toMediaItem(context: Context, forcedKind: MediaKind? = null): MediaItem {
    val mimeType = context.contentResolver.getType(this)
    val kind = forcedKind ?: if (mimeType?.startsWith("video/") == true) MediaKind.VIDEO else MediaKind.AUDIO
    return MediaItem(
        id = MediaId(toString()),
        uri = toString(),
        title = lastPathSegment ?: host ?: "Development media",
        kind = kind,
    )
}

private fun PlaybackStatus.label(): String = when (this) {
    PlaybackStatus.Idle -> "Idle"
    PlaybackStatus.Preparing -> "Preparing"
    PlaybackStatus.Buffering -> "Buffering"
    PlaybackStatus.Ready -> "Ready"
    PlaybackStatus.Playing -> "Playing"
    PlaybackStatus.Paused -> "Paused"
    PlaybackStatus.Completed -> "Completed"
    is PlaybackStatus.Error -> "Error"
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
