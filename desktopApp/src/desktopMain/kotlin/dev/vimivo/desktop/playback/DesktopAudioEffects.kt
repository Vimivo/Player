package dev.vimivo.desktop.playback

import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerPreset
import kotlin.math.roundToInt

internal object DesktopAudioEffects {
    val frequencies = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

    fun filter(settings: AudioEnhancementSettings): String? {
        if (!settings.enabled) return null
        val gains = presetGains(settings).mapIndexed { index, gain ->
            val bass = if (index <= 1) settings.bassStrength.coerceIn(0f, 1f) * 6f else 0f
            val treble = if (index >= 8) settings.trebleGainDb.coerceIn(-12f, 12f) else 0f
            (gain + bass + treble).coerceIn(-12f, 12f)
        }
        val safetyPreamp = settings.preampDb.coerceIn(-12f, 0f) - gains.maxOrNull().orZero().coerceAtLeast(0f)
        val equalizers = frequencies.zip(gains).joinToString(",") { (frequency, gain) ->
            "equalizer=f=$frequency:t=q:w=1:g=${gain.clean()}"
        }
        return "lavfi=[$equalizers,volume=${safetyPreamp.coerceAtLeast(-24f).clean()}dB]"
    }

    fun effectiveSettings(settings: AudioEnhancementSettings): AudioEnhancementSettings = settings.copy(
        bassStrength = settings.bassStrength.coerceIn(0f, 1f),
        trebleGainDb = settings.trebleGainDb.coerceIn(-12f, 12f),
        preampDb = settings.preampDb.coerceIn(-12f, 0f),
    )

    private fun presetGains(settings: AudioEnhancementSettings): List<Float> {
        if (settings.preset == EqualizerPreset.CUSTOM) {
            val custom = settings.bands.associate { it.centerFrequencyHz to it.gainDb.coerceIn(-12f, 12f) }
            return frequencies.map { custom[it] ?: 0f }
        }
        return PRESETS.getValue(settings.preset)
    }

    private fun Float.clean(): String = (this * 10f).roundToInt().div(10f).toString()
    private fun Float?.orZero(): Float = this ?: 0f

    private val PRESETS = mapOf(
        EqualizerPreset.FLAT to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        EqualizerPreset.ROCK to listOf(4f, 3f, 1f, -1f, -2f, 0f, 2f, 3f, 4f, 4f),
        EqualizerPreset.POP to listOf(-1f, 1f, 3f, 4f, 2f, -1f, -2f, -2f, 1f, 2f),
        EqualizerPreset.CLASSICAL to listOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 3f, 4f),
        EqualizerPreset.JAZZ to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
        EqualizerPreset.VOCAL to listOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f),
        EqualizerPreset.BASS_BOOST to listOf(6f, 5f, 4f, 2f, 0f, -1f, -2f, -2f, -2f, -2f),
        EqualizerPreset.TREBLE_BOOST to listOf(-2f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f),
        EqualizerPreset.ACOUSTIC to listOf(3f, 2f, 1f, 1f, 2f, 2f, 3f, 3f, 2f, 1f),
    )
}
