@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "Wrapping")

package dev.vimivo.platform.android.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dev.vimivo.audio.AudioDspChain
import dev.vimivo.audio.EqualizerPresets
import dev.vimivo.audio.GraphicEqualizer
import dev.vimivo.audio.GraphicEqualizerBands
import dev.vimivo.audio.ReplayGain
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.AudioRouteType
import dev.vimivo.playback.ReplayGainMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ReplayGain values read from the playing file's tags (dB and linear peaks; null where the file has none). */
internal data class ReplayGainTags(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
)

/**
 * Owns the audio effects of the Media3 engine: Vimivo's shared 10-band equalizer, the gain for a volume boost above
 * 100 % and for ReplayGain, and the limiter, all running in [processor] inside ExoPlayer's audio pipeline. That makes
 * the equalizer the same on every device (the platform `Equalizer` effect has a device-defined band layout) and lets
 * a boost be limited instead of clipping.
 */
internal class AndroidAudioEnhancementController(context: Context) : AutoCloseable {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshRoute()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshRoute()
    }
    private val mutableState = MutableStateFlow(AudioEnhancementState(CAPABILITIES, route = currentRoute()))
    private var requested = AudioEnhancementSettings()
    private var boost = 1f
    private var tags = ReplayGainTags()

    /** The pipeline stage the player's audio sink runs. */
    val processor = DspAudioProcessor()

    init {
        audioManager.registerAudioDeviceCallback(routeCallback, Handler(Looper.getMainLooper()))
    }

    val state: StateFlow<AudioEnhancementState> = mutableState.asStateFlow()

    fun refreshRoute() {
        val route = currentRoute()
        if (route != mutableState.value.route) mutableState.value = mutableState.value.copy(route = route)
    }

    fun apply(settings: AudioEnhancementSettings): Result<Unit> = runCatching {
        requested = settings.sanitized()
        update()
    }

    /** The gain for a volume above 100 % (1 leaves the level alone). */
    fun setBoost(multiplier: Float) {
        boost = multiplier.coerceIn(1f, MAX_BOOST)
        update()
    }

    /** The ReplayGain tags of the item that now plays. */
    fun setReplayGainTags(newTags: ReplayGainTags) {
        tags = newTags
        update()
    }

    private fun update() {
        val bands = if (requested.enabled) EqualizerPresets.resolved(requested) else List(BANDS) { 0f }
        val preamp = if (requested.enabled) AudioDspChain.linear(requested.preampDb) else 1f
        val replayGain = ReplayGain.linearGain(
            requested.replayGainMode,
            tags.trackGainDb,
            tags.albumGainDb,
            tags.trackPeak,
            tags.albumPeak,
        )
        processor.configure(boost * preamp * replayGain, bands)
        val active = requested.enabled || boost > 1f || requested.replayGainMode != ReplayGainMode.OFF
        mutableState.value = AudioEnhancementState(
            capabilities = CAPABILITIES,
            settings = requested,
            route = currentRoute(),
            availableBandsHz = GraphicEqualizerBands.CENTRES_HZ,
            minimumBandGainDb = -GraphicEqualizer.MAX_GAIN_DB,
            maximumBandGainDb = GraphicEqualizer.MAX_GAIN_DB,
            active = active,
            message = when {
                boost > 1f -> "Volume boost ${(boost * PERCENT).toInt()} % with limiter"
                requested.replayGainMode != ReplayGainMode.OFF && tags == ReplayGainTags() -> "This file has no ReplayGain tags"
                else -> null
            },
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

    override fun close() {
        audioManager.unregisterAudioDeviceCallback(routeCallback)
        mutableState.value = mutableState.value.copy(active = false)
    }

    companion object {
        /** Volume boost goes up to 200 %. */
        const val MAX_BOOST = 2f
        private const val BANDS = 10
        private const val PERCENT = 100
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
            replayGain = true,
            pitchPreservation = true,
            gapless = true,
            limitation = "Vimivo's own 10-band equalizer inside the Media3 audio pipeline; crossfade and bit-perfect output are not available.",
        )
    }
}

private fun AudioEnhancementSettings.sanitized() = copy(
    bassStrength = bassStrength.coerceIn(0f, 1f),
    trebleGainDb = trebleGainDb.coerceIn(-GraphicEqualizer.MAX_GAIN_DB, GraphicEqualizer.MAX_GAIN_DB),
    preampDb = preampDb.coerceIn(-12f, 0f),
    bands = bands.map { it.copy(gainDb = it.gainDb.coerceIn(-GraphicEqualizer.MAX_GAIN_DB, GraphicEqualizer.MAX_GAIN_DB)) },
)
