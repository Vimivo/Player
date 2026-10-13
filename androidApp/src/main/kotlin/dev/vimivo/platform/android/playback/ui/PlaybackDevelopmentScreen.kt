@file:Suppress("ArgumentListWrapping", "UnstableApiUsage", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.playback.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import dev.vimivo.platform.android.playback.PlaybackController
import dev.vimivo.ui.theme.VimivoTheme

@Composable
fun PlaybackDevelopmentScreen(connection: PlaybackController) = VimivoTheme {
    val controller by connection.controller.collectAsState()
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { controller?.open(it.toMediaItem(context)) }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.takeIf { it > 0 } ?: 0
                playbackState = player.playbackState
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Playback session console", style = MaterialTheme.typography.headlineSmall)
            Text("Controls the single background MediaSession service.")
            controller?.let { player ->
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }) { Text("Open media") }
            OutlinedTextField(url, { url = it }, label = { Text("Authorized HTTP/HTTPS URL") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { controller?.open(Uri.parse(url).toMediaItem(context)) }, enabled = url.startsWith("http")) {
                Text("Open stream")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller?.play() }) { Text("Play") }
                OutlinedButton(onClick = { controller?.pause() }) { Text("Pause") }
                OutlinedButton(onClick = { controller?.seekToNext() }) { Text("Next") }
                OutlinedButton(onClick = { controller?.seekToPrevious() }) { Text("Previous") }
            }
            Slider(
                value = position.coerceAtMost(duration).toFloat(),
                onValueChange = { controller?.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = duration > 0,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller?.repeatMode = Player.REPEAT_MODE_ONE }) { Text("Repeat one") }
                OutlinedButton(onClick = { controller?.repeatMode = Player.REPEAT_MODE_ALL }) { Text("Repeat all") }
                OutlinedButton(onClick = { controller?.shuffleModeEnabled = controller?.shuffleModeEnabled != true }) {
                    Text("Shuffle")
                }
            }
            Text("Session: ${if (controller == null) "connecting" else playbackState}")
        }
    }
}

private fun Player.open(item: MediaItem) {
    setMediaItem(item)
    prepare()
    play()
}

private fun Uri.toMediaItem(context: Context): MediaItem = MediaItem.Builder()
    .setMediaId(toString())
    .setUri(this)
    .setMimeType(context.contentResolver.getType(this))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(lastPathSegment ?: host ?: "Media").build())
    .build()
