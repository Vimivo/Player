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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioOutputDevice
import dev.vimivo.playback.CustomEqualizerPreset
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.ReplayGainMode
import dev.vimivo.ui.components.VimivoAccessibleSlider
import dev.vimivo.ui.components.VimivoGlassSurface
import dev.vimivo.ui.theme.VimivoGlassVariant
import kotlin.math.roundToInt

@Composable
internal fun DesktopEqualizerPanel(
    engine: PlayerEngine,
    currentSettings: AudioEnhancementSettings,
    onSettingsChanged: (AudioEnhancementSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settings by remember(currentSettings) { mutableStateOf(currentSettings) }
    var newPresetName by remember { mutableStateOf("") }
    var availableDevices by remember { mutableStateOf<List<AudioOutputDevice>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableDevices = engine.getAudioOutputDevices()
    }

    val frequencies = remember { DesktopAudioEffects.frequencies }

    fun updateAndApply(newSettings: AudioEnhancementSettings) {
        settings = newSettings
        onSettingsChanged(newSettings)
        engine.applyAudioEnhancements(newSettings)
    }

    VimivoGlassSurface(
        variant = VimivoGlassVariant.FLOATING_PLAYER,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Title + Enable Toggle + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Real Graphic Equalizer & Audio DSP",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (settings.enabled) "10-Band EQ & Hardware Effects Active" else "Audio Effects Bypassed (Flat Output)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.semantics {
                            role = Role.Switch
                            contentDescription = "Master audio equalizer enable switch"
                        },
                    ) {
                        Text(
                            text = if (settings.enabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { isChecked ->
                                updateAndApply(settings.copy(enabled = isChecked))
                            },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            role = Role.Switch
                            contentDescription = "Volume normalizer: evens out loudness between and within tracks"
                        },
                    ) {
                        Text("Normalize", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = settings.volumeNormalization,
                            onCheckedChange = { isChecked ->
                                updateAndApply(settings.copy(volumeNormalization = isChecked))
                            },
                        )
                    }
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }

            HorizontalDivider()

            // Presets Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Equalizer Presets",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (settings.enabled && settings.preset != EqualizerPreset.FLAT) {
                        OutlinedButton(
                            onClick = {
                                updateAndApply(
                                    settings.copy(
                                        preset = EqualizerPreset.FLAT,
                                        bands = frequencies.map { EqualizerBand(it, 0f) },
                                    ),
                                )
                            },
                        ) {
                            Text("Reset to Flat")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EqualizerPreset.entries.forEach { preset ->
                        val isSelected = settings.preset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updatedBands = if (preset == EqualizerPreset.CUSTOM) {
                                    settings.bands.ifEmpty { frequencies.map { EqualizerBand(it, 0f) } }
                                } else {
                                    emptyList()
                                }
                                updateAndApply(
                                    settings.copy(
                                        enabled = true,
                                        preset = preset,
                                        bands = updatedBands,
                                        customPresetName = null,
                                    ),
                                )
                            },
                            label = {
                                Text(preset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                            },
                            enabled = settings.enabled,
                        )
                    }
                }
            }

            // Custom Presets List
            if (settings.customPresets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Saved User Presets", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings.customPresets.forEach { customPreset ->
                            val isSelected = settings.customPresetName == customPreset.name
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    updateAndApply(
                                        settings.copy(
                                            enabled = true,
                                            preset = EqualizerPreset.CUSTOM,
                                            bands = customPreset.bands,
                                            customPresetName = customPreset.name,
                                        ),
                                    )
                                },
                                label = { Text(customPreset.name) },
                                trailingIcon = {
                                    Text(
                                        "✕",
                                        modifier = Modifier.padding(start = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                enabled = settings.enabled,
                            )
                        }
                    }
                }
            }

            // 10-Band Graphic Equalizer Frequencies
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "10-Band Frequency Curve (-12 dB to +12 dB)",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    frequencies.forEach { freq ->
                        val currentBandGain = settings.bands.firstOrNull { it.centerFrequencyHz == freq }?.gainDb ?: 0f
                        val freqLabel = if (freq >= 1_000) "${freq / 1_000} kHz" else "$freq Hz"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(64.dp),
                            )
                            VimivoAccessibleSlider(
                                value = currentBandGain,
                                onValueChange = { newGain ->
                                    val updatedBands = frequencies.map { f ->
                                        if (f == freq) {
                                            EqualizerBand(f, (newGain * 10f).roundToInt() / 10f)
                                        } else {
                                            EqualizerBand(f, settings.bands.firstOrNull { it.centerFrequencyHz == f }?.gainDb ?: 0f)
                                        }
                                    }
                                    updateAndApply(
                                        settings.copy(
                                            enabled = true,
                                            preset = EqualizerPreset.CUSTOM,
                                            bands = updatedBands,
                                        ),
                                    )
                                },
                                valueRange = -12f..12f,
                                label = "Band $freqLabel",
                                formattedValue = "%+.1f dB".format(currentBandGain),
                                modifier = Modifier.weight(1f),
                                enabled = settings.enabled,
                            )
                        }
                    }

                    // Save Custom Preset Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            label = { Text("Custom Preset Name") },
                            placeholder = { Text("e.g. My Acoustic Boost") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            enabled = settings.enabled,
                        )
                        Button(
                            onClick = {
                                val trimmed = newPresetName.trim()
                                if (trimmed.isNotEmpty()) {
                                    val currentBands = settings.bands.ifEmpty {
                                        frequencies.map { EqualizerBand(it, 0f) }
                                    }
                                    val newCustomPreset = CustomEqualizerPreset(trimmed, currentBands)
                                    val updatedList = settings.customPresets.filterNot { it.name == trimmed } + newCustomPreset
                                    updateAndApply(
                                        settings.copy(
                                            customPresets = updatedList,
                                            customPresetName = trimmed,
                                        ),
                                    )
                                    newPresetName = ""
                                }
                            },
                            enabled = settings.enabled && newPresetName.isNotBlank(),
                        ) {
                            Text("Save Preset")
                        }
                    }
                }
            }

            // Audio Enhancement Effects: Bass, Treble, Preamp, Balance, ReplayGain
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Hardware DSP & Effects",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    // Bass Enhancement
                    VimivoAccessibleSlider(
                        value = settings.bassStrength,
                        onValueChange = {
                            updateAndApply(settings.copy(bassStrength = (it * 100f).roundToInt() / 100f))
                        },
                        valueRange = 0f..1f,
                        label = "Bass Enhancement",
                        formattedValue = "${(settings.bassStrength * 100).roundToInt()}%",
                        enabled = settings.enabled,
                    )

                    // Treble Adjustment
                    VimivoAccessibleSlider(
                        value = settings.trebleGainDb,
                        onValueChange = {
                            updateAndApply(settings.copy(trebleGainDb = (it * 10f).roundToInt() / 10f))
                        },
                        valueRange = -12f..12f,
                        label = "Treble Boost / Cut",
                        formattedValue = "%+.1f dB".format(settings.trebleGainDb),
                        enabled = settings.enabled,
                    )

                    // Attenuating Preamp & Auto-Clipping Protection
                    VimivoAccessibleSlider(
                        value = settings.preampDb,
                        onValueChange = {
                            updateAndApply(settings.copy(preampDb = (it * 10f).roundToInt() / 10f))
                        },
                        valueRange = -12f..0f,
                        label = "Preamp Attenuation (Anti-Clipping)",
                        formattedValue = "%.1f dB".format(settings.preampDb),
                        enabled = settings.enabled,
                    )

                    // Stereo Balance
                    VimivoAccessibleSlider(
                        value = settings.balance,
                        onValueChange = {
                            val snapped = if (kotlin.math.abs(it) < 0.05f) 0f else (it * 100f).roundToInt() / 100f
                            updateAndApply(settings.copy(balance = snapped))
                        },
                        valueRange = -1f..1f,
                        label = "Stereo Balance",
                        formattedValue = when {
                            settings.balance < -0.05f -> "L %d%%".format((-settings.balance * 100).roundToInt())
                            settings.balance > 0.05f -> "R %d%%".format((settings.balance * 100).roundToInt())
                            else -> "Center"
                        },
                        enabled = settings.enabled,
                    )

                    // ReplayGain Loudness Normalization
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ReplayGain Loudness Normalization",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReplayGainMode.entries.forEach { mode ->
                                val isSelected = settings.replayGainMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        updateAndApply(settings.copy(replayGainMode = mode))
                                        engine.setReplayGainMode(mode)
                                    },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    enabled = settings.enabled,
                                )
                            }
                        }
                    }

                    // Compressor, Spatializer, Stereo Widener & Mono Downmix
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Dynamic Range Compressor", style = MaterialTheme.typography.bodyMedium)
                            Text("Even out loud & quiet passages (night mode)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.compressorEnabled,
                            onCheckedChange = { updateAndApply(settings.copy(compressorEnabled = it)) },
                            enabled = settings.enabled,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Virtual Surround / Spatializer", style = MaterialTheme.typography.bodyMedium)
                            Text("3D spatialized sound stage for headphones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.spatializerEnabled,
                            onCheckedChange = { updateAndApply(settings.copy(spatializerEnabled = it)) },
                            enabled = settings.enabled,
                        )
                    }

                    VimivoAccessibleSlider(
                        value = settings.stereoWidenerStrength,
                        onValueChange = {
                            updateAndApply(settings.copy(stereoWidenerStrength = (it * 100f).roundToInt() / 100f))
                        },
                        valueRange = 0f..1f,
                        label = "Stereo Field Widener",
                        formattedValue = "${(settings.stereoWidenerStrength * 100).roundToInt()}%",
                        enabled = settings.enabled,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Mono Downmix", style = MaterialTheme.typography.bodyMedium)
                            Text("Combine all channels into single mono stream", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.monoDownmix,
                            onCheckedChange = { updateAndApply(settings.copy(monoDownmix = it)) },
                            enabled = settings.enabled,
                        )
                    }
                }
            }

            // Audio Output Device Selection
            if (availableDevices.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Audio Output Device",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            availableDevices.forEach { device ->
                                FilterChip(
                                    selected = device.isSelected,
                                    onClick = {
                                        engine.selectAudioOutputDevice(device.id).onSuccess {
                                            availableDevices = engine.getAudioOutputDevices()
                                        }
                                    },
                                    label = { Text(device.name) },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Reset All Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = {
                        val flatSettings = AudioEnhancementSettings(
                            enabled = false,
                            preset = EqualizerPreset.FLAT,
                            bands = frequencies.map { EqualizerBand(it, 0f) },
                            bassStrength = 0f,
                            trebleGainDb = 0f,
                            preampDb = 0f,
                            balance = 0f,
                            replayGainMode = ReplayGainMode.OFF,
                            customPresets = settings.customPresets,
                        )
                        updateAndApply(flatSettings)
                        engine.setReplayGainMode(ReplayGainMode.OFF)
                        engine.setAudioBalance(0f)
                    },
                ) {
                    Text("Reset All to Default")
                }
            }
        }
    }
}
