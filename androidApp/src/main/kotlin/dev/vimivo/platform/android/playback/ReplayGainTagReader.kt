@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "UnstableApiUsage", "Wrapping")

package dev.vimivo.platform.android.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dev.vimivo.audio.ReplayGain

/**
 * Finds ReplayGain tags in what the extractors report for a file: ID3 `TXXX` frames (MP3), Vorbis comments (FLAC, Ogg,
 * Opus) and iTunes `----` atoms (M4A). Tag names are matched without regard to case.
 */
internal object ReplayGainTagReader {
    private const val TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    private const val ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    private const val TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    private const val ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"

    fun read(metadata: Metadata?): ReplayGainTags {
        val values = mutableMapOf<String, String>()
        for (index in 0 until (metadata?.length() ?: 0)) {
            when (val entry = metadata?.get(index)) {
                is TextInformationFrame -> if (entry.id == "TXXX") put(values, entry.description, entry.values.firstOrNull())
                is VorbisComment -> put(values, entry.key, entry.value)
                is InternalFrame -> put(values, entry.description, entry.text)
                else -> Unit
            }
        }
        return ReplayGainTags(
            trackGainDb = ReplayGain.parseGainDb(values[TRACK_GAIN]),
            albumGainDb = ReplayGain.parseGainDb(values[ALBUM_GAIN]),
            trackPeak = ReplayGain.parsePeak(values[TRACK_PEAK]),
            albumPeak = ReplayGain.parsePeak(values[ALBUM_PEAK]),
        )
    }

    private fun put(values: MutableMap<String, String>, key: String?, value: String?) {
        if (key != null && value != null && key.uppercase().startsWith("REPLAYGAIN_")) values[key.uppercase()] = value
    }
}
