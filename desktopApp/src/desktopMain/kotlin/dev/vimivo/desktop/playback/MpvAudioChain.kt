package dev.vimivo.desktop.playback

import dev.vimivo.playback.AudioEnhancementSettings

/**
 * The audio filter chain (`af`) libmpv runs: the equalizer and normalizer settings, then the gain and limiter for a
 * volume above 100 % (see [DesktopAudioEffects.boostStage]). [setFilter] writes the chain to libmpv.
 */
internal class MpvAudioChain(private val setFilter: (String) -> Unit) {
    /** The volume asked for, 0 to 2 (200 %); libmpv's own volume only covers up to 100 %. */
    var requestedVolume = 1f
        private set
    private var settings = AudioEnhancementSettings()

    fun setVolume(volume: Float) {
        requestedVolume = volume
        refresh()
    }

    fun setSettings(effective: AudioEnhancementSettings) {
        settings = effective
        refresh()
    }

    private fun refresh() {
        setFilter(DesktopAudioEffects.filter(settings, requestedVolume.coerceAtLeast(1f)).orEmpty())
    }
}
