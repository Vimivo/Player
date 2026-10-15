package dev.vimivo.desktop.integration.mpris

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueRepeatMode

/**
 * MPRIS 2 specification data conversion models.
 *
 * MPRIS 2 requires times in microseconds (1 ms = 1,000 µs), object path track IDs,
 * and standardized string representations for status and loop mode.
 */
internal object MprisModel {
    const val BUS_NAME = "org.mpris.MediaPlayer2.vimivo"
    const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
    const val INTERFACE_ROOT = "org.mpris.MediaPlayer2"
    const val INTERFACE_PLAYER = "org.mpris.MediaPlayer2.Player"

    fun toPlaybackStatusString(status: PlaybackStatus): String = when (status) {
        PlaybackStatus.Playing -> "Playing"
        PlaybackStatus.Paused -> "Paused"
        PlaybackStatus.Idle,
        PlaybackStatus.Preparing,
        PlaybackStatus.Buffering,
        PlaybackStatus.Ready,
        PlaybackStatus.Completed,
        is PlaybackStatus.Error,
        -> "Stopped"
    }

    fun toLoopStatusString(repeatMode: QueueRepeatMode): String = when (repeatMode) {
        QueueRepeatMode.OFF -> "None"
        QueueRepeatMode.ALL -> "Playlist"
        QueueRepeatMode.ONE -> "Track"
    }

    fun toTrackId(media: MediaItem?): String {
        if (media == null) return "/org/mpris/MediaPlayer2/track/none"
        val hash = media.id.value.hashCode().toUInt()
        return "/org/mpris/MediaPlayer2/track/$hash"
    }

    fun millisToMicroseconds(millis: Long): Long = millis.coerceAtLeast(0) * 1_000L

    fun toMetadataMap(media: MediaItem?, durationMillis: Long?): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "mpris:trackid" to toTrackId(media),
        )
        if (media != null) {
            map["xesam:title"] = media.title
            media.artist?.let { map["xesam:artist"] = listOf(it) }
            media.album?.let { map["xesam:album"] = it }
            media.artworkUri?.let { map["mpris:artUrl"] = it }
        }
        if (durationMillis != null && durationMillis > 0) {
            map["mpris:length"] = millisToMicroseconds(durationMillis)
        }
        return map
    }
}
