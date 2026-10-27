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

/** The audio, video and subtitle tracks libmpv listed at one moment. */
internal class MpvTrackLists(val audio: List<TrackInfo>, val video: List<TrackInfo>, val subtitle: List<TrackInfo>) {
    val size: Int get() = audio.size + video.size + subtitle.size

    /**
     * Whether this read can be trusted: it holds every track libmpv counts, and a file that has audio or video has one
     * of each selected. libmpv resolves the automatic selection a moment after the list appears, so a read in between
     * lists the tracks as all unselected.
     */
    fun isComplete(listedCount: Int): Boolean {
        val audioReady = audio.isEmpty() || audio.any { it.isSelected }
        val videoReady = video.isEmpty() || video.any { it.isSelected }
        return size == listedCount && audioReady && videoReady
    }
}

/**
 * Decides when the poll loop reads libmpv's track list: when the count changes, on a periodic refresh, or when asked.
 * libmpv fills the list and resolves the automatic track selection while a file opens, so a read can miss an entry or
 * see nothing selected even though the count it reported is right; that incomplete read is repeated on the next pass
 * (a few times at most) instead of being kept until the periodic refresh, which could leave a playing video without
 * its video track, or an audio file without a selected audio track, for seconds.
 */
internal class MpvTrackListWatcher {
    private var lastCount = -1
    private var incompleteReads = 0

    fun shouldRead(currentCount: Int, iteration: Long, requested: Boolean): Boolean =
        currentCount != lastCount || iteration % PERIODIC_REFRESH_PASSES == 1L || requested

    /** Records a read of the [currentCount] tracks libmpv listed; [complete] says whether the read can be trusted. */
    fun recordRead(currentCount: Int, complete: Boolean) {
        lastCount = currentCount
        if (complete) {
            incompleteReads = 0
        } else if (incompleteReads++ < MAX_INCOMPLETE_READS) {
            lastCount = -1
        }
    }

    /** Reads the lists through [reader] when due, and null when the cached ones are still right. */
    fun refresh(reader: MpvTrackReader, currentCount: Int, iteration: Long, requested: Boolean): MpvTrackLists? {
        if (!shouldRead(currentCount, iteration, requested)) return null
        val audio = reader.read(TrackType.AUDIO)
        val video = reader.read(TrackType.VIDEO)
        val lists = MpvTrackLists(audio, video, reader.read(TrackType.SUBTITLE))
        recordRead(currentCount, lists.isComplete(currentCount))
        return lists
    }

    private companion object {
        const val PERIODIC_REFRESH_PASSES = 20L
        const val MAX_INCOMPLETE_READS = 5
    }
}
