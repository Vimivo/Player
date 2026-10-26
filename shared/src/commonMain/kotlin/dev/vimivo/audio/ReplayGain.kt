package dev.vimivo.audio

import dev.vimivo.playback.ReplayGainMode

/** ReplayGain tags (`REPLAYGAIN_TRACK_GAIN` and friends) turned into a linear gain. */
object ReplayGain {
    /** A tag such as `-6.50 dB`, `+3.2dB` or `-6.5`, in dB; null when it is not a number. */
    fun parseGainDb(text: String?): Float? =
        text?.trim()?.removeSuffix("dB")?.removeSuffix("DB")?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() }

    /** A peak tag such as `0.988553`; null when it is not a positive number. */
    fun parsePeak(text: String?): Float? = text?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

    /**
     * The linear gain to apply for [mode]: the album gain for [ReplayGainMode.ALBUM] (the track's when the file has
     * no album tags), the track gain for [ReplayGainMode.TRACK] (and the album's as a fallback), 1 when off or
     * untagged. The gain is lowered where it would push the tagged peak past full scale, so it never clips.
     */
    fun linearGain(
        mode: ReplayGainMode,
        trackGainDb: Float?,
        albumGainDb: Float?,
        trackPeak: Float? = null,
        albumPeak: Float? = null,
    ): Float {
        if (mode == ReplayGainMode.OFF) return 1f
        val useAlbum = mode == ReplayGainMode.ALBUM && albumGainDb != null
        val gainDb = (if (useAlbum) albumGainDb else trackGainDb ?: albumGainDb) ?: return 1f
        val peak = if (useAlbum) albumPeak else trackPeak ?: albumPeak
        val linear = AudioDspChain.linear(gainDb)
        return if (peak != null && peak * linear > 1f) 1f / peak else linear
    }
}
