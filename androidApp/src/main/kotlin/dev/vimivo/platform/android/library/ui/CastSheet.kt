package dev.vimivo.platform.android.library.ui

import android.content.ContentResolver
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.vimivo.cast.CastMime
import dev.vimivo.cast.CastSession
import dev.vimivo.cast.CastSource
import dev.vimivo.cast.DlnaDiscovery
import dev.vimivo.cast.DlnaRenderer
import dev.vimivo.core.model.MediaItem
import dev.vimivo.platform.android.ui.components.BottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

/** Streams a content URI (MediaStore or a picked file) to the TV without copying it. */
private class ContentCastSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val name: String,
) : CastSource {
    override fun isAvailable(): Boolean = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { true } }.getOrNull() == true

    override fun length(): Long = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

    override fun open(offset: Long): InputStream {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: throw java.io.IOException("Cannot open the file")
        val stream = FileInputStream(descriptor.fileDescriptor)
        // The asset may start part-way into its file; position relative to that start.
        stream.channel.position(descriptor.startOffset + offset)
        return object : InputStream() {
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                stream.close()
                descriptor.close()
            }
        }
    }
}

/** Cast the playing item to a DLNA / UPnP TV or speaker on the same Wi-Fi. Casting continues after the sheet closes. */
@Composable
internal fun CastSheet(session: CastSession, media: MediaItem?, onPausePlayback: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renderers by remember { mutableStateOf<List<DlnaRenderer>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var round by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(session.renderer) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(round) {
        searching = true
        renderers = withContext(Dispatchers.IO) { DlnaDiscovery.discover() }
        searching = false
    }

    BottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cast, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Cast to TV or speaker", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton({ round++ }, enabled = !searching) { Icon(Icons.Outlined.Refresh, "Search again") }
            }
            Text(
                if (media == null) "Play something first, then choose where to send it."
                else "Sending “${media.title}”. The TV must be on the same Wi-Fi and support DLNA.",
                style = MaterialTheme.typography.bodyMedium,
            )
            active?.let { renderer ->
                Text("Playing on ${renderer.name}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ scope.launch { withContext(Dispatchers.IO) { session.pause() } } }) { Text("Pause") }
                    OutlinedButton({ scope.launch { withContext(Dispatchers.IO) { session.resume() } } }) { Text("Play") }
                    OutlinedButton({
                        scope.launch { withContext(Dispatchers.IO) { session.close() }; active = null }
                    }) { Text("Stop") }
                }
            }
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (searching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking for devices…")
                }
            } else if (renderers.isEmpty()) {
                Text("No devices found. Check the TV is on and on the same Wi-Fi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            renderers.forEach { renderer ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = media != null) {
                        val item = media ?: return@clickable
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { cast(context.contentResolver, session, renderer, item) }
                            result.fold(
                                onSuccess = { onPausePlayback(); failure = null; active = renderer },
                                onFailure = { failure = it.message ?: "Casting failed"; active = null },
                            )
                        }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Tv, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(renderer.name, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private fun cast(resolver: ContentResolver, session: CastSession, renderer: DlnaRenderer, media: MediaItem): Result<Unit> {
    val uri = Uri.parse(media.uri)
    val mime = CastMime.forName(media.fileName ?: media.title)
    return when (uri.scheme) {
        "content", "file" -> session.start(renderer, media.title, mime, ContentCastSource(resolver, uri, media.fileName ?: media.title), null)
        "http", "https" -> session.start(renderer, media.title, mime, null, media.uri)
        else -> Result.failure(IllegalStateException("This kind of source cannot be cast"))
    }
}
