package dev.vimivo.desktop.integration.mpris

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.QueueRepeatMode

/** A D-Bus value with its wire type, so the transport can marshal it without guessing. */
internal sealed interface MprisValue {
    val signature: String
    val raw: Any

    data class Str(override val raw: String) : MprisValue { override val signature = "s" }
    data class ObjectPath(override val raw: String) : MprisValue { override val signature = "o" }
    data class Int64(override val raw: Long) : MprisValue { override val signature = "x" }
    data class Dbl(override val raw: Double) : MprisValue { override val signature = "d" }
    data class Bool(override val raw: Boolean) : MprisValue { override val signature = "b" }
    data class StrList(override val raw: List<String>) : MprisValue { override val signature = "as" }
    data class Dict(override val raw: Map<String, MprisValue>) : MprisValue { override val signature = "a{sv}" }
}

/** Requests arriving from MPRIS clients (desktop shells, `playerctl`, media keys). */
internal sealed interface MprisCommand {
    data object PlayPause : MprisCommand
    data object Play : MprisCommand
    data object Pause : MprisCommand
    data object Stop : MprisCommand
    data object Next : MprisCommand
    data object Previous : MprisCommand
    data class Seek(val offsetMicros: Long) : MprisCommand
    data class SetPosition(val trackId: String, val positionMicros: Long) : MprisCommand
    data class SetVolume(val volume: Double) : MprisCommand
    data class SetRate(val rate: Double) : MprisCommand
    data class SetShuffle(val enabled: Boolean) : MprisCommand
    data class SetLoopStatus(val status: String) : MprisCommand
}

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
    const val INTERFACE_PROPERTIES = "org.freedesktop.DBus.Properties"
    const val INTERFACE_INTROSPECTABLE = "org.freedesktop.DBus.Introspectable"
    const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
    private const val MICROS_PER_MILLI = 1_000L

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

    fun millisToMicroseconds(millis: Long): Long = millis.coerceAtLeast(0) * MICROS_PER_MILLI

    fun microsToMillis(micros: Long): Long = micros / MICROS_PER_MILLI

    fun metadata(media: MediaItem?, durationMillis: Long?): Map<String, MprisValue> {
        if (media == null) return mapOf("mpris:trackid" to MprisValue.ObjectPath(NO_TRACK))
        val map = linkedMapOf<String, MprisValue>(
            "mpris:trackid" to MprisValue.ObjectPath(toTrackId(media)),
            "xesam:title" to MprisValue.Str(media.title),
            "xesam:url" to MprisValue.Str(media.uri),
        )
        media.artist?.let { map["xesam:artist"] = MprisValue.StrList(listOf(it)) }
        media.album?.let { map["xesam:album"] = MprisValue.Str(it) }
        media.artworkUri?.let { map["mpris:artUrl"] = MprisValue.Str(it) }
        if (durationMillis != null && durationMillis > 0) {
            map["mpris:length"] = MprisValue.Int64(millisToMicroseconds(durationMillis))
        }
        return map
    }

    /** Untyped view of [metadata], kept for callers that only need the values. */
    fun toMetadataMap(media: MediaItem?, durationMillis: Long?): Map<String, Any> =
        metadata(media, durationMillis).mapValues { (_, value) -> value.raw }

    fun rootProperties(): Map<String, MprisValue> = linkedMapOf(
        "CanQuit" to MprisValue.Bool(false),
        "CanRaise" to MprisValue.Bool(false),
        "HasTrackList" to MprisValue.Bool(false),
        "Identity" to MprisValue.Str("Vimivo"),
        "DesktopEntry" to MprisValue.Str("dev.vimivo.desktop"),
        "SupportedUriSchemes" to MprisValue.StrList(listOf("file")),
        "SupportedMimeTypes" to MprisValue.StrList(emptyList()),
    )

    fun playerProperties(state: PlaybackState, queue: PlaybackQueueState, maxVolume: Float): Map<String, MprisValue> {
        val hasMedia = state.media != null
        val hasNext = queue.currentIndex in 0 until queue.items.size - 1 || queue.repeatMode == QueueRepeatMode.ALL
        return linkedMapOf(
            "PlaybackStatus" to MprisValue.Str(toPlaybackStatusString(state.status)),
            "LoopStatus" to MprisValue.Str(toLoopStatusString(queue.repeatMode)),
            "Rate" to MprisValue.Dbl(state.speed.toDouble()),
            "Shuffle" to MprisValue.Bool(queue.shuffleEnabled),
            "Metadata" to MprisValue.Dict(metadata(state.media?.withTags(state.metadata), state.durationMillis)),
            // MPRIS volume is linear with 1.0 as "normal"; values above 1.0 are amplification.
            "Volume" to MprisValue.Dbl(state.volume.coerceIn(0f, maxVolume).toDouble()),
            "Position" to MprisValue.Int64(millisToMicroseconds(state.positionMillis)),
            "MinimumRate" to MprisValue.Dbl(PlaybackSpeeds.MIN.toDouble()),
            "MaximumRate" to MprisValue.Dbl(PlaybackSpeeds.MAX.toDouble()),
            "CanGoNext" to MprisValue.Bool(hasNext),
            "CanGoPrevious" to MprisValue.Bool(hasMedia),
            "CanPlay" to MprisValue.Bool(hasMedia || queue.items.isNotEmpty()),
            "CanPause" to MprisValue.Bool(hasMedia),
            "CanSeek" to MprisValue.Bool(hasMedia && state.isSeekable),
            "CanControl" to MprisValue.Bool(true),
        )
    }

    /**
     * Properties whose values changed, for `PropertiesChanged`. Position is excluded as the
     * specification requires; clients extrapolate it from Rate and the `Seeked` signal.
     */
    fun changedProperties(
        previous: Map<String, MprisValue>?,
        current: Map<String, MprisValue>,
    ): Map<String, MprisValue> =
        current.filter { (name, value) -> name != "Position" && previous?.get(name) != value }

    val INTROSPECTION_XML = """
        <!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
         "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
        <node>
          <interface name="org.freedesktop.DBus.Introspectable">
            <method name="Introspect"><arg name="xml" type="s" direction="out"/></method>
          </interface>
          <interface name="org.freedesktop.DBus.Properties">
            <method name="Get"><arg type="s" direction="in"/><arg type="s" direction="in"/><arg type="v" direction="out"/></method>
            <method name="GetAll"><arg type="s" direction="in"/><arg type="a{sv}" direction="out"/></method>
            <method name="Set"><arg type="s" direction="in"/><arg type="s" direction="in"/><arg type="v" direction="in"/></method>
            <signal name="PropertiesChanged"><arg type="s"/><arg type="a{sv}"/><arg type="as"/></signal>
          </interface>
          <interface name="org.mpris.MediaPlayer2">
            <method name="Raise"/><method name="Quit"/>
            <property name="CanQuit" type="b" access="read"/><property name="CanRaise" type="b" access="read"/>
            <property name="HasTrackList" type="b" access="read"/><property name="Identity" type="s" access="read"/>
            <property name="DesktopEntry" type="s" access="read"/>
            <property name="SupportedUriSchemes" type="as" access="read"/>
            <property name="SupportedMimeTypes" type="as" access="read"/>
          </interface>
          <interface name="org.mpris.MediaPlayer2.Player">
            <method name="Next"/><method name="Previous"/><method name="Pause"/><method name="PlayPause"/>
            <method name="Stop"/><method name="Play"/>
            <method name="Seek"><arg name="Offset" type="x" direction="in"/></method>
            <method name="SetPosition"><arg name="TrackId" type="o" direction="in"/><arg name="Position" type="x" direction="in"/></method>
            <signal name="Seeked"><arg name="Position" type="x"/></signal>
            <property name="PlaybackStatus" type="s" access="read"/><property name="LoopStatus" type="s" access="readwrite"/>
            <property name="Rate" type="d" access="readwrite"/><property name="Shuffle" type="b" access="readwrite"/>
            <property name="Metadata" type="a{sv}" access="read"/><property name="Volume" type="d" access="readwrite"/>
            <property name="Position" type="x" access="read"/><property name="MinimumRate" type="d" access="read"/>
            <property name="MaximumRate" type="d" access="read"/><property name="CanGoNext" type="b" access="read"/>
            <property name="CanGoPrevious" type="b" access="read"/><property name="CanPlay" type="b" access="read"/>
            <property name="CanPause" type="b" access="read"/><property name="CanSeek" type="b" access="read"/>
            <property name="CanControl" type="b" access="read"/>
          </interface>
        </node>
    """.trimIndent()
}

/** The media item as clients should see it: embedded tags from the engine over file-name defaults. */
internal fun MediaItem.withTags(tags: dev.vimivo.playback.PlaybackMetadata) = copy(
    title = tags.title ?: title,
    artist = tags.artist ?: artist,
    album = tags.album ?: album,
)
