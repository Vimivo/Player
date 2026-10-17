package dev.vimivo.playback

enum class EqualizerPreset {
    FLAT,
    ROCK,
    POP,
    CLASSICAL,
    JAZZ,
    VOCAL,
    BASS_BOOST,
    TREBLE_BOOST,
    ACOUSTIC,
    ELECTRONIC,
    PODCAST,
    CUSTOM,
}

enum class ReplayGainMode {
    OFF,
    TRACK,
    ALBUM,
}

enum class AudioRouteType { SPEAKER, WIRED_HEADSET, BLUETOOTH, USB, HDMI, UNKNOWN }

data class EqualizerBand(
    val centerFrequencyHz: Int,
    val gainDb: Float,
)

data class CustomEqualizerPreset(
    val name: String,
    val bands: List<EqualizerBand>,
)

data class AudioEnhancementSettings(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bands: List<EqualizerBand> = emptyList(),
    val bassStrength: Float = 0f,
    val trebleGainDb: Float = 0f,
    /** Attenuation only. Positive digital preamp is deliberately unsupported to reduce clipping. */
    val preampDb: Float = 0f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val balance: Float = 0f,
    val customPresetName: String? = null,
    val customPresets: List<CustomEqualizerPreset> = emptyList(),
)

data class AudioEnhancementCapabilities(
    val graphicEqualizer: Boolean = false,
    val bassEnhancement: Boolean = false,
    val trebleAdjustment: Boolean = false,
    val attenuatingPreamp: Boolean = false,
    val replayGain: Boolean = false,
    val gapless: Boolean = false,
    val crossfade: Boolean = false,
    val stereoBalance: Boolean = false,
    val monoOutput: Boolean = false,
    val pitchPreservation: Boolean = false,
    val normalization: Boolean = false,
    val outputSelection: Boolean = false,
    val highResolutionOutput: Boolean = false,
    val bitPerfect: Boolean = false,
    val limitation: String,
)

data class AudioEnhancementState(
    val capabilities: AudioEnhancementCapabilities,
    val settings: AudioEnhancementSettings = AudioEnhancementSettings(),
    val route: AudioRouteType = AudioRouteType.UNKNOWN,
    val availableBandsHz: List<Int> = emptyList(),
    val minimumBandGainDb: Float = -12f,
    val maximumBandGainDb: Float = 12f,
    val active: Boolean = false,
    val message: String? = null,
)
