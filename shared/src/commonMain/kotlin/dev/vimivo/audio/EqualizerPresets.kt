package dev.vimivo.audio

import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerPreset

/** The ten-band gains (dB) of each equalizer preset, one value per [GraphicEqualizerBands.CENTRES_HZ] band. */
object EqualizerPresets {
    private val FLAT = List(GraphicEqualizerBands.CENTRES_HZ.size) { 0f }

    private val PRESETS: Map<EqualizerPreset, List<Float>> = mapOf(
        EqualizerPreset.FLAT to FLAT,
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

    /** The gains of a built-in [preset]; [EqualizerPreset.CUSTOM] has none of its own and is flat here. */
    fun gains(preset: EqualizerPreset): List<Float> = PRESETS[preset] ?: FLAT

    /**
     * The ten band gains a settings object asks for, before the preamp: the preset's, or for a custom preset the
     * custom bands matched by centre frequency, each limited to ±[GraphicEqualizer.MAX_GAIN_DB], plus the bass and
     * treble controls (bass lifts the two lowest bands by up to 6 dB, treble moves the two highest).
     */
    fun resolved(settings: AudioEnhancementSettings): List<Float> {
        val centres = GraphicEqualizerBands.CENTRES_HZ
        val base = if (settings.preset == EqualizerPreset.CUSTOM) {
            val custom = settings.bands.associate { it.centerFrequencyHz to it.gainDb }
            centres.map { custom[it] ?: 0f }
        } else {
            gains(settings.preset)
        }
        val limit = GraphicEqualizer.MAX_GAIN_DB
        return base.mapIndexed { index, gain ->
            val bass = if (index <= 1) settings.bassStrength.coerceIn(0f, 1f) * BASS_MAX_DB else 0f
            val treble = if (index >= centres.size - 2) settings.trebleGainDb.coerceIn(-limit, limit) else 0f
            (gain.coerceIn(-limit, limit) + bass + treble).coerceIn(-limit, limit)
        }
    }

    private const val BASS_MAX_DB = 6f
}
