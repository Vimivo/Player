@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "NoConsecutiveBlankLines", "ParameterListWrapping",
    "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop.playback

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SubtitleStyle
import dev.vimivo.playback.VideoAspectRatio
import dev.vimivo.ui.components.VimivoAccessibleSlider
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun DesktopVideoControlsPanel(
    engine: PlayerEngine,
    state: PlaybackState,
    commands: DesktopPlayerCommandExecutor,
    modifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var currentAdjustments by remember(state.videoAdjustments) { mutableStateOf(state.videoAdjustments) }
    // A-B points and sync delays live in the engine state so shortcuts and this panel stay in sync.
    val abRepeatA = state.abRepeat.pointA
    val abRepeatB = state.abRepeat.pointB
    val subtitleDelayMs = state.subtitleDelayMillis
    val audioDelayMs = state.audioDelayMillis
    var subtitleFontSize by remember { mutableStateOf(24) }
    var subtitleColorHex by remember { mutableStateOf("#FFFFFF") }

    VimivoGlassSurface(
        variant = VimivoGlassVariant.CONTROL,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Advanced Video & Subtitle Controls", style = MaterialTheme.typography.titleSmall)

            // Section 1: Aspect Ratio, Rotation, Screenshots, Playback Speed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aspect:", style = MaterialTheme.typography.labelMedium)
                VideoAspectRatio.entries.forEach { ratio ->
                    val isSelected = currentAdjustments.aspectRatio == ratio
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            currentAdjustments = currentAdjustments.copy(aspectRatio = ratio)
                            engine.applyVideoAdjustments(currentAdjustments)
                        },
                        label = { Text(ratio.label) },
                    )
                }

                OutlinedButton(
                    onClick = {
                        val nextRotation = (currentAdjustments.rotationDegrees + 90) % 360
                        currentAdjustments = currentAdjustments.copy(rotationDegrees = nextRotation)
                        engine.applyVideoAdjustments(currentAdjustments)
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Rotate video 90 degrees. Currently ${currentAdjustments.rotationDegrees} degrees"
                    },
                ) {
                    Text("Rotate (${currentAdjustments.rotationDegrees}°)")
                }

                Button(
                    // Same folder, file naming and HUD feedback as the Shift+S shortcut.
                    onClick = { commands.execute(DesktopPlayerCommand.SNAPSHOT) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Capture full-resolution video frame screenshot"
                    },
                ) {
                    Text("Screenshot")
                }

                OutlinedButton(
                    onClick = { engine.frameStep(forward = false) },
                    enabled = engine.capabilities.frameStepping,
                    modifier = Modifier.semantics { contentDescription = "Step back one frame" },
                ) { Text("◀ Frame") }
                OutlinedButton(
                    onClick = { engine.frameStep(forward = true) },
                    enabled = engine.capabilities.frameStepping,
                    modifier = Modifier.semantics { contentDescription = "Step forward one frame" },
                ) { Text("Frame ▶") }
                FilterChip(
                    selected = state.deinterlace,
                    onClick = { engine.setDeinterlace(!state.deinterlace) },
                    enabled = engine.capabilities.deinterlacing,
                    label = { Text("Deinterlace") },
                )
            }

            // Speed Selector Row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Speed:", style = MaterialTheme.typography.labelMedium)
                PlaybackSpeeds.PRESETS.forEach { speed ->
                    val isSelected = kotlin.math.abs(state.speed - speed) < 0.05f
                    FilterChip(
                        selected = isSelected,
                        onClick = { engine.setPlaybackSpeed(speed) },
                        label = { Text("${speed}x") },
                    )
                }
            }

            HorizontalDivider()

            // Section 2: Audio Tracks & Synchronization
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Audio Track:", style = MaterialTheme.typography.labelMedium)
                if (state.audioTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.audioTracks.forEach { track ->
                            FilterChip(
                                selected = track.isSelected,
                                onClick = { engine.selectAudioTrack(track.id) },
                                label = {
                                    val desc = listOfNotNull(track.label, track.language, track.codec).joinToString(" • ")
                                    Text(desc.ifEmpty { "Track ${track.id}" })
                                },
                            )
                        }
                    }
                } else {
                    Text("Default Audio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Text("Sync: ${audioDelayMs}ms", style = MaterialTheme.typography.labelSmall)
                val audioStep = PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS
                OutlinedButton(
                    onClick = { commands.execute(DesktopPlayerCommand.AUDIO_DELAY_DOWN) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Decrease audio delay by $audioStep milliseconds"
                    },
                ) { Text("−$audioStep ms") }
                OutlinedButton(
                    onClick = { commands.execute(DesktopPlayerCommand.AUDIO_DELAY_UP) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Increase audio delay by $audioStep milliseconds"
                    },
                ) { Text("+$audioStep ms") }
                if (audioDelayMs != 0L) {
                    OutlinedButton(
                        onClick = { engine.setAudioDelay(0) },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Reset audio delay to 0 milliseconds"
                        },
                    ) { Text("Reset") }
                }
            }

            // Section 3: Subtitle Tracks, Delay, Styling & External File Loading
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Subtitles:", style = MaterialTheme.typography.labelMedium)
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
                                message = "Loaded external subtitle: ${file.name}"
                            }
                        }
                    },
                ) {
                    Text("Add File (.srt/.vtt/.ass)")
                }

                FilterChip(
                    selected = state.subtitleTracks.none { it.isSelected },
                    onClick = { engine.selectSubtitleTrack(null) },
                    label = { Text("None (Off)") },
                )

                if (state.subtitleTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.subtitleTracks.forEach { track ->
                            FilterChip(
                                selected = track.isSelected,
                                onClick = { engine.selectSubtitleTrack(track.id) },
                                label = {
                                    val desc = listOfNotNull(track.label, track.language).joinToString(" • ")
                                    Text(desc.ifEmpty { "Subtitle ${track.id}" })
                                },
                            )
                        }
                    }
                }

                Text("Delay: ${subtitleDelayMs}ms", style = MaterialTheme.typography.labelSmall)
                val subtitleStep = PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS
                OutlinedButton(
                    onClick = { commands.execute(DesktopPlayerCommand.SUBTITLE_DELAY_DOWN) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Decrease subtitle delay by $subtitleStep milliseconds"
                    },
                ) { Text("−$subtitleStep ms") }
                OutlinedButton(
                    onClick = { commands.execute(DesktopPlayerCommand.SUBTITLE_DELAY_UP) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Increase subtitle delay by $subtitleStep milliseconds"
                    },
                ) { Text("+$subtitleStep ms") }
                if (subtitleDelayMs != 0L) {
                    OutlinedButton(
                        onClick = { engine.setSubtitleDelay(0) },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Reset subtitle delay to 0 milliseconds"
                        },
                    ) { Text("Reset") }
                }
            }

            // Subtitle Appearance: Size & Color
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sub Size:", style = MaterialTheme.typography.labelMedium)
                listOf(18, 24, 30, 36).forEach { size ->
                    FilterChip(
                        selected = subtitleFontSize == size,
                        onClick = {
                            subtitleFontSize = size
                            engine.applySubtitleStyle(
                                SubtitleStyle(
                                    fontSizeSp = size,
                                    textColorHex = subtitleColorHex,
                                    bottomMarginPercent = 5,
                                ),
                            )
                        },
                        label = { Text("${size}sp") },
                    )
                }

                Text("Color:", style = MaterialTheme.typography.labelMedium)
                listOf("#FFFFFF" to "White", "#FFFF00" to "Yellow", "#00FFFF" to "Cyan").forEach { (hex, name) ->
                    FilterChip(
                        selected = subtitleColorHex == hex,
                        onClick = {
                            subtitleColorHex = hex
                            engine.applySubtitleStyle(
                                SubtitleStyle(
                                    fontSizeSp = subtitleFontSize,
                                    textColorHex = hex,
                                    bottomMarginPercent = 5,
                                ),
                            )
                        },
                        label = { Text(name) },
                    )
                }
            }

            HorizontalDivider()

            // Section 4: Real-Time Picture Adjustments (Brightness, Contrast, Saturation, Gamma)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    VimivoAccessibleSlider(
                        value = currentAdjustments.brightness.toFloat(),
                        onValueChange = {
                            currentAdjustments = currentAdjustments.copy(brightness = it.roundToInt())
                            engine.applyVideoAdjustments(currentAdjustments)
                        },
                        valueRange = -50f..50f,
                        label = "Brightness",
                        formattedValue = "%+d".format(currentAdjustments.brightness),
                    )
                }
                Column(Modifier.weight(1f)) {
                    VimivoAccessibleSlider(
                        value = currentAdjustments.contrast.toFloat(),
                        onValueChange = {
                            currentAdjustments = currentAdjustments.copy(contrast = it.roundToInt())
                            engine.applyVideoAdjustments(currentAdjustments)
                        },
                        valueRange = -50f..50f,
                        label = "Contrast",
                        formattedValue = "%+d".format(currentAdjustments.contrast),
                    )
                }
                Column(Modifier.weight(1f)) {
                    VimivoAccessibleSlider(
                        value = currentAdjustments.saturation.toFloat(),
                        onValueChange = {
                            currentAdjustments = currentAdjustments.copy(saturation = it.roundToInt())
                            engine.applyVideoAdjustments(currentAdjustments)
                        },
                        valueRange = -50f..50f,
                        label = "Saturation",
                        formattedValue = "%+d".format(currentAdjustments.saturation),
                    )
                }
                if (currentAdjustments.brightness != 0 || currentAdjustments.contrast != 0 || currentAdjustments.saturation != 0) {
                    OutlinedButton(
                        onClick = {
                            currentAdjustments = currentAdjustments.copy(brightness = 0, contrast = 0, saturation = 0, gamma = 0)
                            engine.applyVideoAdjustments(currentAdjustments)
                        },
                    ) {
                        Text("Reset Colors")
                    }
                }
            }

            // Section 5: Chapters & A-B Repeat
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chapters:", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = { engine.previousChapter() },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Skip to previous chapter"
                    },
                ) { Text("◀ Prev") }
                OutlinedButton(
                    onClick = { engine.nextChapter() },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Skip to next chapter"
                    },
                ) { Text("Next ▶") }

                Text("A-B Loop:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 12.dp))
                OutlinedButton(
                    onClick = {
                        engine.setABRepeat(ABRepeatRange(pointA = state.positionMillis))
                        message = "Point A set at ${formatMs(state.positionMillis)}"
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = if (abRepeatA != null) "Point A set at ${formatMs(abRepeatA!!)}" else "Set Point A"
                    },
                ) {
                    Text(if (abRepeatA != null) "A: ${formatMs(abRepeatA!!)}" else "Set A")
                }
                OutlinedButton(
                    onClick = {
                        val a = abRepeatA ?: 0L
                        val b = state.positionMillis
                        if (b > a) {
                            engine.setABRepeat(ABRepeatRange(a, b, enabled = true))
                            message = "Looping ${formatMs(a)} to ${formatMs(b)}"
                        } else {
                            message = "Point B must be after point A"
                        }
                    },
                    enabled = abRepeatA != null,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = if (abRepeatB != null) "Point B set at ${formatMs(abRepeatB!!)}" else "Set Point B"
                    },
                ) {
                    Text(if (abRepeatB != null) "B: ${formatMs(abRepeatB!!)}" else "Set B")
                }
                if (abRepeatA != null || abRepeatB != null) {
                    OutlinedButton(
                        onClick = {
                            engine.setABRepeat(ABRepeatRange(null, null, enabled = false))
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
