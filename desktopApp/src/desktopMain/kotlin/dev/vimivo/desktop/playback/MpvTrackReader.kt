package dev.vimivo.desktop.playback

import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackOrigin
import dev.vimivo.playback.TrackType

/** Reads libmpv's `track-list` through [stringProperty] (null when a property is unset). */
internal class MpvTrackReader(private val stringProperty: (String) -> String?) {
    fun read(type: TrackType): List<TrackInfo> {
        val count = stringProperty("track-list/count")?.toIntOrNull() ?: return emptyList()
        val selected = stringProperty(type.selectionProperty)
        return (0 until count).mapNotNull { index ->
            val prefix = "track-list/$index"
            val actualType = stringProperty("$prefix/type") ?: return@mapNotNull null
            if (actualType != type.mpvName) return@mapNotNull null
            val id = stringProperty("$prefix/id") ?: return@mapNotNull null
            val language = stringProperty("$prefix/lang")
            TrackInfo(
                id = id,
                type = type,
                label = stringProperty("$prefix/title") ?: language ?: "${type.displayName} $id",
                language = language,
                codec = stringProperty("$prefix/codec"),
                bitrate = stringProperty("$prefix/demux-bitrate")?.toIntOrNull(),
                channels = stringProperty("$prefix/demux-channel-count")?.toIntOrNull(),
                sampleRate = stringProperty("$prefix/demux-samplerate")?.toIntOrNull(),
                width = stringProperty("$prefix/demux-w")?.toIntOrNull(),
                height = stringProperty("$prefix/demux-h")?.toIntOrNull(),
                frameRate = stringProperty("$prefix/demux-fps")?.toFloatOrNull(),
                isSelected = id == selected,
                origin = if (stringProperty("$prefix/external") == "yes") {
                    TrackOrigin.EXTERNAL
                } else {
                    TrackOrigin.EMBEDDED
                },
            )
        }
    }
}

private val TrackType.mpvName: String get() = when (this) {
    TrackType.AUDIO -> "audio"
    TrackType.VIDEO -> "video"
    TrackType.SUBTITLE -> "sub"
}

private val TrackType.selectionProperty: String get() = when (this) {
    TrackType.AUDIO -> "aid"
    TrackType.VIDEO -> "vid"
    TrackType.SUBTITLE -> "sid"
}

private val TrackType.displayName: String get() = name.lowercase().replaceFirstChar(Char::uppercase)
