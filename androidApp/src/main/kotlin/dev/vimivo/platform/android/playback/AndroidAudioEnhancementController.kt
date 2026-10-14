@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "StringTemplate",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.platform.android.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.AudioRouteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Owns Android session effects. Effects are always released before a session/engine change. */
internal class AndroidAudioEnhancementController(context: Context) : AutoCloseable {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshRoute()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshRoute()
    }
    private val mutableState = MutableStateFlow(
        AudioEnhancementState(CAPABILITIES, route = currentRoute()),
    )
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var sessionId = 0
    private var requested = AudioEnhancementSettings()

    init {
        audioManager.registerAudioDeviceCallback(routeCallback, Handler(Looper.getMainLooper()))
    }

    val state: StateFlow<AudioEnhancementState> = mutableState.asStateFlow()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId) return
        releaseEffects()
        sessionId = audioSessionId
        if (audioSessionId > 0) apply(requested)
    }

    fun refreshRoute() {
        val route = currentRoute()
        if (route != mutableState.value.route) {
            releaseEffects()
            mutableState.value = mutableState.value.copy(route = route, active = false)
            if (sessionId > 0) apply(requested)
        }
    }

    fun apply(settings: AudioEnhancementSettings): Result<Unit> = runCatching {
        requested = settings.sanitized()
        if (!requested.enabled) {
            releaseEffects()
            mutableState.value = mutableState.value.copy(
                settings = requested,
                route = currentRoute(),
                active = false,
                message = null,
            )
            return@runCatching
        }
        if (sessionId <= 0) {
            mutableState.value = mutableState.value.copy(
                settings = requested,
                active = false,
                message = "Effects will activate when Media3 opens an audio session",
            )
            return@runCatching
        }
        if (equalizer == null) equalizer = Equalizer(0, sessionId)
        val eq = checkNotNull(equalizer)
        val frequencies = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1_000 }
        val range = eq.bandLevelRange
        val gains = requested.resolvedGains(frequencies)
        val peak = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val safetyHeadroom = -peak
        gains.forEachIndexed { index, gain ->
            val safeGain = (gain + requested.preampDb + safetyHeadroom).coerceIn(range[0] / 100f, range[1] / 100f)
            eq.setBandLevel(index.toShort(), (safeGain * 100).roundToInt().toShort())
        }
        eq.enabled = requested.enabled

        if (bassBoost == null) bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        val bassSupported = bassBoost?.strengthSupported == true
        if (bassSupported) {
            bassBoost?.setStrength((requested.bassStrength * 1_000).roundToInt().coerceIn(0, 1_000).toShort())
            bassBoost?.enabled = requested.enabled && requested.bassStrength > 0f
        }
        mutableState.value = AudioEnhancementState(
            capabilities = CAPABILITIES.copy(bassEnhancement = bassSupported),
            settings = requested,
            route = currentRoute(),
            availableBandsHz = frequencies,
            minimumBandGainDb = range[0] / 100f,
            maximumBandGainDb = range[1] / 100f,
            active = requested.enabled,
            message = if (peak > 0f) "Automatic ${peak} dB headroom is applied to reduce clipping" else null,
        )
    }.onFailure { error ->
        releaseEffects()
        mutableState.value = mutableState.value.copy(
            settings = requested,
            active = false,
            message = error.message ?: "This device rejected the audio effect session",
        )
    }

    private fun currentRoute(): AudioRouteType {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when {
            devices.any { it.type in BLUETOOTH_TYPES } -> AudioRouteType.BLUETOOTH
            devices.any { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET } -> AudioRouteType.USB
            devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> AudioRouteType.WIRED_HEADSET
            devices.any { it.type == AudioDeviceInfo.TYPE_HDMI || it.type == AudioDeviceInfo.TYPE_HDMI_ARC } -> AudioRouteType.HDMI
            devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } -> AudioRouteType.SPEAKER
            else -> AudioRouteType.UNKNOWN
        }
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    override fun close() {
        audioManager.unregisterAudioDeviceCallback(routeCallback)
        releaseEffects()
        sessionId = 0
        mutableState.value = mutableState.value.copy(active = false)
    }

    companion object {
        private val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
        val CAPABILITIES = AudioEnhancementCapabilities(
            graphicEqualizer = true,
            bassEnhancement = true,
            trebleAdjustment = true,
            attenuatingPreamp = true,
            pitchPreservation = true,
            limitation = "Media3 Android audio-session effects only. Availability and band layout depend on the device and route.",
        )
    }
}

private fun AudioEnhancementSettings.sanitized() = copy(
    bassStrength = bassStrength.coerceIn(0f, 1f),
    trebleGainDb = trebleGainDb.coerceIn(-6f, 6f),
    preampDb = preampDb.coerceIn(-12f, 0f),
    bands = bands.map { it.copy(gainDb = it.gainDb.coerceIn(-12f, 12f)) },
)

private fun AudioEnhancementSettings.resolvedGains(frequencies: List<Int>): List<Float> {
    val presetBands = when (preset) {
        dev.vimivo.playback.EqualizerPreset.FLAT -> listOf(0f, 0f, 0f, 0f, 0f)
        dev.vimivo.playback.EqualizerPreset.ROCK -> listOf(4f, 2f, -1f, 2f, 4f)
        dev.vimivo.playback.EqualizerPreset.POP -> listOf(-1f, 2f, 4f, 2f, -1f)
        dev.vimivo.playback.EqualizerPreset.CLASSICAL -> listOf(3f, 2f, 0f, 2f, 3f)
        dev.vimivo.playback.EqualizerPreset.JAZZ -> listOf(3f, 1f, 1f, 3f, 4f)
        dev.vimivo.playback.EqualizerPreset.VOCAL -> listOf(-2f, 0f, 4f, 3f, 0f)
        dev.vimivo.playback.EqualizerPreset.BASS_BOOST -> listOf(6f, 4f, 0f, -1f, -2f)
        dev.vimivo.playback.EqualizerPreset.CUSTOM -> emptyList()
    }
    return frequencies.mapIndexed { index, frequency ->
        val custom = bands.minByOrNull { kotlin.math.abs(it.centerFrequencyHz - frequency) }?.gainDb
        val base = if (presetBands.isEmpty()) custom ?: 0f else {
            presetBands[((index.toFloat() / (frequencies.size - 1).coerceAtLeast(1)) * presetBands.lastIndex).roundToInt()]
        }
        base + if (frequency >= 4_000) trebleGainDb else 0f
    }
}
