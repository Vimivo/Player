package dev.vimivo.desktop.playback

import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerPreset
import kotlin.math.roundToInt

internal object DesktopAudioEffects {
    val frequencies = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

    /** dynaudnorm smooths loudness jumps between and within tracks without hard limiting. */
    const val LIMITER_CEILING = 0.97

    const val NORMALIZER_FILTER = "dynaudnorm=f=250:g=31:p=0.9"

    /**
     * mpv applies its own `volume` after the filter chain, so a limiter cannot sit in front of it. Boost above 100 %
     * is therefore done here, followed by a limiter that stops the extra gain from clipping.
     */
    fun boostStage(gain: Float): String? =
        if (gain <= 1f) null else "volume=${gain.toDouble()}:precision=float,alimiter=limit=$LIMITER_CEILING:level=0"

    fun filter(settings: AudioEnhancementSettings, boostGain: Float = 1f): String? {
        val stages = listOfNotNull(
            equalizerChain(settings).takeIf { settings.enabled },
            NORMALIZER_FILTER.takeIf { settings.volumeNormalization },
            boostStage(boostGain),
        )
        return if (stages.isEmpty()) null else "lavfi=[${stages.joinToString(",")}]"
    }

    private fun equalizerChain(settings: AudioEnhancementSettings): String {
        val gains = presetGains(settings).mapIndexed { index, gain ->
            val bass = if (index <= 1) settings.bassStrength.coerceIn(0f, 1f) * 6f else 0f
            val treble = if (index >= 8) settings.trebleGainDb.coerceIn(-12f, 12f) else 0f
            (gain + bass + treble).coerceIn(-12f, 12f)
        }
        val safetyPreamp = settings.preampDb.coerceIn(-12f, 0f) - gains.maxOrNull().orZero().coerceAtLeast(0f)
        val equalizers = frequencies.zip(gains).joinToString(",") { (frequency, gain) ->
            "equalizer=f=$frequency:t=q:w=1:g=${gain.clean()}"
        }
        return "$equalizers,volume=${safetyPreamp.coerceAtLeast(-24f).clean()}dB"
    }

    fun effectiveSettings(settings: AudioEnhancementSettings): AudioEnhancementSettings = settings.copy(
        bassStrength = settings.bassStrength.coerceIn(0f, 1f),
        trebleGainDb = settings.trebleGainDb.coerceIn(-12f, 12f),
        preampDb = settings.preampDb.coerceIn(-12f, 0f),
        balance = settings.balance.coerceIn(-1f, 1f),
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
        EqualizerPreset.ELECTRONIC to listOf(5f, 4f, 2f, 0f, -2f, 2f, 1f, 2f, 4f, 5f),
        EqualizerPreset.PODCAST to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, -1f, -2f),
    )
}
