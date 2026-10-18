package dev.vimivo.playback

import kotlin.math.roundToInt

/** Human-readable summaries for the media information / codec dialog. */
object MediaInfoFormatter {
    private const val KILO = 1_000
    private const val BYTES_PER_KIB = 1_024.0
    private const val FPS_PRECISION = 1_000f

    fun describe(track: TrackInfo): String = buildList {
        track.codec?.takeIf(String::isNotBlank)?.let { add(it.uppercase()) }
        if (track.width != null && track.height != null) add("${track.width}×${track.height}")
        track.frameRate?.takeIf { it > 0f }?.let { add("${formatFrameRate(it)} fps") }
        track.channels?.takeIf { it > 0 }?.let { add(channelLabel(it)) }
        track.sampleRate?.takeIf { it > 0 }?.let { add("${formatKilo(it)} kHz") }
        track.bitrate?.takeIf { it > 0 }?.let { add("${it / KILO} kbps") }
        track.language?.takeIf(String::isNotBlank)?.let(::add)
        if (track.origin == TrackOrigin.EXTERNAL) add("external")
    }.joinToString(" · ")

    fun formatFileSize(bytes: Long): String {
        if (bytes < KILO) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / BYTES_PER_KIB
        var unit = 0
        while (value >= BYTES_PER_KIB && unit < units.lastIndex) {
            value /= BYTES_PER_KIB
            unit++
        }
        return "${(value * 10).roundToInt() / 10.0} ${units[unit]}"
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0) / KILO
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.pad()}:${seconds.pad()}"
        } else {
            "$minutes:${seconds.pad()}"
        }
    }

    private fun channelLabel(channels: Int): String = when (channels) {
        1 -> "mono"
        2 -> "stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$channels ch"
    }

    private fun formatFrameRate(fps: Float): String {
        val rounded = (fps * FPS_PRECISION).roundToInt() / FPS_PRECISION
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }

    private fun formatKilo(hz: Int): String {
        val khz = hz / KILO.toFloat()
        return if (khz == khz.toInt().toFloat()) khz.toInt().toString() else ((khz * 10).roundToInt() / 10f).toString()
    }

    private fun Long.pad(): String = toString().padStart(2, '0')
}
