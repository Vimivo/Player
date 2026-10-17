@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "MaximumLineLength", "MaxLineLength",
    "NoConsecutiveBlankLines", "ParameterListWrapping", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.ExternalSubtitle
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.VideoAspectRatio
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
internal fun DesktopVideoControlsPanel(
    engine: PlayerEngine,
    state: PlaybackState,
    modifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var currentAdjustments by remember { mutableStateOf(state.videoAdjustments) }
    var abRepeatA by remember { mutableStateOf<Long?>(null) }
    var abRepeatB by remember { mutableStateOf<Long?>(null) }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var audioDelayMs by remember { mutableStateOf(0L) }

    VimivoGlassSurface(
        variant = VimivoGlassVariant.CONTROL,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Video & Subtitle Controls", style = MaterialTheme.typography.titleSmall)

            // Row 1: Aspect Ratio, Screenshot, Rotation
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aspect:", style = MaterialTheme.typography.labelMedium)
                VideoAspectRatio.entries.forEach { ratio ->
                    val isSelected = currentAdjustments.aspectRatio == ratio
                    if (isSelected) {
                        Button(
                            onClick = {
                                currentAdjustments = currentAdjustments.copy(aspectRatio = ratio)
                                engine.applyVideoAdjustments(currentAdjustments)
                            },
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                contentDescription = "Aspect ratio ${ratio.label}, selected"
                            },
                        ) { Text(ratio.label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                currentAdjustments = currentAdjustments.copy(aspectRatio = ratio)
                                engine.applyVideoAdjustments(currentAdjustments)
                            },
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                contentDescription = "Aspect ratio ${ratio.label}"
                            },
                        ) { Text(ratio.label) }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val nextRotation = (currentAdjustments.rotationDegrees + 90) % 360
                        currentAdjustments = currentAdjustments.copy(rotationDegrees = nextRotation)
                        engine.applyVideoAdjustments(currentAdjustments)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Rotate video 90 degrees clockwise. Currently ${currentAdjustments.rotationDegrees} degrees"
                    },
                ) {
                    Text("Rotate 90° (${currentAdjustments.rotationDegrees}°)")
                }
                Button(
                    onClick = {
                        val userHome = System.getProperty("user.home")
                        val desktop = File(userHome, "Desktop").takeIf { it.isDirectory } ?: File(userHome)
                        val target = File(desktop, "vimivo_frame_${System.currentTimeMillis()}.png")
                        engine.takeScreenshot(target.absolutePath).onSuccess {
                            message = "Screenshot saved: ${target.name}"
                        }.onFailure {
                            message = "Screenshot failed: ${it.message}"
                        }
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Take frame screenshot and save to Desktop"
                    },
                ) {
                    Text("Screenshot")
                }
            }

            // Row 2: Subtitle, Audio Delay, Chapters
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        chooseSubtitleFile()?.let { file ->
                            val sub = ExternalSubtitle(
                                uri = file.toURI().toString(),
                                label = file.name,
                                language = null,
                                mimeType = "application/x-subrip",
                            )
                            engine.addExternalSubtitle(sub).onSuccess {
                                message = "Loaded subtitle: ${file.name}"
                            }
                        }
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Load external subtitle file (.srt, .vtt, .ass)"
                    },
                ) {
                    Text("Add Subtitle")
                }
                Text("Sub Delay: ${subtitleDelayMs}ms", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    onClick = {
                        subtitleDelayMs -= 250
                        engine.setSubtitleDelay(subtitleDelayMs)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Decrease subtitle delay by 250 milliseconds"
                    },
                ) { Text("-250ms") }
                OutlinedButton(
                    onClick = {
                        subtitleDelayMs += 250
                        engine.setSubtitleDelay(subtitleDelayMs)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Increase subtitle delay by 250 milliseconds"
                    },
                ) { Text("+250ms") }
                OutlinedButton(
                    onClick = {
                        subtitleDelayMs = 0
                        engine.setSubtitleDelay(0)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Reset subtitle delay to 0 milliseconds"
                    },
                ) { Text("Reset") }

                Text("Audio Sync: ${audioDelayMs}ms", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    onClick = {
                        audioDelayMs -= 100
                        engine.setAudioDelay(audioDelayMs)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Decrease audio delay by 100 milliseconds"
                    },
                ) { Text("-100ms") }
                OutlinedButton(
                    onClick = {
                        audioDelayMs += 100
                        engine.setAudioDelay(audioDelayMs)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Increase audio delay by 100 milliseconds"
                    },
                ) { Text("+100ms") }

                OutlinedButton(
                    onClick = { engine.previousChapter() },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Go to previous chapter"
                    },
                ) { Text("◀ Chapter") }
                OutlinedButton(
                    onClick = { engine.nextChapter() },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Go to next chapter"
                    },
                ) { Text("Chapter ▶") }
            }

            // Row 3: A-B Repeat
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("A-B Repeat:", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = {
                        abRepeatA = state.positionMillis
                        message = "Loop point A set at ${formatMs(state.positionMillis)}"
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Set loop start point A at current position"
                    },
                ) {
                    Text(if (abRepeatA != null) "A: ${formatMs(abRepeatA!!)}" else "Set A")
                }
                OutlinedButton(
                    onClick = {
                        val a = abRepeatA ?: 0L
                        val b = state.positionMillis
                        abRepeatB = b
                        engine.setABRepeat(ABRepeatRange(a, b))
                        message = "Looping A (${formatMs(a)}) to B (${formatMs(b)})"
                    },
                    enabled = abRepeatA != null,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Set loop end point B at current position and begin repeat loop"
                    },
                ) {
                    Text(if (abRepeatB != null) "B: ${formatMs(abRepeatB!!)}" else "Set B")
                }
                if (abRepeatA != null || abRepeatB != null) {
                    OutlinedButton(
                        onClick = {
                            abRepeatA = null
                            abRepeatB = null
                            engine.setABRepeat(ABRepeatRange(null, null))
                            message = "A-B Repeat cleared"
                        },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Clear A-B repeat loop"
                        },
                    ) {
                        Text("Clear Loop")
                    }
                }
            }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatMs(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun chooseSubtitleFile(): File? {
    val dialog = FileDialog(null as Frame?, "Open Subtitle File", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".srt", true) || name.endsWith(".vtt", true) ||
            name.endsWith(".ass", true) || name.endsWith(".ssa", true)
    }
    dialog.isVisible = true
    return dialog.files?.firstOrNull() ?: dialog.file?.let { File(dialog.directory, it) }
}
