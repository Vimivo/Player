package dev.vimivo.desktop.playback

import dev.vimivo.audio.EqualizerPresets
import dev.vimivo.audio.GraphicEqualizerBands
import dev.vimivo.playback.AudioEnhancementSettings
import kotlin.math.roundToInt

internal object DesktopAudioEffects {
    val frequencies = GraphicEqualizerBands.CENTRES_HZ

    const val LIMITER_CEILING = 0.97

    /** dynaudnorm smooths loudness jumps between and within tracks without hard limiting. */
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
        val gains = EqualizerPresets.resolved(settings)
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

    private fun Float.clean(): String = (this * 10f).roundToInt().div(10f).toString()
    private fun Float?.orZero(): Float = this ?: 0f
}
