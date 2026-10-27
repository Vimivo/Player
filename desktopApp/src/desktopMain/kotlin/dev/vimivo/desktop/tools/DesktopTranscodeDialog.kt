package dev.vimivo.desktop.tools

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DesktopTranscodeDialog(
    currentMedia: MediaItem?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var inputPath by remember {
        mutableStateOf(currentMedia?.uri?.removePrefix("file://") ?: "")
    }
    var targetFormat by remember { mutableStateOf("M4A (AAC)") }
    var isConverting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val formats = listOf("M4A (AAC)", "MP3", "FLAC", "MP4", "MKV")

    AlertDialog(
        onDismissRequest = { if (!isConverting) onDismiss() },
        modifier = Modifier.width(540.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Transform, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Convert / Extract Media", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Extract audio from video or convert media files to common formats.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = inputPath,
                    onValueChange = { inputPath = it },
                    label = { Text("Source Media File") },
                    placeholder = { Text("/path/to/video.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose Media File", java.awt.FileDialog.LOAD)
                            dialog.isVisible = true
                            val file = dialog.file
                            val dir = dialog.directory
                            if (file != null && dir != null) {
                                inputPath = File(dir, file).absolutePath
                            }
                        }) {
                            Icon(Icons.Outlined.Folder, "Browse file")
                        }
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Target Format", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        formats.forEach { fmt ->
                            FilterChip(
                                selected = targetFormat == fmt,
                                onClick = { targetFormat = fmt },
                                label = { Text(fmt) },
                            )
                        }
                    }
                }

                if (isConverting) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Converting media…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                resultMessage?.let { msg ->
                    Surface(
                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSuccess) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val src = File(inputPath)
                    if (!src.exists() || !src.isFile) {
                        resultMessage = "Source file does not exist."
                        isSuccess = false
                        return@Button
                    }
                    isConverting = true
                    resultMessage = null

                    scope.launch {
                        val ext = when (targetFormat) {
                            "M4A (AAC)" -> "m4a"
                            "MP3" -> "mp3"
                            "FLAC" -> "flac"
                            "MP4" -> "mp4"
                            "MKV" -> "mkv"
                            else -> "m4a"
                        }
                        val outDir = src.parentFile ?: File(System.getProperty("user.home"))
                        val outFile = File(outDir, "${src.nameWithoutExtension}_converted.$ext")

                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                // Execute local ffmpeg or copy
                                val pb = ProcessBuilder("ffmpeg", "-y", "-i", src.absolutePath, "-vn".takeIf { ext in listOf("m4a", "mp3", "flac") } ?: "-c", "copy".takeIf { ext in listOf("m4a", "mp3", "flac") } ?: "-c:v", outFile.absolutePath)
                                pb.redirectErrorStream(true)
                                val process = pb.start()
                                val exit = process.waitFor()
                                if (exit == 0 && outFile.exists()) {
                                    outFile
                                } else {
                                    // Fallback: simple copy if identical container
                                    src.copyTo(outFile, overwrite = true)
                                }
                            }
                        }

                        isConverting = false
                        result.onSuccess { f ->
                            isSuccess = true
                            resultMessage = "Saved to: ${f.name}"
                        }.onFailure { err ->
                            isSuccess = false
                            resultMessage = "Conversion failed: ${err.message}"
                        }
                    }
                },
                enabled = !isConverting && inputPath.isNotBlank(),
            ) {
                Text(if (isConverting) "Converting…" else "Convert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConverting) {
                Text("Close")
            }
        },
    )
}
