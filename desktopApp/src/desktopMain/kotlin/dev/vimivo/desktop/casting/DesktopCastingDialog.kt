package dev.vimivo.desktop.casting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.cast.CastMime
import dev.vimivo.cast.CastSession
import dev.vimivo.cast.FileCastSource
import dev.vimivo.cast.DlnaDiscovery
import dev.vimivo.cast.DlnaRenderer
import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

private sealed interface CastStatus {
    data object Idle : CastStatus
    data class Casting(val renderer: DlnaRenderer) : CastStatus
    data class Failed(val message: String) : CastStatus
}

/**
 * Cast the current item to a DLNA / UPnP TV or speaker on the local network. A local file is served to the chosen
 * device only, from a private address, and stops being served when casting ends or this window closes.
 */
@Composable
internal fun DesktopCastingDialog(
    session: CastSession,
    currentMedia: MediaItem?,
    onPausePlayback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renderers by remember { mutableStateOf<List<DlnaRenderer>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var searchRound by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var activeRenderer by remember { mutableStateOf(session.renderer) }
    val status: CastStatus = activeRenderer?.let { CastStatus.Casting(it) } ?: failure?.let { CastStatus.Failed(it) } ?: CastStatus.Idle

    LaunchedEffect(searchRound) {
        searching = true
        renderers = withContext(Dispatchers.IO) { DlnaDiscovery.discover() }
        searching = false
    }
    fun cast(renderer: DlnaRenderer) {
        val media = currentMedia ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { startCast(session, renderer, media) }
            result.fold(
                onSuccess = { onPausePlayback(); failure = null; activeRenderer = renderer },
                onFailure = { failure = dev.vimivo.desktop.platform.FriendlyError.describe(it, "Casting failed"); activeRenderer = null },
            )
        }
    }

    fun stopCasting() {
        scope.launch {
            withContext(Dispatchers.IO) { session.close() }
            activeRenderer = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Cast to TV or speaker", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searchRound++ }, enabled = !searching) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Search again")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (currentMedia == null) "Open a video or song first, then choose where to play it."
                    else "Casting “${currentMedia.title}”. Devices must be on the same network (DLNA / UPnP).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CastStatusPanel(status, session, scope, onStop = ::stopCasting)
                if (searching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Looking for devices…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (renderers.isEmpty()) {
                    Text(
                        "No devices found. Make sure the TV is on, on the same Wi-Fi, and has DLNA / media rendering enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                renderers.forEach { renderer -> RendererRow(renderer, enabled = currentMedia != null) { cast(renderer) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun CastStatusPanel(status: CastStatus, session: CastSession, scope: kotlinx.coroutines.CoroutineScope, onStop: () -> Unit) {
    when (status) {
        is CastStatus.Casting -> {
            Text("Playing on ${status.renderer.name}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { session.pause() } } }) { Text("Pause") }
                OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { session.resume() } } }) { Text("Play") }
                OutlinedButton(onClick = onStop) { Text("Stop casting") }
            }
        }
        is CastStatus.Failed -> Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        CastStatus.Idle -> Unit
    }
}

@Composable
private fun RendererRow(renderer: DlnaRenderer, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(renderer.name, style = MaterialTheme.typography.titleSmall)
            if (renderer.manufacturer.isNotEmpty()) {
                Text(renderer.manufacturer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Starts [media] on [renderer]: a local file is served to that device only, a web link is handed over as it is. */
private fun startCast(session: CastSession, renderer: DlnaRenderer, media: MediaItem): Result<Unit> {
    val uri = runCatching { URI(media.uri) }.getOrElse { return Result.failure(it) }
    val mime = CastMime.forName(media.fileName ?: media.title)
    return when (uri.scheme) {
        "file" -> session.start(renderer, media.title, mime, FileCastSource(File(uri)), null)
        "http", "https" -> session.start(renderer, media.title, mime, null, media.uri)
        else -> Result.failure(IllegalStateException("This kind of source cannot be cast"))
    }
}
