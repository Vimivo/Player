package dev.vimivo.desktop.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.platform.FriendlyError
import dev.vimivo.desktop.playback.MpvTranscoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException

/** The dialog's behaviour in one place; the composables below only draw it. */
private class TranscodeState(private val scope: CoroutineScope, initial: File?) {
    var source by mutableStateOf(initial)
    var target by mutableStateOf(MpvTranscoder.Target.M4A)
    var running by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    var resultFile by mutableStateOf<File?>(null)
    private var cancelRequested = false

    fun cancel() {
        cancelRequested = true
    }

    fun start() {
        val input = source ?: return
        running = true
        cancelRequested = false
        progress = 0f
        message = null
        failed = false
        resultFile = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val folder = input.parentFile ?: File(System.getProperty("user.home"))
                MpvTranscoder().convert(input, target, folder, { progress = it }, { cancelRequested })
            }
            running = false
            outcome.fold(
                onSuccess = { resultFile = it; message = "Saved as ${it.name}" },
                onFailure = {
                    failed = it !is CancellationException
                    message = if (failed) FriendlyError.describe(it, "Conversion failed") else "Cancelled"
                },
            )
        }
    }
}

/**
 * Convert the current file, or one you pick, to audio (AAC, FLAC, WAV, MP3) or rewrap it as MKV/MP4 without losing
 * quality. Everything runs inside the app through libmpv; nothing is uploaded and no other program is needed.
 */
@Composable
internal fun DesktopTranscodeDialog(currentMedia: MediaItem?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember { TranscodeState(scope, currentMedia?.localFile()) }

    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Transform, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Convert media", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = { TranscodeBody(state) },
        confirmButton = {
            if (state.running) OutlinedButton(onClick = state::cancel) { Text("Cancel") }
            else Button(onClick = state::start, enabled = state.source != null) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.running) { Text("Close") } },
    )
}

@Composable
private fun TranscodeBody(state: TranscodeState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(440.dp)) {
        Text(state.source?.name ?: "No file chosen", style = MaterialTheme.typography.titleSmall, maxLines = 1)
        OutlinedButton(enabled = !state.running, onClick = { chooseFile()?.let { state.source = it } }) { Text("Choose another file…") }
        Text("Convert to", style = MaterialTheme.typography.labelMedium)
        TargetChips(state)
        Text(
            state.target.label + ": " + state.target.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "The new file is saved next to the original; the original is never changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.running) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        state.message?.let {
            Text(it, color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        state.resultFile?.let { file ->
            TextButton(onClick = { runCatching { Desktop.getDesktop().open(file.parentFile) } }) { Text("Show in folder") }
        }
    }
}

@Composable
private fun TargetChips(state: TranscodeState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MpvTranscoder.Target.entries.chunked(CHIPS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    FilterChip(
                        selected = state.target == t,
                        enabled = !state.running,
                        onClick = { state.target = t },
                        label = { Text(t.extension.uppercase() + if (t.name.endsWith("COPY")) " copy" else "") },
                    )
                }
            }
        }
    }
}

private const val CHIPS_PER_ROW = 3

private fun MediaItem.localFile(): File? = runCatching {
    URI(uri).takeIf { it.scheme == "file" }?.let(::File)?.takeIf(File::isFile)
}.getOrNull()

private fun chooseFile(): File? {
    val dialog = FileDialog(null as Frame?, "Choose a file to convert", FileDialog.LOAD)
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}
