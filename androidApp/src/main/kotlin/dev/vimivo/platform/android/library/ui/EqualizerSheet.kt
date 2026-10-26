@file:Suppress(
    "ArgumentListWrapping",
    "CyclomaticComplexMethod",
    "ImportOrdering",
    "Indentation",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.audio.EqualizerPresets
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset

private val EQ_PRESETS = listOf(
    EqualizerPreset.FLAT, EqualizerPreset.POP, EqualizerPreset.ROCK, EqualizerPreset.JAZZ, EqualizerPreset.CLASSICAL,
    EqualizerPreset.VOCAL, EqualizerPreset.BASS_BOOST, EqualizerPreset.TREBLE_BOOST, EqualizerPreset.ACOUSTIC,
    EqualizerPreset.ELECTRONIC, EqualizerPreset.PODCAST, EqualizerPreset.CUSTOM,
)

private fun presetLabel(preset: EqualizerPreset) = preset.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun bandLabel(hz: Int) = if (hz >= 1_000) "${hz / 1_000}k".replace(".0", "") else "$hz"

/**
 * The equalizer: on/off, presets, ten vertical band sliders, preamp and the bass/treble/ReplayGain extras the engine
 * reports. Dragging a band turns the current curve into a custom one, so a preset is never edited in place.
 */
@Composable
internal fun EqualizerContent(
    settings: AudioEnhancementSettings,
    audio: AudioEnhancementState,
    onChange: (AudioEnhancementSettings) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", style = MaterialTheme.typography.headlineSmall)
                Text(
                    audio.route.name.lowercase().replace('_', ' ') + if (audio.active) " · active" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                settings.enabled,
                { onChange(settings.copy(enabled = it)) },
                Modifier.semantics { contentDescription = "Equalizer on or off" },
                enabled = audio.capabilities.graphicEqualizer,
            )
        }
        if (!audio.capabilities.graphicEqualizer) {
            Text(
                audio.capabilities.limitation,
                Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(EQ_PRESETS) { preset ->
                FilterChip(
                    selected = settings.preset == preset,
                    onClick = {
                        val gains = EqualizerPresets.gains(preset)
                        onChange(
                            settings.copy(
                                preset = preset,
                                enabled = true,
                                bands = if (preset == EqualizerPreset.CUSTOM) currentBands(settings, audio) else bandsFrom(audio, gains),
                            ),
                        )
                    },
                    label = { Text(presetLabel(preset)) },
                )
            }
        }
        val gains = baseGains(settings, audio)
        val bands = audio.availableBandsHz
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(200.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bands.forEachIndexed { index, hz ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    VerticalGainSlider(
                        gainDb = gains.getOrElse(index) { 0f }.coerceIn(audio.minimumBandGainDb, audio.maximumBandGainDb),
                        range = audio.minimumBandGainDb..audio.maximumBandGainDb,
                        label = "${bandLabel(hz)} hertz",
                        enabled = settings.enabled,
                        onChange = { gain ->
                            val edited = currentBands(settings, audio).map { if (it.centerFrequencyHz == hz) it.copy(gainDb = gain) else it }
                            onChange(settings.copy(preset = EqualizerPreset.CUSTOM, bands = edited))
                        },
                        modifier = Modifier.weight(1f).width(36.dp),
                    )
                    Text(bandLabel(hz), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (audio.capabilities.attenuatingPreamp) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row {
                    Text("Preamp", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("%.1f dB".format(settings.preampDb), style = MaterialTheme.typography.labelLarge)
                }
                Slider(settings.preampDb, { onChange(settings.copy(preampDb = it)) }, valueRange = -12f..0f)
            }
        }
        if (audio.capabilities.bassEnhancement) ExtraSlider("Bass", settings.bassStrength, 0f..1f) { onChange(settings.copy(bassStrength = it)) }
        if (audio.capabilities.trebleAdjustment) ExtraSlider("Treble", settings.trebleGainDb, -6f..6f) { onChange(settings.copy(trebleGainDb = it)) }
        TextButton(
            { onChange(settings.copy(preset = EqualizerPreset.FLAT, bands = emptyList(), bassStrength = 0f, trebleGainDb = 0f, preampDb = 0f)) },
            Modifier.padding(horizontal = 12.dp),
        ) { Text("Reset") }
        audio.message?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ExtraSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("%.1f".format(value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(value, onChange, valueRange = range)
    }
}

private fun bandsFrom(audio: AudioEnhancementState, gains: List<Float>) =
    audio.availableBandsHz.mapIndexed { index, hz -> EqualizerBand(hz, gains.getOrElse(index) { 0f }) }

/**
 * The per-band gains of the chosen preset, or of the custom curve, without the bass and treble extras (those are their
 * own sliders and must not be copied into the bands).
 */
private fun baseGains(settings: AudioEnhancementSettings, audio: AudioEnhancementState): List<Float> =
    audio.availableBandsHz.mapIndexed { index, hz ->
        if (settings.preset == EqualizerPreset.CUSTOM) {
            settings.bands.firstOrNull { it.centerFrequencyHz == hz }?.gainDb ?: 0f
        } else {
            EqualizerPresets.gains(settings.preset).getOrElse(index) { 0f }
        }
    }

/** The ten bands as drawn right now, as an editable starting point for a custom curve. */
private fun currentBands(settings: AudioEnhancementSettings, audio: AudioEnhancementState) =
    bandsFrom(audio, baseGains(settings, audio))
