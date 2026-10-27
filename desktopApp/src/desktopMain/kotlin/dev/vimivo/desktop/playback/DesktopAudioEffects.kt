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

    /** Dynamic range compressor for dialog clarity and evening listening. */
    const val COMPRESSOR_FILTER = "acompressor=threshold=0.125:ratio=4:attack=20:release=250"

    /** Virtual surround / spatializer downmix. */
    const val SPATIALIZER_FILTER = "surround=chl_out=stereo:level_in=1:level_out=1"

    /**
     * Stereo field widener (extrastereo): expands or narrows stereo image.
     */
    fun stereoWidenerStage(strength: Float): String? =
        if (strength <= 0.05f) null else "extrastereo=m=${(1.0 + (strength * 1.5)).toFloat().clean()}"

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
            COMPRESSOR_FILTER.takeIf { settings.compressorEnabled },
            stereoWidenerStage(settings.stereoWidenerStrength),
            SPATIALIZER_FILTER.takeIf { settings.spatializerEnabled },
            "pan=1c|c0=0.5*c0+0.5*c1".takeIf { settings.monoDownmix },
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
